package com.railcab.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railcab.audit.domain.AuditEvent;
import com.railcab.audit.domain.AuditEventFilter;
import com.railcab.audit.domain.AuditEventType;
import com.railcab.audit.domain.AuditOutcome;
import com.railcab.audit.domain.AuditPayload;
import com.railcab.audit.domain.AuditEventRepository;
import com.railcab.audit.application.InputBuilder;
import com.railcab.audit.domain.SchedulerLock;
import com.railcab.audit.application.AuditRetentionService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behaviour that must be identical on both persistence modes: the whole application is started on a real
 * database (embedded MongoDB or embedded PostgreSQL) and driven over HTTP and through the repository port.
 * Subclasses only choose the database.
 */
abstract class AbstractAuditModeTest {

    static final String SECRET = "test-secret-test-secret-test-secret-123456";
    static final String SERVICE_TOKEN = "test-service-token";

    @LocalServerPort
    int port;
    @Autowired
    AuditEventRepository repository;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    AuditRetentionService retentionJob;
    @Autowired
    SchedulerLock lock;

    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void clean() {
        repository.deleteExpired(Instant.parse("2100-01-01T00:00:00Z"));
    }

    // ---------------------------------------------------------------- helpers

    HttpResponse<String> call(String method, String path, Map<String, String> headers, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        headers.forEach(b::header);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static String token(String userId, String... roles) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().subject(userId).claim("roles", List.of(roles))
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    static String tokenInOrganization(String userId, String tenantId, String... roles) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().subject(userId).claim("roles", List.of(roles)).claim("tenantId", tenantId)
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
    }

    static Map<String, String> bearer(String token) {
        return Map.of("Authorization", "Bearer " + token);
    }

    static Map<String, String> service() {
        return Map.of("X-Rider-Service-Token", SERVICE_TOKEN);
    }

    JsonNode json(HttpResponse<String> r) throws Exception {
        return mapper.readTree(r.body());
    }

    String ingestBody(com.railcab.audit.application.AuditEventInput... events) throws Exception {
        return mapper.writeValueAsString(Map.of("events", List.of(events)));
    }

    AuditEvent event(String eventId, AuditEventType type, String userId, Instant at) {
        return AuditEvent.builder().eventId(eventId).eventType(type).occurredAt(at).receivedAt(at)
                .expireAt(at.plus(Duration.ofDays(30))).sourceService("test").userId(userId).userType("STAFF")
                .role("ADMIN").roles(List.of("ADMIN")).privilegeIds(List.of(11)).feature("rider-config")
                .module("CONFIGURATION").method("GET").path("/api/x").statusCode(200).outcome(AuditOutcome.SUCCESS)
                .durationMs(5L).correlationId("c-" + eventId).build();
    }

    // ---------------------------------------------------------------- internal ingest + auth

    @Test
    void internalEndpointAcceptsOnlyTheServiceCredential() throws Exception {
        String body = ingestBody(InputBuilder.access().eventId("evt-auth-0001").build());
        assertThat(call("POST", "/api/v1/audit/internal/events", Map.of(), body).statusCode()).isEqualTo(401);
        assertThat(call("POST", "/api/v1/audit/internal/events", Map.of("X-Rider-Service-Token", "wrong"), body).statusCode()).isEqualTo(401);
        assertThat(call("POST", "/api/v1/audit/internal/events", bearer(token("admin-1", "SUPER_ADMIN")), body).statusCode())
                .as("an end-user token must not reach the internal chain").isEqualTo(401);
        assertThat(call("POST", "/api/v1/audit/internal/events", service(), body).statusCode()).isEqualTo(200);
        assertThat(call("POST", "/api/v1/audit/internal/events", bearer(token("svc", "SERVICE")), body).statusCode()).isEqualTo(200);
    }

    @Test
    void ingestStoresMasksSanitizesAndIsIdempotent() throws Exception {
        String body = ingestBody(InputBuilder.access().eventId("evt-mask-0001").method("POST")
                .path("/api/x/y?token=abc#f").ipAddress("203.0.113.57")
                .details(Map.of("recipient", "john@example.com", "otp", "123456", "channel", "EMAIL")).build());
        JsonNode first = json(call("POST", "/api/v1/audit/internal/events", service(), body)).get("responseObject");
        assertThat(first.get("accepted").asInt()).isEqualTo(1);
        JsonNode again = json(call("POST", "/api/v1/audit/internal/events", service(), body)).get("responseObject");
        assertThat(again.get("accepted").asInt()).isZero();
        assertThat(again.get("duplicates").asInt()).isEqualTo(1);

        JsonNode list = json(call("GET", "/api/v1/audit/events?correlationId=corr-1", bearer(token("a", "ADMIN")), null))
                .get("responseObject");
        assertThat(list.get("totalElements").asInt()).isEqualTo(1);
        JsonNode e = list.get("content").get(0);
        assertThat(e.get("path").asText()).isEqualTo("/api/x/y");
        assertThat(e.get("ipAddress").asText()).isEqualTo("203.0.113.0");
        assertThat(e.get("details").has("otp")).isFalse();
        assertThat(e.get("details").get("recipient").asText()).isEqualTo("j***@example.com");
        assertThat(e.has("expireAt")).isFalse();
        assertThat(e.has("requestBody")).as("lists never carry payloads").isFalse();
    }

