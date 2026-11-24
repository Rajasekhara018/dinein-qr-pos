package com.heuristiq.gateway.audit;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditPayloadRedactorTest {

    @Test
    void headersWithCredentialsAreMaskedAndNamesKept() {
        Map<String, String> in = new LinkedHashMap<>();
        in.put("Authorization", "Bearer abc.def.ghi");
        in.put("cookie", "SESSION=1");
        in.put("Set-Cookie", "a=b");
        in.put("X-Rider-Service-Token", "svc");
        in.put("X-Gateway-Token", "gw");
        in.put("X-NMS-API-Key", "nms");
        in.put("X-Custom-Secret", "s");
        in.put("X-Db-Password", "p");
        in.put("Content-Type", "application/json");
        in.put("User-Agent", "Mozilla/5.0");
        in.put("X-Note", "see Bearer abcdefghijklmnopqrstu please");
        Map<String, String> out = AuditPayloadRedactor.redactHeaders(in, 60);
        for (String k : new String[]{"Authorization", "cookie", "Set-Cookie", "X-Rider-Service-Token", "X-Gateway-Token",
                "X-NMS-API-Key", "X-Custom-Secret", "X-Db-Password"}) {
            assertThat(out).containsEntry(k, "***");
        }
        assertThat(out).containsEntry("Content-Type", "application/json").containsEntry("User-Agent", "Mozilla/5.0");
        assertThat(out.get("X-Note")).doesNotContain("abcdefghijklmnopqrstu");
    }

    @Test
    void theTripShareTokenHeaderIsNeverStoredInClearInAnySpelling() {
        Map<String, String> in = new LinkedHashMap<>();
        in.put("X-Share-Token", "Zm9vYmFyLXNoYXJlLXRva2VuLTMyLWJ5dGVz");
        in.put("x-share-token", "lower");
        in.put("X-SHARE-TOKEN", "upper");
        in.put("Accept", "application/json");
        Map<String, String> out = AuditPayloadRedactor.redactHeaders(in, 60);

        assertThat(out).containsEntry("X-Share-Token", "***").containsEntry("x-share-token", "***")
                .containsEntry("X-SHARE-TOKEN", "***").containsEntry("Accept", "application/json");
        assertThat(AuditPayloadRedactor.isSensitiveHeader(com.heuristiq.gateway.config.RidePublicRouteConstants.SHARE_TOKEN_HEADER)).isTrue();
        assertThat(out.values()).noneMatch(v -> v.contains("Zm9vYmFy"));
    }

    @Test
    void headerCountIsBoundedAndEmptyBecomesNull() {
        Map<String, String> many = new LinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            many.put("H" + i, "v");
        }
        assertThat(AuditPayloadRedactor.redactHeaders(many, 10)).hasSize(10);
        assertThat(AuditPayloadRedactor.redactHeaders(Map.of(), 10)).isNull();
        assertThat(AuditPayloadRedactor.redactHeaders(null, 10)).isNull();
    }

    @Test
    void jsonSecretsAreRedactedRecursivelyThroughObjectsAndArrays() {
        String body = "{\"user\":\"ann\",\"Password\":\"p1\",\"profile\":{\"PIN\":1234,\"otp\":\"998877\","
                + "\"deep\":[{\"accessToken\":\"t\"},{\"ok\":1}]},\"list\":[{\"refresh_token\":\"r\"}],"
                + "\"client_secret\":\"s\",\"apiKey\":\"k\",\"cvv\":\"123\",\"authorization\":\"a\",\"signature\":\"sg\"}";
        String out = AuditPayloadRedactor.redactBody(body, "application/json");
        assertThat(out).contains("\"user\":\"ann\"").contains("\"ok\":1");
        for (String secret : new String[]{"p1", "1234", "998877", "\"t\"", "\"r\"", "\"s\"", "\"k\"", "\"123\"", "\"a\"", "\"sg\""}) {
            assertThat(out).as(secret).doesNotContain(secret);
        }
        assertThat(out).contains("\"Password\":\"***\"").contains("\"PIN\":\"***\"").contains("\"accessToken\":\"***\"")
                .contains("\"refresh_token\":\"***\"");
    }

    @Test
    void cardAndAccountKeepLastFourPhoneEmailAndVpaAreMasked() {
        String body = "{\"cardNumber\":\"4111 1111 1111 1234\",\"card_number\":\"5555555555554444\","
                + "\"accountNumber\":\"000123456789\",\"ifsc\":\"HDFC0001234\",\"phone\":\"9876543210\","
                + "\"email\":\"john.doe@example.com\",\"vpa\":\"johndoe@okbank\",\"name\":\"John Doe\"}";
        String out = AuditPayloadRedactor.redactBody(body, "application/json");
        assertThat(out).contains("\"cardNumber\":\"***1234\"").contains("\"card_number\":\"***4444\"")
                .contains("\"accountNumber\":\"***6789\"").contains("\"ifsc\":\"HDFC0001234\"")
                .contains("\"phone\":\"******3210\"").contains("\"email\":\"j***@example.com\"")
                .contains("\"vpa\":\"jo***@okbank\"").contains("\"name\":\"John Doe\"");
        assertThat(out).doesNotContain("4111").doesNotContain("5555555555554444").doesNotContain("johndoe@okbank");
    }

    @Test
    void tokenLookingValuesUnderOrdinaryKeysAreScrubbed() {
        String out = AuditPayloadRedactor.redactBody("{\"data\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig\",\"h\":[\"Bearer abcdefghijkl\"]}",
                "application/json");
        assertThat(out).doesNotContain("eyJhbGci").doesNotContain("abcdefghijkl");
    }

    @Test
    void truncatedJsonFallsBackToTextScan() {
        String cut = "{\"user\":\"ann\",\"password\":\"hunter2\",\"otp\":987654,\"note\":\"abc\",\"token\":\"unterminat";
        String out = AuditPayloadRedactor.redactBody(cut, "application/json");
        assertThat(out).doesNotContain("hunter2").doesNotContain("987654").doesNotContain("unterminat");
        assertThat(out).contains("\"user\":\"ann\"").contains("\"password\":\"***\"");
    }

    @Test
    void formBodiesAreRedactedWithTheSameKeys() {
        String out = AuditPayloadRedactor.redactBody("username=ann&password=hunter2&otp=123456&cardNumber=4111111111111111"
                + "&email=a%40b.com&city=Pune", "application/x-www-form-urlencoded");
        assertThat(out).contains("username=ann").contains("password=***").contains("otp=***")
                .contains("cardNumber=***1111").contains("email=a***@b.com").contains("city=Pune");
        assertThat(out).doesNotContain("hunter2").doesNotContain("123456");
    }

    @Test
    void plainTextWithKeyValuePairsIsScanned() {
        String out = AuditPayloadRedactor.redactBody("login ok, token=abc123 password: hunter2 name=Bob", "text/plain");
        assertThat(out).doesNotContain("abc123").doesNotContain("hunter2").contains("name=Bob");
    }

    @Test
    void bodiesWithoutSecretsAreUnchangedInMeaning() {
        assertThat(AuditPayloadRedactor.redactBody("{\"a\":[1,2,{\"b\":null}]}", "application/json")).isEqualTo("{\"a\":[1,2,{\"b\":null}]}");
        assertThat(AuditPayloadRedactor.redactBody("", "application/json")).isEmpty();
        assertThat(AuditPayloadRedactor.redactBody(null, null)).isNull();
    }

    @Test
    void capturableContentTypes() {
        assertThat(AuditPayloadRedactor.isCapturableContentType("application/json; charset=utf-8")).isTrue();
        assertThat(AuditPayloadRedactor.isCapturableContentType("application/problem+json")).isTrue();
        assertThat(AuditPayloadRedactor.isCapturableContentType("text/plain")).isTrue();
        assertThat(AuditPayloadRedactor.isCapturableContentType("application/x-www-form-urlencoded")).isTrue();
        assertThat(AuditPayloadRedactor.isCapturableContentType("multipart/form-data; boundary=x")).isFalse();
        assertThat(AuditPayloadRedactor.isCapturableContentType("image/png")).isFalse();
        assertThat(AuditPayloadRedactor.isCapturableContentType("application/pdf")).isFalse();
        assertThat(AuditPayloadRedactor.isCapturableContentType("application/octet-stream")).isFalse();
    }
}
