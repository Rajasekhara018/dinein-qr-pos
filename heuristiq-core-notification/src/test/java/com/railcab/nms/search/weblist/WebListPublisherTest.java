package com.railcab.nms.search.weblist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** The NMS web lists reach search as whole organization partitions, in batches of at most 100, and are complete only when all went through. */
class WebListPublisherTest {

    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final WebListProperties ON = new WebListProperties(true, "http://search", "token", 600_000, 60_000, 2_000);
    private static final String TEMPLATE = WebListChanges.TEMPLATE;

    private final WebListSource source = Mockito.mock(WebListSource.class);
    private final WebListTransport transport = Mockito.mock(WebListTransport.class);
    private final WebListPublisher publisher = new WebListPublisher(source, transport, ON);

    static JsonNode spec(String model) throws Exception {
        try (InputStream in = WebListPublisherTest.class.getResourceAsStream(WebListSource.MODELS_RESOURCE)) {
            for (JsonNode spec : JSON.readTree(in)) {
                if (model.equals(spec.path("model").asText())) {
                    return spec;
                }
            }
        }
        throw new IllegalArgumentException(model);
    }

    private void rows(String model, WebListSource.Partition partition, int count) {
        Mockito.doAnswer(call -> {
            Consumer<Map<String, Object>> sink = call.getArgument(2);
            for (int i = 0; i < count; i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", "t" + i);
                row.put("name", "Ride Booked " + i);
                row.put("createdAt", "2026-11-01T10:00:00");
                sink.accept(row);
            }
            return null;
        }).when(source).scan(eq(model), eq(partition), any());
    }

    @Test
    void eachOrganizationIsReplacedInBatchesOfAtMostAHundredAndTheListIsThenComplete() throws Exception {
        WebListSource.Partition fallback = WebListSource.Partition.organization("default");
        WebListSource.Partition acme = WebListSource.Partition.organization("acme");
        Mockito.when(source.models()).thenReturn(List.of(TEMPLATE));
        Mockito.when(source.partitions(TEMPLATE)).thenReturn(List.of(fallback, acme));
        Mockito.when(source.spec(TEMPLATE)).thenReturn(spec(TEMPLATE));
        Mockito.when(source.json()).thenReturn(JSON);
        rows(TEMPLATE, fallback, 250);
        rows(TEMPLATE, acme, 3);

        WebListPublisher.Report report = publisher.publishAll();

        assertThat(report.records()).isEqualTo(253);
        assertThat(report.failures()).isEmpty();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> batches = ArgumentCaptor.forClass(List.class);
        InOrder order = Mockito.inOrder(transport);
        order.verify(transport).send(eq("BEGIN"), eq(TEMPLATE), eq(fallback), anyString(), eq(List.of()));
        order.verify(transport, Mockito.times(3)).send(eq("BATCH"), eq(TEMPLATE), eq(fallback), anyString(), batches.capture());
        order.verify(transport).send(eq("COMPLETE"), eq(TEMPLATE), eq(fallback), anyString(), eq(List.of()));
        order.verify(transport).send(eq("BEGIN"), eq(TEMPLATE), eq(acme), anyString(), eq(List.of()));
        order.verify(transport).send(eq("BATCH"), eq(TEMPLATE), eq(acme), anyString(), anyList());
        order.verify(transport).send(eq("COMPLETE"), eq(TEMPLATE), eq(acme), anyString(), eq(List.of()));
        order.verify(transport).send(eq("MODEL_COMPLETE"), eq(TEMPLATE), eq(fallback), anyString(), eq(List.of()));
        assertThat(batches.getAllValues()).extracting(List::size).containsExactly(100, 100, 50);
    }