    @Test
    void batchOver500IsRefusedAndBadEventsAreRejectedIndividually() throws Exception {
        List<Object> big = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            big.add(Map.of("eventType", "ACCESS", "sourceService", "t", "statusCode", 200));
        }
        assertThat(call("POST", "/api/v1/audit/internal/events", service(), mapper.writeValueAsString(Map.of("events", big)))
                .statusCode()).isEqualTo(413);
        String mixed = ingestBody(InputBuilder.access().eventId("evt-mixed-001").build(),
                InputBuilder.access().eventType("NOPE").build());
        JsonNode r = json(call("POST", "/api/v1/audit/internal/events", service(), mixed)).get("responseObject");
        assertThat(r.get("accepted").asInt()).isEqualTo(1);
        assertThat(r.get("rejected").asInt()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- payloads

    @Test
    void payloadsAreStoredRedactedSeparatelyAndServedOnlyByThePayloadEndpoint() throws Exception {
        String body = ingestBody(InputBuilder.access().eventId("evt-pay-00001").method("POST").correlationId("corr-pay")
                .requestHeaders(Map.of("Authorization", "Bearer abcdefghijkl", "Accept", "*/*"))
                .responseHeaders(Map.of("Set-Cookie", "a=b", "X.Dotted", "v"))
                .requestBody("{\"name\":\"Ann\",\"password\":\"hunter2\",\"profile\":{\"cardNumber\":\"4111111111111111\"}}")
                .responseBody("{\"token\":\"abc\",\"id\":1}").bodyContentType("application/json").build());
        assertThat(call("POST", "/api/v1/audit/internal/events", service(), body).statusCode()).isEqualTo(200);
        String admin = token("a", "ADMIN");

        JsonNode event = json(call("GET", "/api/v1/audit/events?correlationId=corr-pay", bearer(admin), null))
                .get("responseObject").get("content").get(0);
        long id = event.get("id").asLong();
        String single = call("GET", "/api/v1/audit/events/" + id, bearer(admin), null).body();
        assertThat(single).doesNotContain("hunter2").doesNotContain("requestBody");

        JsonNode p = json(call("GET", "/api/v1/audit/events/" + id + "/payload", bearer(admin), null)).get("responseObject");
        assertThat(p.get("requestHeaders").get("Authorization").asText()).isEqualTo("***");
        assertThat(p.get("requestHeaders").get("Accept").asText()).isEqualTo("*/*");
        assertThat(p.get("responseHeaders").get("Set-Cookie").asText()).isEqualTo("***");
        assertThat(p.get("responseHeaders").get("X.Dotted").asText()).isEqualTo("v");
        assertThat(p.get("requestBody").asText()).contains("\"name\":\"Ann\"").contains("***1111")
                .doesNotContain("hunter2").doesNotContain("4111");
        assertThat(p.get("responseBody").asText()).contains("\"token\":\"***\"");

        String csv = call("GET", "/api/v1/audit/events/export/payloads?correlationId=corr-pay", bearer(admin), null).body();
        assertThat(csv).contains("requestBody").contains("***1111").doesNotContain("hunter2");
        assertThat(call("GET", "/api/v1/audit/events/export?correlationId=corr-pay", bearer(admin), null).body())
                .doesNotContain("requestBody");

        String noPayload = ingestBody(InputBuilder.access().eventId("evt-nopay-0001").correlationId("corr-nopay").build());
        call("POST", "/api/v1/audit/internal/events", service(), noPayload);
        long noPayloadId = json(call("GET", "/api/v1/audit/events?correlationId=corr-nopay", bearer(admin), null))
                .get("responseObject").get("content").get(0).get("id").asLong();
        assertThat(call("GET", "/api/v1/audit/events/" + noPayloadId + "/payload", bearer(admin), null).statusCode()).isEqualTo(404);
    }

    @Test
    void sensitivePathBodiesAreNeverStoredEvenIfSent() throws Exception {
        String body = ingestBody(InputBuilder.access().eventId("evt-login-0001").method("POST").correlationId("corr-login")
                .path("/api/uam/identity/auth/login").requestBody("{\"phone\":\"9876543210\",\"otp\":\"1\"}")
                .responseBody("{\"accessToken\":\"x\"}").bodyContentType("application/json")
                .requestHeaders(Map.of("Accept", "*/*")).build());
        call("POST", "/api/v1/audit/internal/events", service(), body);
        AuditPayload p = repository.findPayload("evt-login-0001").orElseThrow();
        assertThat(p.getRequestBody()).isNull();
        assertThat(p.getResponseBody()).isNull();
        assertThat(p.getRequestHeaders()).containsEntry("Accept", "*/*");
    }

    @Test
    void purgeRemovesExpiredEventsAndTheirPayloadsTogether() throws Exception {
        Instant now = Instant.now();
        AuditEvent old = event("evt-old-00001", AuditEventType.ACCESS, "u", now.minus(Duration.ofDays(40)));
        old.setExpireAt(now.minus(Duration.ofDays(10)));
        AuditEvent fresh = event("evt-fresh-0001", AuditEventType.ACCESS, "u", now);
        repository.insertNew(List.of(old, fresh));
        repository.insertPayloads(List.of(
                AuditPayload.builder().eventId("evt-old-00001").requestBody("{}").expireAt(old.getExpireAt()).build(),
                AuditPayload.builder().eventId("evt-fresh-0001").requestBody("{}").expireAt(fresh.getExpireAt()).build()));

        assertThat(retentionJob.purge()).isEqualTo(1);

        assertThat(repository.search(new AuditEventFilter(null, null, null, null, null, null, null, null, null, null, null, null, null), 0, 10)
                .content()).extracting(AuditEvent::getEventId).containsExactly("evt-fresh-0001");
        assertThat(repository.findPayload("evt-old-00001")).isEmpty();
        assertThat(repository.findPayload("evt-fresh-0001")).isPresent();
        assertThat(retentionJob.purge()).isZero();
    }

    // ---------------------------------------------------------------- screen views

    @Test
    void screenViewsUseTheTokenIdentityAndBadKeysAreRejected() throws Exception {
        String body = "{\"events\":[{\"screenKey\":\"payments.refunds\",\"appChannel\":\"CUSTOMER_APP\",\"userId\":\"attacker\","
                + "\"role\":\"SUPER_ADMIN\",\"eventId\":\"sv-event-0001\"},{\"screenKey\":\"Bad Key\"}]}";
        HttpResponse<String> r = call("POST", "/api/v1/audit/screen-views", bearer(token("cust-1", "CUSTOMER")), body);
        assertThat(r.statusCode()).isEqualTo(200);
        JsonNode result = json(r).get("responseObject");
        assertThat(result.get("accepted").asInt()).isEqualTo(1);
        assertThat(result.get("rejected").asInt()).isEqualTo(1);

        JsonNode e = json(call("GET", "/api/v1/audit/events?eventType=SCREEN_VIEW&screenKey=payments.refunds",
                bearer(token("a", "ADMIN")), null)).get("responseObject").get("content").get(0);
        assertThat(e.get("userId").asText()).isEqualTo("cust-1");
        assertThat(e.get("role").asText()).isEqualTo("CUSTOMER");
        assertThat(e.get("userType").asText()).isEqualTo("CUSTOMER");
        assertThat(json(call("GET", "/api/v1/audit/events?userId=attacker", bearer(token("a", "ADMIN")), null))
                .get("responseObject").get("totalElements").asInt()).isZero();
    }

    @Test
    void screenViewsNeedAUserTokenAndAreRateLimited() throws Exception {
        String one = "{\"events\":[{\"screenKey\":\"home.dashboard\"}]}";
        assertThat(call("POST", "/api/v1/audit/screen-views", Map.of(), one).statusCode()).isEqualTo(401);
        assertThat(call("POST", "/api/v1/audit/screen-views", service(), one).statusCode()).isEqualTo(401);
        assertThat(call("POST", "/api/v1/audit/screen-views", bearer(token("svc", "SERVICE")), one).statusCode()).isEqualTo(401);

        StringBuilder fifty = new StringBuilder("{\"events\":[");
        for (int i = 0; i < 50; i++) {
            fifty.append(i == 0 ? "" : ",").append("{\"screenKey\":\"home.dashboard\"}");
        }
        fifty.append("]}");
        String busy = token("busy-user", "DRIVER");
        assertThat(call("POST", "/api/v1/audit/screen-views", bearer(busy), fifty.toString()).statusCode()).isEqualTo(200);
        StringBuilder twenty = new StringBuilder("{\"events\":[");
        for (int i = 0; i < 20; i++) {
            twenty.append(i == 0 ? "" : ",").append("{\"screenKey\":\"home.dashboard\"}");
        }
        twenty.append("]}");
        HttpResponse<String> limited = call("POST", "/api/v1/audit/screen-views", bearer(busy), twenty.toString());
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
        assertThat(json(limited).get("responseObject").get("dropped").asInt()).isEqualTo(10);
        assertThat(json(call("GET", "/api/v1/audit/events?userId=busy-user", bearer(token("a", "ADMIN")), null))
                .get("responseObject").get("totalElements").asInt()).isEqualTo(60);
        assertThat(call("POST", "/api/v1/audit/screen-views", bearer(token("calm-user", "DRIVER")), one).statusCode()).isEqualTo(200);
    }

    // ---------------------------------------------------------------- queries

    @Test
    void queryEndpointsRequireAUserToken() throws Exception {
        assertThat(call("GET", "/api/v1/audit/events", Map.of(), null).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/api/v1/audit/events/facets", service(), null).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/api/v1/audit/events/1/payload", Map.of(), null).statusCode()).isEqualTo(401);
    }

