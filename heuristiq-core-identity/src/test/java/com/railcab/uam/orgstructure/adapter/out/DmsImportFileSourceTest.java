package com.heuristiq.uam.orgstructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.uam.orgstructure.domain.ImportFileSource.ImportFile;
import com.heuristiq.uam.orgstructure.domain.OrgStructureException;

import reactor.core.publisher.Mono;

/** The DMS adapter against a stubbed HTTP exchange: no network, so each rule of the read is checked directly. */
class DmsImportFileSourceTest {
    private static final String CSV = "employee_code,full_name,department_code,position_name\nE1,Asha,TECH,TL\nE2,Ravi,TECH,SE\n";

    private final List<ClientRequest> requests = new ArrayList<>();

    private static String metadataJson(String fileName, String contentType, String scanStatus) {
        return "{\"success\":true,\"message\":\"Success\",\"responseObject\":{\"id\":\"doc-1\",\"category\":\"org-import\","
                + "\"ownerId\":\"user-9\",\"originalFileName\":\"" + fileName + "\",\"storedFileName\":\"stored-1.csv\","
                + "\"contentType\":\"" + contentType + "\",\"sizeBytes\":90,\"scanStatus\":\"" + scanStatus + "\",\"extra\":1}}";
    }

    private DmsImportFileSource sourceReturning(HttpStatus metadataStatus, String metadataBody, byte[] fileBytes) {
        ExchangeFunction exchange = request -> {
            requests.add(request);
            boolean isMetadata = request.url().getPath().contains("/metadata/");
            if (isMetadata) {
                return Mono.just(ClientResponse.create(metadataStatus)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(metadataBody).build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .body(new String(fileBytes, StandardCharsets.UTF_8)).build());
        };
        WebClient client = WebClient.builder().baseUrl("http://dms.test").exchangeFunction(exchange).build();
        ResilientCaller caller = new ResilientCaller("dms-test", ResilienceProfile.defaults().withMaxAttempts(1));
        return new DmsImportFileSource(client, caller);
    }

    @Test
    void readsParsesAndReturnsTheRowsOfACleanCsv() {
        ImportFile file = sourceReturning(HttpStatus.OK, metadataJson("staff.csv", "text/csv", "CLEAN"),
                CSV.getBytes(StandardCharsets.UTF_8)).fetch("doc-1", "user-9");

        assertThat(file.documentId()).isEqualTo("doc-1");
        assertThat(file.fileName()).isEqualTo("staff.csv");
        assertThat(file.rows()).extracting("employeeCode").containsExactly("E1", "E2");
    }

    @Test
    void identifiesTheCallerAndLooksUpThenDownloadsByStorageKey() {
        sourceReturning(HttpStatus.OK, metadataJson("staff.csv", "text/csv", "CLEAN"), CSV.getBytes(StandardCharsets.UTF_8))
                .fetch("doc-1", "user-9");

        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).url().getPath()).isEqualTo("/api/v1/dms/documents/metadata/doc-1");
        assertThat(requests.get(1).url().getPath()).isEqualTo("/api/v1/dms/documents/org-import/user-9/stored-1.csv");
        assertThat(requests.get(0).headers().getFirst("X-User-Id")).isEqualTo("user-9");
        assertThat(requests.get(0).headers().getFirst("X-User-Role")).isEqualTo("INTERNAL_SERVICE");
    }

    @Test
    void acceptsAFileWhoseScanWasSkippedButRefusesAnyOtherScanOutcome() {
        byte[] bytes = CSV.getBytes(StandardCharsets.UTF_8);
        assertThat(sourceReturning(HttpStatus.OK, metadataJson("staff.csv", "text/csv", "SKIPPED"), bytes)
                .fetch("doc-1", "u").rows()).hasSize(2);

        assertThatThrownBy(() -> sourceReturning(HttpStatus.OK, metadataJson("staff.csv", "text/csv", "INFECTED"), bytes)
                .fetch("doc-1", "u")).isInstanceOf(OrgStructureException.class).hasMessageContaining("scan");
        assertThat(requests.stream().filter(r -> !r.url().getPath().contains("/metadata/")).count())
                .as("a file that failed the scan is never downloaded").isEqualTo(1);   // only the SKIPPED one was downloaded
    }

    @Test
    void refusesAFileThatIsNotACsv() {
        assertThatThrownBy(() -> sourceReturning(HttpStatus.OK, metadataJson("photo.png", "image/png", "CLEAN"), new byte[] {1, 2})
                .fetch("doc-1", "u")).isInstanceOf(OrgStructureException.class).hasMessageContaining("CSV");
    }

    @Test
    void acceptsACsvByContentTypeEvenWithoutTheExtension() {
        ImportFile file = sourceReturning(HttpStatus.OK, metadataJson("export", "text/csv; charset=utf-8", "CLEAN"),
                CSV.getBytes(StandardCharsets.UTF_8)).fetch("doc-1", "u");
        assertThat(file.rows()).hasSize(2);
    }

    @Test
    void aMissingOrForbiddenDocumentIsAnAnswerNotAnOutage() {
        assertThatThrownBy(() -> sourceReturning(HttpStatus.NOT_FOUND, "{}", new byte[0]).fetch("nope", "u"))
                .isInstanceOf(OrgStructureException.class).extracting(e -> ((OrgStructureException) e).kind())
                .isEqualTo(OrgStructureException.Kind.NOT_FOUND);
        assertThatThrownBy(() -> sourceReturning(HttpStatus.FORBIDDEN, "{}", new byte[0]).fetch("doc-1", "u"))
                .isInstanceOf(OrgStructureException.class).hasMessageContaining("would not let");
    }

    /** The metadata of a document stored the way DMS stores a new upload, with the {@code objectKey} it is served at. */
    private static String metadataJsonWithKey(String objectKey) {
        return metadataJson("staff.csv", "text/csv", "CLEAN").replace("\"storedFileName\":\"stored-1.csv\",",
                "\"storedFileName\":\"stored-1.csv\",\"objectKey\":" + (objectKey == null ? "null" : "\"" + objectKey + "\"") + ",");
    }

    @Test
    void downloadsAtTheObjectKeyDmsGaveNotAKeyRebuiltFromTheOldLayout() {
        // The real production case: the key starts with the organization, so category/owner/file (the old layout) is a 404.
        sourceReturning(HttpStatus.OK, metadataJsonWithKey("no-organization/org-import/general/user-9/c3e9f3bb_staff.csv"),
                CSV.getBytes(StandardCharsets.UTF_8)).fetch("doc-1", "user-9");

        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).url().getPath())
                .isEqualTo("/api/v1/dms/documents/no-organization/org-import/general/user-9/c3e9f3bb_staff.csv");
    }

    @Test
    void aLeadingSlashInTheObjectKeyIsIgnored() {
        sourceReturning(HttpStatus.OK, metadataJsonWithKey("/org-1/org-import/general/user-9/a.csv"), CSV.getBytes(StandardCharsets.UTF_8))
                .fetch("doc-1", "user-9");

        assertThat(requests.get(1).url().getPath()).isEqualTo("/api/v1/dms/documents/org-1/org-import/general/user-9/a.csv");
    }

    @Test
    void aBlankObjectKeyFallsBackToTheOldLayoutOfDocumentsStoredBeforeOrganizations() {
        sourceReturning(HttpStatus.OK, metadataJsonWithKey(" "), CSV.getBytes(StandardCharsets.UTF_8)).fetch("doc-1", "user-9");
        sourceReturning(HttpStatus.OK, metadataJsonWithKey(null), CSV.getBytes(StandardCharsets.UTF_8)).fetch("doc-1", "user-9");

        assertThat(requests.get(1).url().getPath()).isEqualTo("/api/v1/dms/documents/org-import/user-9/stored-1.csv");
        assertThat(requests.get(3).url().getPath()).isEqualTo("/api/v1/dms/documents/org-import/user-9/stored-1.csv");
    }

    @Test
    void anObjectKeyThatCouldLeaveTheDocumentFolderIsRefusedBeforeAnyDownload() {
        for (String bad : new String[] {"../secrets/a.csv", "org-1/../../a.csv", "org-1//a.csv", "org-1/%2e%2e/a.csv", "org 1/a.csv", "http://evil/a.csv"}) {
            requests.clear();
            assertThatThrownBy(() -> sourceReturning(HttpStatus.OK, metadataJsonWithKey(bad), CSV.getBytes(StandardCharsets.UTF_8))
                    .fetch("doc-1", "user-9")).as(bad).isInstanceOf(OrgStructureException.class).hasMessageContaining("cannot be used");
            assertThat(requests).as("only the metadata call was made for " + bad).hasSize(1);
        }
    }

    @Test
    void anEmptyFileAndAFileWithoutTheRequiredColumnsAreRejectedClearly() {
        assertThatThrownBy(() -> sourceReturning(HttpStatus.OK, metadataJson("a.csv", "text/csv", "CLEAN"), new byte[0])
                .fetch("doc-1", "u")).isInstanceOf(OrgStructureException.class);
        assertThatThrownBy(() -> sourceReturning(HttpStatus.OK, metadataJson("a.csv", "text/csv", "CLEAN"),
                "employee_code,full_name\nE1,Asha\n".getBytes(StandardCharsets.UTF_8)).fetch("doc-1", "u"))
                .isInstanceOf(OrgStructureException.class).hasMessageContaining("department_code");
    }
}
