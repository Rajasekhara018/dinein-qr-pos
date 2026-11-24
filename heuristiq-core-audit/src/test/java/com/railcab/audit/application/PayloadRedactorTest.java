package com.railcab.audit.application;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadRedactorTest {

    /** Public trip tracking sends its share token in X-Share-Token: masked again at ingest, whatever its case. */
    @Test
    void theTripShareTokenHeaderIsMaskedAtIngest() {
        Map<String, String> in = new LinkedHashMap<>();
        in.put("X-Share-Token", "Zm9vYmFyLXNoYXJlLXRva2Vu");
        in.put("x-share-token", "lower");
        in.put("Accept", "application/json");
        Map<String, String> out = PayloadRedactor.redactHeaders(in, 60);

        assertThat(out).containsEntry("X-Share-Token", "***").containsEntry("x-share-token", "***")
                .containsEntry("Accept", "application/json");
        assertThat(PayloadRedactor.isSensitiveHeader("X-SHARE-TOKEN")).isTrue();
    }

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
        Map<String, String> out = PayloadRedactor.redactHeaders(in, 60);
        for (String k : new String[]{"Authorization", "cookie", "Set-Cookie", "X-Rider-Service-Token", "X-Gateway-Token",
                "X-NMS-API-Key", "X-Custom-Secret", "X-Db-Password"}) {
            assertThat(out).containsEntry(k, "***");
        }
        assertThat(out).containsEntry("Content-Type", "application/json").containsEntry("User-Agent", "Mozilla/5.0");
        assertThat(out.get("X-Note")).doesNotContain("abcdefghijklmnopqrstu");
    }

    @Test
    void headerCountIsBoundedAndEmptyBecomesNull() {
        Map<String, String> many = new LinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            many.put("H" + i, "v");
        }
        assertThat(PayloadRedactor.redactHeaders(many, 10)).hasSize(10);
        assertThat(PayloadRedactor.redactHeaders(Map.of(), 10)).isNull();
        assertThat(PayloadRedactor.redactHeaders(null, 10)).isNull();
    }

    @Test
    void jsonSecretsAreRedactedRecursivelyThroughObjectsAndArrays() {
        String body = "{\"user\":\"ann\",\"Password\":\"p1\",\"profile\":{\"PIN\":1234,\"otp\":\"998877\","
                + "\"deep\":[{\"accessToken\":\"t\"},{\"ok\":1}]},\"list\":[{\"refresh_token\":\"r\"}],"
                + "\"client_secret\":\"s\",\"apiKey\":\"k\",\"cvv\":\"123\",\"authorization\":\"a\",\"signature\":\"sg\"}";
        String out = PayloadRedactor.redactBody(body, "application/json");
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
        String out = PayloadRedactor.redactBody(body, "application/json");
        assertThat(out).contains("\"cardNumber\":\"***1234\"").contains("\"card_number\":\"***4444\"")
                .contains("\"accountNumber\":\"***6789\"").contains("\"ifsc\":\"HDFC0001234\"")
                .contains("\"phone\":\"******3210\"").contains("\"email\":\"j***@example.com\"")
                .contains("\"vpa\":\"jo***@okbank\"").contains("\"name\":\"John Doe\"");
        assertThat(out).doesNotContain("4111").doesNotContain("5555555555554444").doesNotContain("johndoe@okbank");
    }

    @Test
    void tokenLookingValuesUnderOrdinaryKeysAreScrubbed() {
        String out = PayloadRedactor.redactBody("{\"data\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig\",\"h\":[\"Bearer abcdefghijkl\"]}",
                "application/json");
        assertThat(out).doesNotContain("eyJhbGci").doesNotContain("abcdefghijkl");
    }

    @Test
    void truncatedJsonFallsBackToTextScan() {
        String cut = "{\"user\":\"ann\",\"password\":\"hunter2\",\"otp\":987654,\"note\":\"abc\",\"token\":\"unterminat";
        String out = PayloadRedactor.redactBody(cut, "application/json");
        assertThat(out).doesNotContain("hunter2").doesNotContain("987654").doesNotContain("unterminat");
        assertThat(out).contains("\"user\":\"ann\"").contains("\"password\":\"***\"");
    }

    @Test
    void formBodiesAreRedactedWithTheSameKeys() {
        String out = PayloadRedactor.redactBody("username=ann&password=hunter2&otp=123456&cardNumber=4111111111111111"
                + "&email=a%40b.com&city=Pune", "application/x-www-form-urlencoded");
        assertThat(out).contains("username=ann").contains("password=***").contains("otp=***")
                .contains("cardNumber=***1111").contains("email=a***@b.com").contains("city=Pune");
        assertThat(out).doesNotContain("hunter2").doesNotContain("123456");
    }

    @Test
    void plainTextWithKeyValuePairsIsScanned() {
        String out = PayloadRedactor.redactBody("login ok, token=abc123 password: hunter2 name=Bob", "text/plain");
        assertThat(out).doesNotContain("abc123").doesNotContain("hunter2").contains("name=Bob");
    }

    @Test
    void bodiesWithoutSecretsAreUnchangedInMeaning() {
        assertThat(PayloadRedactor.redactBody("{\"a\":[1,2,{\"b\":null}]}", "application/json")).isEqualTo("{\"a\":[1,2,{\"b\":null}]}");
        assertThat(PayloadRedactor.redactBody("", "application/json")).isEmpty();
        assertThat(PayloadRedactor.redactBody(null, null)).isNull();
    }

    @Test
    void capturableContentTypes() {
        assertThat(PayloadRedactor.isCapturableContentType("application/json; charset=utf-8")).isTrue();
        assertThat(PayloadRedactor.isCapturableContentType("application/problem+json")).isTrue();
        assertThat(PayloadRedactor.isCapturableContentType("text/plain")).isTrue();
        assertThat(PayloadRedactor.isCapturableContentType("application/x-www-form-urlencoded")).isTrue();
        assertThat(PayloadRedactor.isCapturableContentType("multipart/form-data; boundary=x")).isFalse();
        assertThat(PayloadRedactor.isCapturableContentType("image/png")).isFalse();
        assertThat(PayloadRedactor.isCapturableContentType("application/pdf")).isFalse();
        assertThat(PayloadRedactor.isCapturableContentType("application/octet-stream")).isFalse();
    }
}