    @Test
    void filtersCombineAndPagingIsNewestFirstAndCapped() throws Exception {
        Instant base = Instant.now().minus(Duration.ofHours(1));
        List<AuditEvent> events = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            events.add(event("evt-page-000" + i, AuditEventType.ACCESS, "pager", base.plusSeconds(i)));
        }
        AuditEvent special = event("evt-special-01", AuditEventType.COMMUNICATION, "other", base.plusSeconds(100));
        special.setRole("SUPPORT");
        special.setRoles(List.of("SUPPORT", "AUDITOR"));
        special.setPrivilegeIds(List.of(7, 99));
        special.setFeature("nms");
        special.setScreenKey("notifications.log");
        special.setOutcome(AuditOutcome.DENIED);
        special.setAppChannel(com.railcab.audit.domain.AppChannel.ADMIN_WEB);
        special.setMethod("POST");
        special.setPath("/api/Rider/50%_Off/Special");
        events.add(special);
        assertThat(repository.insertNew(events)).hasSize(6);
        assertThat(repository.insertNew(events)).as("idempotent at the port").isEmpty();

        AuditEventFilter all = filter(null, null, null, null, null, null, null, null, null, null, null);
        assertThat(repository.search(all, 0, 100).totalElements()).isEqualTo(6);
        assertThat(repository.search(all, 0, 100).content().get(0).getEventId()).isEqualTo("evt-special-01");
        var page = repository.search(filter("pager", null, null, null, null, null, null, null, null, null, null), 1, 2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.content()).extracting(AuditEvent::getEventId).containsExactly("evt-page-0002", "evt-page-0001");

