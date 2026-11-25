package com.heuristiq.identity.orgstructure.adapter.out;

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

import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderFile;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderRowInput;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;

import reactor.core.publisher.Mono;

/** Reading a ladders file back from the document store: the shared read rules, the ladders parsing and a store outage. */
class DmsLadderFileSourceTest {
    private static final String CSV = "ladder,position,rank,managerial\nTech,Engineer,3,no\nTech,Lead,6,yes\n";

    private final List<ClientRequest> requests = new ArrayList<>();

    private static String metadata(String fileName, String contentType, String scanStatus) {
        return "{\"success\":true,\"message\":\"Success\",\"responseObject\":{\"id\":\"doc-7\",\"category\":\"org-import\","
                + "\"ownerId\":\"user-9\",\"originalFileName\":\"" + fileName + "\",\"storedFileName\":\"stored.csv\","
                + "\"objectKey\":\"no-organization/org-import/general/user-9/stored.csv\","
                + "\"contentType\":\"" + contentType + "\",\"sizeBytes\":60,\"scanStatus\":\"" + scanStatus + "\"}}";
    }

    private DmsImportFileSource source(HttpStatus metadataStatus, String metadataBody, byte[] fileBytes) {
        ExchangeFunction exchange = request -> {
            requests.add(request);
            if (request.url().getPath().contains("/metadata/")) {
                return Mono.just(ClientResponse.create(metadataStatus)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(metadataBody).build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .body(new String(fileBytes, StandardCharsets.UTF_8)).build());
        };
        WebClient client = WebClient.builder().baseUrl("http://dms.test").exchangeFunction(exchange).build();
        return new DmsImportFileSource(client, new ResilientCaller("dms-test", ResilienceProfile.defaults().withMaxAttempts(1)));
    }

    @Test
    void readsAndParsesALadderFileAtTheLocationDmsGave() {
        LadderFile file = source(HttpStatus.OK, metadata("ladders.csv", "text/csv", "CLEAN"), CSV.getBytes(StandardCharsets.UTF_8))
                .fetchLadders("doc-7", "user-9");

        assertThat(file.documentId()).isEqualTo("doc-7");
        assertThat(file.fileName()).isEqualTo("ladders.csv");
        assertThat(file.rows()).containsExactly(new LadderRowInput(1, "Tech", "Engineer", "3", "no"),
                new LadderRowInput(2, "Tech", "Lead", "6", "yes"));
        assertThat(requests.get(1).url().getPath()).isEqualTo("/api/v1/dms/documents/no-organization/org-import/general/user-9/stored.csv");
        assertThat(requests.get(0).headers().getFirst("X-User-Id")).isEqualTo("user-9");
    }

    @Test
    void aFileThatFailedTheScanOrIsNotACsvIsRefused() {
        assertThatThrownBy(() -> source(HttpStatus.OK, metadata("d.csv", "text/csv", "INFECTED"), CSV.getBytes(StandardCharsets.UTF_8))
                .fetchLadders("doc-7", "u")).isInstanceOf(OrgStructureException.class).hasMessageContaining("scan");
        assertThatThrownBy(() -> source(HttpStatus.OK, metadata("d.pdf", "application/pdf", "CLEAN"), CSV.getBytes(StandardCharsets.UTF_8))
                .fetchLadders("doc-7", "u")).isInstanceOf(OrgStructureException.class).hasMessageContaining("must be a CSV");
    }

    @Test
    void aMissingDocumentIsNotFoundAndAnEmptyFileIsRefused() {
        assertThatThrownBy(() -> source(HttpStatus.NOT_FOUND, "{}", new byte[0]).fetchLadders("nope", "u"))
                .isInstanceOf(OrgStructureException.class).extracting(e -> ((OrgStructureException) e).kind())
                .isEqualTo(OrgStructureException.Kind.NOT_FOUND);
        assertThatThrownBy(() -> source(HttpStatus.OK, metadata("d.csv", "text/csv", "CLEAN"), new byte[0]).fetchLadders("doc-7", "u"))
                .isInstanceOf(OrgStructureException.class).hasMessageContaining("empty");
    }

    @Test
    void anEmployeeFileGivesAClearMissingColumnMessage() {
        byte[] employees = "employee_code,full_name,department_code,position_name\nE1,Asha,HR,TL\n".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> source(HttpStatus.OK, metadata("e.csv", "text/csv", "CLEAN"), employees).fetchLadders("doc-7", "u"))
                .isInstanceOf(OrgStructureException.class).hasMessage("Missing column(s): ladder, position, rank");
    }

    @Test
    void aDocumentStoreOutageIsAnOutageNotAnAnswerAboutTheFile() {
        ExchangeFunction down = request -> Mono.error(new java.net.ConnectException("connection refused"));
        WebClient client = WebClient.builder().baseUrl("http://dms.test").exchangeFunction(down).build();
        DmsImportFileSource source = new DmsImportFileSource(client, new ResilientCaller("dms-test", ResilienceProfile.defaults().withMaxAttempts(1)));

        assertThatThrownBy(() -> source.fetchLadders("doc-7", "u")).isInstanceOf(DownstreamUnavailableException.class);
    }
}