    @Test
    void aRowCarriesItsPayloadOrganizationOrderSearchTextAndInstantCreatedAt() throws Exception {
        WebListSource.Partition acme = WebListSource.Partition.organization("acme");
        Mockito.when(source.json()).thenReturn(JSON);
        Map<String, Object> row = Map.of("id", "t1", "name", "Ride Booked Email", "createdAt", "2026-11-01T10:00:00");

        Map<String, Object> record = publisher.record(TEMPLATE, spec(TEMPLATE), acme, row, 4);

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) record.get("fields");
        String created = LocalDateTime.parse("2026-11-01T10:00:00").atZone(ZoneId.systemDefault()).toInstant().toString();
        assertThat(record.get("modelKey")).isEqualTo(WebListPublisher.key("acme:acme:t1"));
        assertThat(fields).containsEntry("ownerKey", "acme").containsEntry("tenantId", "acme").containsEntry("payload", row)
                .containsEntry("number5", 4L).containsEntry("createdAt", created);
        assertThat((String) fields.get("text13")).contains("ride booked email");
    }

    @Test
    void slotsAreFilledFromTheRowAndNormalized() throws Exception {
        WebListSource.Partition acme = WebListSource.Partition.organization("acme");
        Mockito.when(source.json()).thenReturn(JSON);

        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>) publisher.record(WebListChanges.EVENT_TEMPLATE,
                spec(WebListChanges.EVENT_TEMPLATE), acme, Map.of("id", "t1", "event", "ride_booked"), 0).get("fields");
        @SuppressWarnings("unchecked")
        Map<String, Object> broadcast = (Map<String, Object>) publisher.record(WebListChanges.BROADCAST,
                spec(WebListChanges.BROADCAST), acme, Map.of("id", "b1", "status", "PENDING"), 0).get("fields");
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) publisher.record(WebListChanges.EVENT,
                spec(WebListChanges.EVENT), acme, Map.of("value", "RIDE_BOOKED", "label", "Ride booked"), 0).get("fields");

        assertThat(template).containsEntry("text12", "RIDE_BOOKED").containsEntry("createdAt", "1970-01-01T00:00:00Z");
        assertThat(broadcast).containsEntry("text1", "pending");
        assertThat(event).containsEntry("ownerKey", "acme");
    }

    @Test
    void aRowWithoutAnIdIsRefused() throws Exception {
        Mockito.when(source.json()).thenReturn(JSON);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> publisher.record(TEMPLATE, spec(TEMPLATE),
                WebListSource.Partition.organization("acme"), Map.of("name", "x"), 0))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no id");
    }

    @Test
    void aFailingPartitionIsReportedWithItsReasonSkippedAndTheListIsNotMarkedComplete() throws Exception {
        WebListSource.Partition fallback = WebListSource.Partition.organization("default");
        WebListSource.Partition acme = WebListSource.Partition.organization("acme");
        Mockito.when(source.models()).thenReturn(List.of(TEMPLATE));
        Mockito.when(source.partitions(TEMPLATE)).thenReturn(List.of(fallback, acme));
        Mockito.when(source.spec(TEMPLATE)).thenReturn(spec(TEMPLATE));
        Mockito.when(source.json()).thenReturn(JSON);
        Mockito.doThrow(new IllegalStateException("Search refused BEGIN for WEB_NMS_TEMPLATE with HTTP 401"))
                .when(transport).send(eq("BEGIN"), anyString(), eq(fallback), anyString(), anyList());
        rows(TEMPLATE, acme, 2);

        WebListPublisher.Report report = publisher.publishAll();

        assertThat(report.failures()).containsExactly(TEMPLATE + "/default: Search refused BEGIN for WEB_NMS_TEMPLATE with HTTP 401");
        assertThat(report.records()).isEqualTo(2);
        Mockito.verify(transport).send(eq("COMPLETE"), eq(TEMPLATE), eq(acme), anyString(), anyList());
        Mockito.verify(transport, Mockito.never()).send(eq("MODEL_COMPLETE"), anyString(), any(), anyString(), anyList());
    }

    @Test
    void switchedOffNothingIsSent() {
        WebListProperties off = new WebListProperties(false, "http://search", "token", 600_000, 60_000, 2_000);

        assertThat(new WebListPublisher(source, transport, off).publishAll().records()).isZero();
        Mockito.verifyNoInteractions(transport);
    }

    @Test
    void withoutTheServiceTokenTheTransportSaysWhy() {
        WebListProperties noToken = new WebListProperties(true, "http://search", "", 600_000, 60_000, 2_000);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new WebListTransport(noToken, JSON)
                        .send("BEGIN", TEMPLATE, WebListSource.Partition.organization("default"), "g", List.of()))
                .hasMessageContaining("RIDER_INTERNAL_SERVICE_TOKEN");
    }

    /** The contracts are copied from Rider_SearchAPI's catalog; in the monorepo they must still be the same. */
    @Test
    void theContractsMatchTheSearchCatalog() throws Exception {
        List<JsonNode> ours = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream(WebListSource.MODELS_RESOURCE)) {
            JSON.readTree(in).forEach(ours::add);
        }
        assertThat(ours).extracting(spec -> spec.path("model").asText())
                .containsExactly(TEMPLATE, WebListChanges.EVENT_TEMPLATE, WebListChanges.EVENT, WebListChanges.BROADCAST);
        Path catalog = Path.of("..", "Rider_SearchAPI", "src", "main", "resources", "web-search-models.json");
        if (!Files.exists(catalog)) {
            return;
        }
        List<JsonNode> expected = new ArrayList<>();
        JSON.readTree(catalog.toFile()).forEach(spec -> {
            if ("nms".equals(spec.path("service").asText())) {
                expected.add(spec);
            }
        });
        assertThat(ours).containsExactlyElementsOf(expected);
    }
}