        assertThat(ids(filter(null, "AUDITOR", null, null, null, null, null, null, null, null, null))).containsExactly("evt-special-01");
        assertThat(ids(filter(null, "SUPPORT", null, null, null, null, null, null, null, null, null))).containsExactly("evt-special-01");
        assertThat(ids(filter(null, "SUPPOR", null, null, null, null, null, null, null, null, null))).isEmpty();
        assertThat(ids(filter(null, null, "nms", null, null, null, null, null, null, null, null))).containsExactly("evt-special-01");
        assertThat(ids(filter(null, null, null, "notifications.log", null, null, null, null, null, null, null))).containsExactly("evt-special-01");
        assertThat(ids(filter(null, null, null, null, AuditEventType.COMMUNICATION, null, null, null, null, null, null))).containsExactly("evt-special-01");
        assertThat(ids(filter(null, null, null, null, null, AuditOutcome.DENIED, null, null, null, null, null))).containsExactly("evt-special-01");
        assertThat(ids(filter(null, null, null, null, null, null, com.railcab.audit.domain.AppChannel.ADMIN_WEB, null, null, null, null))).containsExactly("evt-special-01");
        assertThat(ids(filter(null, null, null, null, null, null, null, 99, null, null, null))).containsExactly("evt-special-01");
        assertThat(ids(filter(null, null, null, null, null, null, null, 9, null, null, null))).isEmpty();
        assertThat(ids(filter(null, null, null, null, null, null, null, null, "post", null, null))).containsExactly("evt-special-01");
        assertThat(ids(filter(null, null, null, null, null, null, null, null, null, "50%_off", null))).containsExactly("evt-special-01");
        assertThat(ids(filter(null, null, null, null, null, null, null, null, null, "5__ff", null))).as("wildcards are literal").isEmpty();
        assertThat(ids(filter(null, null, null, null, null, null, null, null, null, null, "c-evt-page-0003"))).containsExactly("evt-page-0003");
        AuditEventFilter window = new AuditEventFilter("pager", null, null, null, null, null, null, null, null, null, null,
                base.plusSeconds(1), base.plusSeconds(3));
        assertThat(repository.search(window, 0, 10).content()).extracting(AuditEvent::getEventId)
                .containsExactly("evt-page-0002", "evt-page-0001");

