package com.railcab.dms.search.projection;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebProjectionPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC);
    private static final String MODEL = ProjectionConstants.MODEL_DOCUMENT_TYPE;

    record Call(ProjectionPhase phase, String model, String tenantId, String generation, List<ProjectionRecord> records) {
    }

    /** Records every step; {@code failure} may throw for a step. */
    static final class RecordingGateway implements ProjectionGateway {
        final List<Call> calls = new ArrayList<>();
        BiFunction<ProjectionPhase, Integer, RuntimeException> failure = (phase, n) -> null;

        @Override
        public void send(ProjectionPhase phase, String model, String tenantId, String generation, List<ProjectionRecord> records) {
            calls.add(new Call(phase, model, tenantId, generation, records));
            RuntimeException error = failure.apply(phase, calls.size());
            if (error != null) {
                throw error;
            }
        }

        List<ProjectionPhase> phases() {
            return calls.stream().map(Call::phase).toList();
        }
    }

    /** A source whose rows depend on the organization asked for, like the real ones. */
    static final class FakeSource implements ProjectionSource {
        final Map<String, Integer> rowsPerTenant;
        final List<String> tenantsAsked = new ArrayList<>();

        FakeSource(Map<String, Integer> rowsPerTenant) {
            this.rowsPerTenant = rowsPerTenant;
        }

        @Override
        public String model() {
            return MODEL;
        }

        @Override
        public Page page(String tenantId, int pageIndex, int size) {
            tenantsAsked.add(tenantId);
            int total = rowsPerTenant.getOrDefault(tenantId, 0);
            int from = pageIndex * size;
            List<ProjectionRecord> rows = new ArrayList<>();
            for (int i = from; i < Math.min(total, from + size); i++) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put(ProjectionConstants.FIELD_PAYLOAD, Map.of("id", tenantId + "-" + i));
                rows.add(new ProjectionRecord(tenantId + "-" + i, fields));
            }
            return new Page(rows, from + size >= total);
        }
    }

    private static WebProjectionPublisher publisher(FakeSource source, RecordingGateway gateway) {
        return new WebProjectionPublisher(List.of(source), gateway, CLOCK);
    }

    @Test
    void publishesBeginBatchesCompleteAndTenantCompleteInOrderWithOneGeneration() {
        FakeSource source = new FakeSource(Map.of("org-a", 250));
        RecordingGateway gateway = new RecordingGateway();

        WebProjectionPublisher.Outcome outcome = publisher(source, gateway).publish(MODEL, "org-a", (done, lost) -> { });

        assertEquals(WebProjectionPublisher.Status.PUBLISHED, outcome.status());
        assertEquals(250, outcome.indexed());
        assertEquals(0, outcome.failed());
        assertEquals(List.of(ProjectionPhase.BEGIN, ProjectionPhase.BATCH, ProjectionPhase.BATCH, ProjectionPhase.BATCH,
                ProjectionPhase.COMPLETE, ProjectionPhase.TENANT_COMPLETE), gateway.phases());
        assertEquals(Set.of(gateway.calls.get(0).generation()), Set.copyOf(gateway.calls.stream().map(Call::generation).toList()),
                "one fixed generation for the whole run");
        assertTrue(gateway.calls.get(0).generation().length() <= 64);
        assertEquals(List.of(100, 100, 50), gateway.calls.stream().filter(c -> c.phase() == ProjectionPhase.BATCH)
                .map(c -> c.records().size()).toList());
    }

    @Test
    void numbersTheRowsInSourceOrderAcrossBatches() {
        RecordingGateway gateway = new RecordingGateway();

        publisher(new FakeSource(Map.of("org-a", 130)), gateway).publish(MODEL, "org-a", (done, lost) -> { });

        List<Object> positions = gateway.calls.stream().filter(c -> c.phase() == ProjectionPhase.BATCH)
                .flatMap(c -> c.records().stream()).map(r -> r.fields().get(ProjectionConstants.SLOT_POSITION)).toList();
        assertEquals(130, positions.size());
        for (int i = 0; i < positions.size(); i++) {
            assertEquals((long) i, positions.get(i));
        }
    }

    @Test
    void anEmptySourceStillCompletesSoStaleRowsAreRemovedAndTheModelIsReady() {
        RecordingGateway gateway = new RecordingGateway();

        WebProjectionPublisher.Outcome outcome = publisher(new FakeSource(Map.of()), gateway).publish(MODEL, "org-a", (done, lost) -> { });

        assertEquals(WebProjectionPublisher.Status.PUBLISHED, outcome.status());
        assertEquals(List.of(ProjectionPhase.BEGIN, ProjectionPhase.COMPLETE, ProjectionPhase.TENANT_COMPLETE), gateway.phases());
    }

    @Test
    void reportsProgressAfterEachAcceptedBatch() {
        List<String> seen = new ArrayList<>();

        publisher(new FakeSource(Map.of("org-a", 150)), new RecordingGateway())
                .publish(MODEL, "org-a", (done, lost) -> seen.add(done + "/" + lost));

        assertEquals(List.of("100/0", "150/0"), seen);
    }

    @Test
    void aLeaseHeldByAnotherRunAtBeginIsReportedAsInProgressAndNothingElseIsSent() {
        RecordingGateway gateway = new RecordingGateway();
        gateway.failure = (phase, n) -> phase == ProjectionPhase.BEGIN
                ? new ProjectionGateway.LeaseHeldException(ProjectionConstants.MESSAGE_IN_PROGRESS) : null;

        WebProjectionPublisher.Outcome outcome = publisher(new FakeSource(Map.of("org-a", 5)), gateway).publish(MODEL, "org-a", (d, l) -> { });

        assertEquals(WebProjectionPublisher.Status.IN_PROGRESS, outcome.status());
        assertEquals(ProjectionConstants.MESSAGE_IN_PROGRESS, outcome.message());
        assertEquals(List.of(ProjectionPhase.BEGIN), gateway.phases());
    }

    @Test
    void aLostLeaseInTheMiddleIsAlsoInProgressAndNeverCompletes() {
        RecordingGateway gateway = new RecordingGateway();
        gateway.failure = (phase, n) -> n == 3 ? new ProjectionGateway.LeaseHeldException("lost") : null;

        WebProjectionPublisher.Outcome outcome = publisher(new FakeSource(Map.of("org-a", 250)), gateway).publish(MODEL, "org-a", (d, l) -> { });

        assertEquals(WebProjectionPublisher.Status.IN_PROGRESS, outcome.status());
        assertEquals(100, outcome.indexed());
        assertFalse(gateway.phases().contains(ProjectionPhase.COMPLETE));
        assertFalse(gateway.phases().contains(ProjectionPhase.TENANT_COMPLETE));
    }

    @Test
    void aFailedBatchCountsItsRowsAsFailedAndNeverCompletesSoNothingIsDeleted() {
        RecordingGateway gateway = new RecordingGateway();
        gateway.failure = (phase, n) -> n == 3
                ? new ProjectionGateway.UnavailableException("the search service is unavailable (TIMEOUT)", null) : null;

        WebProjectionPublisher.Outcome outcome = publisher(new FakeSource(Map.of("org-a", 250)), gateway).publish(MODEL, "org-a", (d, l) -> { });

        assertEquals(WebProjectionPublisher.Status.FAILED, outcome.status());
        assertEquals(100, outcome.indexed());
        assertEquals(100, outcome.failed());
        assertEquals("the search service is unavailable (TIMEOUT)", outcome.message());
        assertEquals(List.of(ProjectionPhase.BEGIN, ProjectionPhase.BATCH, ProjectionPhase.BATCH), gateway.phases());
    }

    @Test
    void aFailureAtBeginIsFailedWithNoRowsSent() {
        RecordingGateway gateway = new RecordingGateway();
        gateway.failure = (phase, n) -> new ProjectionGateway.UnavailableException(ProjectionConstants.MESSAGE_NOT_CONFIGURED, null);

        WebProjectionPublisher.Outcome outcome = publisher(new FakeSource(Map.of("org-a", 5)), gateway).publish(MODEL, "org-a", (d, l) -> { });

        assertEquals(WebProjectionPublisher.Status.FAILED, outcome.status());
        assertEquals(0, outcome.indexed());
        assertEquals(ProjectionConstants.MESSAGE_NOT_CONFIGURED, outcome.message());
    }

    @Test
    void aSourceReadFailureIsFailedAndNeverCompletes() {
        RecordingGateway gateway = new RecordingGateway();
        ProjectionSource broken = new ProjectionSource() {
            @Override
            public String model() {
                return MODEL;
            }

            @Override
            public Page page(String tenantId, int pageIndex, int size) {
                throw new IllegalStateException("database down");
            }
        };

        WebProjectionPublisher.Outcome outcome = new WebProjectionPublisher(List.of(broken), gateway, CLOCK)
                .publish(MODEL, "org-a", (d, l) -> { });

        assertEquals(WebProjectionPublisher.Status.FAILED, outcome.status());
        assertEquals("IllegalStateException", outcome.message());
        assertEquals(List.of(ProjectionPhase.BEGIN), gateway.phases());
    }

    @Test
    void organizationAsRowsNeverReachOrganizationBsProjection() {
        FakeSource source = new FakeSource(Map.of("org-a", 3, "org-b", 2));
        RecordingGateway gateway = new RecordingGateway();

        publisher(source, gateway).publish(MODEL, "org-b", (d, l) -> { });

        assertEquals(Set.of("org-b"), Set.copyOf(source.tenantsAsked), "the source is only ever asked for the caller's organization");
        assertEquals(Set.of("org-b"), Set.copyOf(gateway.calls.stream().map(Call::tenantId).toList()));
        List<String> keys = gateway.calls.stream().flatMap(c -> c.records().stream()).map(ProjectionRecord::modelKey).toList();
        assertEquals(List.of("org-b-0", "org-b-1"), keys);
        assertTrue(gateway.calls.stream().flatMap(c -> c.records().stream()).noneMatch(r -> r.modelKey().startsWith("org-a")));
    }

    @Test
    void anUnknownModelIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> publisher(new FakeSource(Map.of()), new RecordingGateway()).publish("WEB_NOPE", "org-a", (d, l) -> { }));
    }

    @Test
    void listsItsModelsInPublishOrder() {
        assertEquals(List.of(MODEL), publisher(new FakeSource(Map.of()), new RecordingGateway()).models());
    }
}