        JsonNode huge = json(call("GET", "/api/v1/audit/events?size=100000", bearer(token("a", "ADMIN")), null)).get("responseObject");
        assertThat(huge.get("size").asInt()).isEqualTo(200);
        assertThat(call("GET", "/api/v1/audit/events?eventType=BOGUS", bearer(token("a", "ADMIN")), null).statusCode()).isEqualTo(400);
        assertThat(call("GET", "/api/v1/audit/events?from=yesterday", bearer(token("a", "ADMIN")), null).statusCode()).isEqualTo(400);
    }

    @Test
    void singleCorrelationFacetsAndExportWork() throws Exception {
        Instant t = Instant.now().minus(Duration.ofMinutes(5));
        AuditEvent a = event("evt-corr-0001", AuditEventType.ACCESS, "u1", t);
        a.setCorrelationId("chain-1");
        AuditEvent b = event("evt-corr-0002", AuditEventType.COMMUNICATION, "u1", t.plusSeconds(2));
        b.setCorrelationId("chain-1");
        b.setRoles(List.of("SUPPORT"));
        b.setScreenKey("notifications.log");
        b.setUserId("=HYPERLINK(\"x\")");
        repository.insertNew(List.of(b, a));
        String admin = token("admin", "ADMIN");

        JsonNode chain = json(call("GET", "/api/v1/audit/events/correlation/chain-1", bearer(admin), null)).get("responseObject");
        assertThat(chain).hasSize(2);
        assertThat(chain.get(0).get("eventId").asText()).isEqualTo("evt-corr-0001");

        long id = chain.get(0).get("id").asLong();
        assertThat(json(call("GET", "/api/v1/audit/events/" + id, bearer(admin), null)).get("responseObject").get("eventId").asText())
                .isEqualTo("evt-corr-0001");
        assertThat(call("GET", "/api/v1/audit/events/987654321", bearer(admin), null).statusCode()).isEqualTo(404);

        JsonNode facets = json(call("GET", "/api/v1/audit/events/facets", bearer(admin), null)).get("responseObject");
        assertThat(facets.get("eventTypes")).extracting(JsonNode::asText).contains("ACCESS", "COMMUNICATION");
        assertThat(facets.get("roles")).extracting(JsonNode::asText).contains("ADMIN", "SUPPORT");
        assertThat(facets.get("features")).extracting(JsonNode::asText).contains("rider-config");
        assertThat(facets.get("screenKeys")).extracting(JsonNode::asText).contains("notifications.log");
        assertThat(facets.get("outcomes")).extracting(JsonNode::asText).contains("SUCCESS");

        HttpResponse<String> csv = call("GET", "/api/v1/audit/events/export?correlationId=chain-1", bearer(admin), null);
        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.headers().firstValue("Content-Type").orElse("")).startsWith("text/csv");
        assertThat(csv.body().split("\r\n")).hasSize(3);
        assertThat(csv.body()).contains("'=HYPERLINK").doesNotContain(",=HYPERLINK");
    }

    @Test
    void eventsOutsideTheFacetWindowAreNotListedInFacets() {
        Instant old = Instant.now().minus(Duration.ofDays(60));
        AuditEvent oldEvent = event("evt-facet-old1", AuditEventType.DOMAIN, "u", old);
        oldEvent.setFeature("legacy-feature");
        oldEvent.setExpireAt(Instant.now().plus(Duration.ofDays(1)));
        repository.insertNew(List.of(oldEvent));
        assertThat(repository.facets(Instant.now().minus(Duration.ofDays(30)), 500, com.railcab.audit.domain.TenantScope.ALL).features()).doesNotContain("legacy-feature");
        assertThat(repository.facets(Instant.now().minus(Duration.ofDays(90)), 500, com.railcab.audit.domain.TenantScope.ALL).features()).contains("legacy-feature");
    }

    // ---------------------------------------------------------------- organization separation

    private AuditEvent orgEvent(String eventId, String tenantId, Instant at) {
        AuditEvent e = event(eventId, AuditEventType.ACCESS, "u-" + eventId, at);
        e.setTenantId(tenantId);
        e.setCorrelationId("org-chain");
        e.setFeature("feature-" + (tenantId == null ? "legacy" : tenantId));
        return e;
    }

    private List<String> listedEventIds(Map<String, String> headers) throws Exception {
        JsonNode page = json(call("GET", "/api/v1/audit/events?correlationId=org-chain&size=50", headers, null))
                .get("responseObject").get("content");
        List<String> ids = new ArrayList<>();
        page.forEach(n -> ids.add(n.get("eventId").asText()));
        return ids;
    }

    private long idOf(String eventId) {
        return repository.search(new AuditEventFilter(null, null, null, null, null, null, null, null, null, null,
                "org-chain", null, null), 0, 50).content().stream()
                .filter(e -> e.getEventId().equals(eventId)).findFirst().orElseThrow().getId();
    }

    @Test
    void readersSeeOnlyTheirOrganizationPlusLegacyRecords() throws Exception {
        Instant t = Instant.now().minus(Duration.ofMinutes(5));
        AuditEvent a = orgEvent("evt-org-a-0001", "org-a", t);
        AuditEvent b = orgEvent("evt-org-b-0001", "org-b", t.plusSeconds(1));
        AuditEvent legacy = orgEvent("evt-org-none-01", null, t.plusSeconds(2));
        repository.insertNew(List.of(a, b, legacy));
        repository.insertPayloads(List.of(
                AuditPayload.builder().eventId("evt-org-a-0001").requestBody("a").expireAt(t.plus(Duration.ofDays(1))).build(),
                AuditPayload.builder().eventId("evt-org-b-0001").requestBody("b").expireAt(t.plus(Duration.ofDays(1))).build()));
        Map<String, String> inA = bearer(tokenInOrganization("admin-a", "org-a", "ADMIN"));
        Map<String, String> noOrg = bearer(token("admin-x", "ADMIN"));

        assertThat(listedEventIds(inA)).containsExactlyInAnyOrder("evt-org-a-0001", "evt-org-none-01");
        assertThat(listedEventIds(bearer(tokenInOrganization("admin-b", "org-b", "ADMIN"))))
                .containsExactlyInAnyOrder("evt-org-b-0001", "evt-org-none-01");
        assertThat(listedEventIds(noOrg)).as("a session without an organization sees only unowned records")
                .containsExactly("evt-org-none-01");

        long idB = idOf("evt-org-b-0001");
        long idA = idOf("evt-org-a-0001");
        assertThat(call("GET", "/api/v1/audit/events/" + idB, inA, null).statusCode()).as("other organization looks absent").isEqualTo(404);
        assertThat(call("GET", "/api/v1/audit/events/" + idA, inA, null).statusCode()).isEqualTo(200);
        assertThat(call("GET", "/api/v1/audit/events/" + idB + "/payload", inA, null).statusCode()).isEqualTo(404);
        assertThat(call("GET", "/api/v1/audit/events/" + idA + "/payload", inA, null).statusCode()).isEqualTo(200);
        assertThat(call("GET", "/api/v1/audit/events/" + idA, noOrg, null).statusCode()).isEqualTo(404);

        JsonNode chain = json(call("GET", "/api/v1/audit/events/correlation/org-chain", inA, null)).get("responseObject");
        assertThat(chain).extracting(n -> n.get("eventId").asText()).containsExactly("evt-org-a-0001", "evt-org-none-01");

        JsonNode facets = json(call("GET", "/api/v1/audit/events/facets", inA, null)).get("responseObject");
        assertThat(facets.get("features")).extracting(JsonNode::asText).contains("feature-org-a", "feature-legacy")
                .doesNotContain("feature-org-b");

        String csv = call("GET", "/api/v1/audit/events/export?correlationId=org-chain", inA, null).body();
        assertThat(csv).contains("evt-org-a-0001", "evt-org-none-01").doesNotContain("evt-org-b-0001");
        String payloadCsv = call("GET", "/api/v1/audit/events/export/payloads?correlationId=org-chain", inA, null).body();
        assertThat(payloadCsv).contains("evt-org-a-0001").doesNotContain("evt-org-b-0001");
    }

    @Test
    void crossOrganizationViewNeedsTheGatewayMarkAndTheAsk() throws Exception {
        Instant t = Instant.now().minus(Duration.ofMinutes(5));
        repository.insertNew(List.of(orgEvent("evt-wide-a-0001", "org-a", t), orgEvent("evt-wide-b-0001", "org-b", t.plusSeconds(1)),
                orgEvent("evt-wide-none-01", null, t.plusSeconds(2))));
        String token = tokenInOrganization("wide-admin", "org-a", "ADMIN");
        Map<String, String> both = Map.of("Authorization", "Bearer " + token, "X-Org-Wide", "true", "X-View-All-Organizations", "true");
        Map<String, String> markOnly = Map.of("Authorization", "Bearer " + token, "X-Org-Wide", "true");
        Map<String, String> askOnly = Map.of("Authorization", "Bearer " + token, "X-View-All-Organizations", "true");

        assertThat(listedEventIds(both)).containsExactlyInAnyOrder("evt-wide-a-0001", "evt-wide-b-0001", "evt-wide-none-01");
        assertThat(listedEventIds(markOnly)).containsExactlyInAnyOrder("evt-wide-a-0001", "evt-wide-none-01");
        assertThat(listedEventIds(askOnly)).as("a caller the gateway did not mark can not widen").containsExactlyInAnyOrder("evt-wide-a-0001", "evt-wide-none-01");
    }

    @Test
    void ingestStampsTheOrganizationOfTheEventAndOfTheScreenViewer() throws Exception {
        call("POST", "/api/v1/audit/internal/events", service(), ingestBody(
                InputBuilder.access().eventId("evt-stamp-0001").tenantId("org-a").correlationId("stamp-chain").build(),
                InputBuilder.access().eventId("evt-stamp-0002").correlationId("stamp-chain").build()));
        String views = "{\"events\":[{\"eventId\":\"sv-stamp-0001\",\"screenKey\":\"payments.refunds\",\"appChannel\":\"ADMIN_WEB\"}]}";
        assertThat(call("POST", "/api/v1/audit/screen-views", bearer(tokenInOrganization("viewer", "org-b", "ADMIN")), views).statusCode()).isEqualTo(200);

        AuditEventFilter all = new AuditEventFilter(null, null, null, null, null, null, null, null, null, null, null, null, null);
        Map<String, String> tenants = new java.util.HashMap<>();
        repository.search(all, 0, 100).content().forEach(e -> tenants.put(e.getEventId(), e.getTenantId()));
        assertThat(tenants).containsEntry("evt-stamp-0001", "org-a").containsEntry("evt-stamp-0002", null)
                .containsEntry("sv-stamp-0001", "org-b");
    }

    // ---------------------------------------------------------------- misc

    @Test
    void actuatorHealthIsOpen() throws Exception {
        assertThat(call("GET", "/api/v1/audit/actuator/health", Map.of(), null).statusCode()).isEqualTo(200);
    }

    private AuditEventFilter filter(String userId, String role, String feature, String screenKey, AuditEventType type,
                                    AuditOutcome outcome, com.railcab.audit.domain.AppChannel channel, Integer privilegeId,
                                    String method, String path, String correlationId) {
        return new AuditEventFilter(userId, role, feature, screenKey, type, outcome, channel, privilegeId, method, path,
                correlationId, null, null);
    }

    private List<String> ids(AuditEventFilter f) {
        return repository.search(f, 0, 50).content().stream().map(AuditEvent::getEventId).toList();
    }

    // ---------------------------------------------------------------- subject and reason

    @Test
    void subjectAndReasonRoundTripThroughIngestAndAreFoundByTheNewFilters() throws Exception {
        Instant t = Instant.now().minus(Duration.ofMinutes(5));
        assertThat(call("POST", "/api/v1/audit/internal/events", service(), ingestBody(
                InputBuilder.access().eventId("evt-subj-0001").eventType("DOMAIN").feature("dms").module("DOCUMENTS")
                        .subjectType("document").subjectId("doc_1").reasonCode("virus_detected").correlationId("subj-chain")
                        .occurredAt(t).details(Map.of("documentType", "PAN", "scanVerdict", "INFECTED")).build(),
                InputBuilder.access().eventId("evt-subj-0002").eventType("DOMAIN").feature("dms").module("DOCUMENTS")
                        .subjectType("DOCUMENT").subjectId("doc_2").correlationId("subj-chain").occurredAt(t.plusSeconds(1)).build(),
                InputBuilder.access().eventId("evt-subj-0003").subjectType("PAYMENT").subjectId("doc_1")
                        .reasonCode("PROVIDER_TIMEOUT").correlationId("subj-chain").occurredAt(t.plusSeconds(2)).build(),
                InputBuilder.access().eventId("evt-subj-0004").correlationId("subj-chain").occurredAt(t.plusSeconds(3)).build()))
                .statusCode()).isEqualTo(200);
        Map<String, String> admin = bearer(token("auditor", "ADMIN"));

        AuditEvent stored = repository.search(new AuditEventFilter(null, null, null, null, null, null, null, null, null, null,
                "subj-chain", null, null, "DOCUMENT", "doc_1", "VIRUS_DETECTED", null), 0, 10).content().get(0);
        assertThat(stored.getEventId()).isEqualTo("evt-subj-0001");
        assertThat(stored.getSubjectType()).isEqualTo("DOCUMENT");
        assertThat(stored.getSubjectId()).isEqualTo("doc_1");
        assertThat(stored.getReasonCode()).isEqualTo("VIRUS_DETECTED");

        String base = "/api/v1/audit/events?correlationId=subj-chain&size=50";
        assertThat(eventIds(admin, base + "&subjectType=DOCUMENT")).containsExactly("evt-subj-0002", "evt-subj-0001");
        assertThat(eventIds(admin, base + "&subjectType=document")).as("filter input is case-insensitive")
                .containsExactly("evt-subj-0002", "evt-subj-0001");
        assertThat(eventIds(admin, base + "&subjectType=DOCUMENT&subjectId=doc_1")).containsExactly("evt-subj-0001");
        assertThat(eventIds(admin, base + "&subjectId=doc_1")).containsExactly("evt-subj-0003", "evt-subj-0001");
        assertThat(eventIds(admin, base + "&reasonCode=VIRUS_DETECTED")).containsExactly("evt-subj-0001");
        assertThat(eventIds(admin, base + "&reasonCode=VIRUS")).as("exact match, not prefix").isEmpty();
        assertThat(eventIds(admin, base + "&subjectType=DOCUMENT&reasonCode=PROVIDER_TIMEOUT")).isEmpty();
        assertThat(eventIds(admin, "/api/v1/audit/events?correlationId=subj-chain&subjectType=DOCUMENT&size=1&page=1")).containsExactly("evt-subj-0001");
        assertThat(call("GET", base + "&subjectType=DOC%3BDROP", admin, null).statusCode()).isEqualTo(400);
        assertThat(call("GET", base + "&subjectId=%7B%22%24ne%22%3A1%7D", admin, null).statusCode()).isEqualTo(400);

        JsonNode first = json(call("GET", base + "&reasonCode=VIRUS_DETECTED", admin, null)).get("responseObject").get("content").get(0);
        assertThat(first.get("subjectType").asText()).isEqualTo("DOCUMENT");
        assertThat(first.get("subjectId").asText()).isEqualTo("doc_1");
        assertThat(first.get("reasonCode").asText()).isEqualTo("VIRUS_DETECTED");
        assertThat(first.get("details").get("scanVerdict").asText()).isEqualTo("INFECTED");

        String csv = call("GET", "/api/v1/audit/events/export?correlationId=subj-chain&subjectType=DOCUMENT&subjectId=doc_1", admin, null).body();
        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).endsWith(",tenantId,details,subjectType,subjectId,reasonCode");
        assertThat(lines[1]).endsWith(",DOCUMENT,doc_1,VIRUS_DETECTED");
        String payloadCsv = call("GET", "/api/v1/audit/events/export/payloads?correlationId=subj-chain&reasonCode=VIRUS_DETECTED", admin, null).body();
        assertThat(payloadCsv.split("\r\n")[0]).endsWith(",bodyContentType,subjectType,subjectId,reasonCode");

        JsonNode facets = json(call("GET", "/api/v1/audit/events/facets", admin, null)).get("responseObject");
        assertThat(facets.get("subjectTypes")).extracting(JsonNode::asText).contains("DOCUMENT", "PAYMENT");
        assertThat(facets.get("reasonCodes")).extracting(JsonNode::asText).contains("VIRUS_DETECTED", "PROVIDER_TIMEOUT");
    }

    @Test
    void invalidSubjectOrReasonOnIngestRejectsOnlyThatEvent() throws Exception {
        JsonNode r = json(call("POST", "/api/v1/audit/internal/events", service(), ingestBody(
                InputBuilder.access().eventId("evt-badsubj-01").subjectType("not valid").build(),
                InputBuilder.access().eventId("evt-badsubj-02").subjectId("a b").build(),
                InputBuilder.access().eventId("evt-badsubj-03").reasonCode("x".repeat(61)).build(),
                InputBuilder.access().eventId("evt-badsubj-04").subjectType("DOCUMENT").build()))).get("responseObject");
        assertThat(r.get("accepted").asInt()).isEqualTo(1);
        assertThat(r.get("rejected").asInt()).isEqualTo(3);
    }

    @Test
    void subjectAndReasonFiltersAndFacetsNeverCrossOrganizations() throws Exception {
        Instant t = Instant.now().minus(Duration.ofMinutes(5));
        AuditEvent a = subjectEvent("evt-sub-org-a-01", "org-a", "DOCUMENT", "shared-doc", "REASON_A", t);
        AuditEvent b = subjectEvent("evt-sub-org-b-01", "org-b", "DOCUMENT", "shared-doc", "REASON_B", t.plusSeconds(1));
        AuditEvent legacy = subjectEvent("evt-sub-none-01", null, "DOCUMENT", "shared-doc", "REASON_LEGACY", t.plusSeconds(2));
        AuditEvent onlyB = subjectEvent("evt-sub-org-b-02", "org-b", "SECRET_B_TYPE", "b-only", "ONLY_B_REASON", t.plusSeconds(3));
        repository.insertNew(List.of(a, b, legacy, onlyB));
        Map<String, String> inA = bearer(tokenInOrganization("admin-a", "org-a", "ADMIN"));
        Map<String, String> inB = bearer(tokenInOrganization("admin-b", "org-b", "ADMIN"));
        Map<String, String> noOrg = bearer(token("admin-x", "ADMIN"));
        String base = "/api/v1/audit/events?correlationId=sub-chain&size=50";

        assertThat(eventIds(inA, base + "&subjectType=DOCUMENT&subjectId=shared-doc"))
                .containsExactlyInAnyOrder("evt-sub-org-a-01", "evt-sub-none-01");
        assertThat(eventIds(inB, base + "&subjectType=DOCUMENT&subjectId=shared-doc"))
                .containsExactlyInAnyOrder("evt-sub-org-b-01", "evt-sub-none-01");
        assertThat(eventIds(noOrg, base + "&subjectType=DOCUMENT")).containsExactly("evt-sub-none-01");
        assertThat(eventIds(inA, base + "&reasonCode=REASON_B")).as("B's reason is invisible to A").isEmpty();
        assertThat(eventIds(inA, base + "&subjectType=SECRET_B_TYPE")).isEmpty();
        assertThat(eventIds(inA, base + "&subjectId=b-only")).isEmpty();
        assertThat(call("GET", "/api/v1/audit/events/export?correlationId=sub-chain&subjectType=DOCUMENT", inA, null).body())
                .contains("evt-sub-org-a-01").doesNotContain("evt-sub-org-b-01").doesNotContain("REASON_B");

        JsonNode facets = json(call("GET", "/api/v1/audit/events/facets", inA, null)).get("responseObject");
        assertThat(facets.get("subjectTypes")).extracting(JsonNode::asText).contains("DOCUMENT").doesNotContain("SECRET_B_TYPE");
        assertThat(facets.get("reasonCodes")).extracting(JsonNode::asText).contains("REASON_A", "REASON_LEGACY")
                .doesNotContain("REASON_B", "ONLY_B_REASON");
        JsonNode noOrgFacets = json(call("GET", "/api/v1/audit/events/facets", noOrg, null)).get("responseObject");
        assertThat(noOrgFacets.get("reasonCodes")).extracting(JsonNode::asText).contains("REASON_LEGACY")
                .doesNotContain("REASON_A", "REASON_B");
    }

    private AuditEvent subjectEvent(String eventId, String tenantId, String type, String id, String reason, Instant at) {
        AuditEvent e = event(eventId, AuditEventType.DOMAIN, "u-" + eventId, at);
        e.setTenantId(tenantId);
        e.setCorrelationId("sub-chain");
        e.setSubjectType(type);
        e.setSubjectId(id);
        e.setReasonCode(reason);
        return e;
    }

    private List<String> eventIds(Map<String, String> headers, String path) throws Exception {
        List<String> ids = new ArrayList<>();
        json(call("GET", path, headers, null)).get("responseObject").get("content").forEach(n -> ids.add(n.get("eventId").asText()));
        return ids;
    }
}
