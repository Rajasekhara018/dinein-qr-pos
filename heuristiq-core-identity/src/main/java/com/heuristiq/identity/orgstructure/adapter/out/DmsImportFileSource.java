package com.heuristiq.identity.orgstructure.adapter.out;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.identity.orgstructure.domain.DepartmentFileSource;
import com.heuristiq.identity.orgstructure.domain.LadderFileSource;
import com.heuristiq.identity.orgstructure.domain.RoleRuleFileSource;
import com.heuristiq.identity.orgstructure.domain.RoleRuleImportModels.RoleRuleFile;
import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderFile;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentFile;
import com.heuristiq.identity.orgstructure.domain.ImportFileSource;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;

/**
 * Reads an uploaded employee or department file out of Rider_DMS. The browser uploaded it there (so it was scanned and filed
 * by the document store); here the document is looked up, checked to be a clean CSV, downloaded and parsed.
 * Calls go through the resilient client: a DMS outage surfaces as {@code DownstreamUnavailableException}, and
 * both reads are GETs, so they are safe to retry.
 */
@Component
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class DmsImportFileSource implements ImportFileSource, DepartmentFileSource, LadderFileSource, RoleRuleFileSource {
    private static final Logger log = LoggerFactory.getLogger(DmsImportFileSource.class);

    /** The part of Rider_DMS's document response this feature needs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DmsDocument(String id, String category, String ownerId, String originalFileName, String storedFileName,
                       String objectKey, String contentType, long sizeBytes, String scanStatus) {
    }

    /** Rider_DMS's standard response envelope. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DmsEnvelope<T>(boolean success, String message, T responseObject) {
    }

    private final WebClient dms;
    private final ResilientCaller caller;

    DmsImportFileSource(@Qualifier(OrgStructureAdapterConstants.DMS_WEB_CLIENT_BEAN) WebClient dms, @Qualifier(OrgStructureAdapterConstants.DMS_RESILIENT_CALLER_BEAN) ResilientCaller caller) {
        this.dms = dms;
        this.caller = caller;
    }

    /** A clean CSV read back from the document store, before it is parsed for one kind of import. */
    private record CsvFile(String documentId, String fileName, String text) {
    }

    @Override
    public ImportFile fetch(String documentId, String callerId) {
        CsvFile csv = readCsv(documentId, callerId);
        return new ImportFile(csv.documentId(), csv.fileName(), ImportFileParser.parse(csv.text()));
    }

    @Override
    public DepartmentFile fetchDepartments(String documentId, String callerId) {
        CsvFile csv = readCsv(documentId, callerId);
        return new DepartmentFile(csv.documentId(), csv.fileName(), DepartmentCsvParser.parse(csv.text()));
    }

    @Override
    public LadderFile fetchLadders(String documentId, String callerId) {
        CsvFile csv = readCsv(documentId, callerId);
        return new LadderFile(csv.documentId(), csv.fileName(), LadderCsvParser.parse(csv.text()));
    }

    @Override
    public RoleRuleFile fetchRoleRules(String documentId, String callerId) {
        CsvFile csv = readCsv(documentId, callerId);
        return new RoleRuleFile(csv.documentId(), csv.fileName(), RoleRuleCsvParser.parse(csv.text()));
    }

    /** Looks the document up, checks it passed the scan and is a CSV, and downloads it: the part both imports share. */
    private CsvFile readCsv(String documentId, String callerId) {
        DmsDocument document = metadata(documentId, callerId);
        if (document.scanStatus() != null && !OrgStructureAdapterConstants.READABLE_SCAN_STATUSES.contains(document.scanStatus().toUpperCase(Locale.ROOT))) {
            throw OrgStructureException.invalid("The file did not pass the document store's scan (" + document.scanStatus() + ")");
        }
        if (!looksLikeCsv(document)) {
            throw OrgStructureException.invalid("The file must be a CSV (.csv)");
        }
        byte[] bytes = download(document, callerId);
        return new CsvFile(document.id(), document.originalFileName(), new String(bytes, StandardCharsets.UTF_8));
    }

    private DmsDocument metadata(String documentId, String callerId) {
        try {
            DmsEnvelope<DmsDocument> response = caller.callBlocking(() -> dms.get()
                    .uri(OrgStructureAdapterConstants.DOCUMENTS_PATH + OrgStructureAdapterConstants.DOCUMENT_METADATA_PATH, documentId)
                    .headers(h -> identity(h, callerId))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<DmsEnvelope<DmsDocument>>() { }), Idempotency.SAFE);
            if (response == null || !response.success() || response.responseObject() == null) {
                throw OrgStructureException.notFound("The uploaded file was not found: " + documentId);
            }
            return response.responseObject();
        } catch (WebClientResponseException e) {
            throw clientError(e, documentId);
        }
    }

    private byte[] download(DmsDocument document, String callerId) {
        String[] location = storageLocation(document);
        try {
            byte[] bytes = caller.callBlocking(() -> dms.get()
                    .uri(uri -> uri.path(OrgStructureAdapterConstants.DOCUMENTS_PATH).pathSegment(location).build())
                    .headers(h -> identity(h, callerId))
                    .retrieve()
                    .bodyToMono(byte[].class), Idempotency.SAFE);
            if (bytes == null || bytes.length == 0) {
                throw OrgStructureException.invalid("The uploaded file is empty");
            }
            return bytes;
        } catch (WebClientResponseException e) {
            throw clientError(e, document.id());
        }
    }

    /**
     * Where DMS keeps the file, as the path parts to ask for it. DMS says it itself in {@code objectKey}
     * ({@code <organization>/<category>/<type>/<owner>/<file>} for a new upload): that key is used as given, never rebuilt, because
     * rebuilding it from the category, owner and file name only works for documents stored before organizations existed, and
     * asks for a file that is not there for every newer one (a 404). A document without an {@code objectKey} is such an old one.
     */
    private static String[] storageLocation(DmsDocument document) {
        if (document.objectKey() != null && !document.objectKey().isBlank()) {
            String[] parts = document.objectKey().replaceFirst("^/", "").split(OrgStructureAdapterConstants.OBJECT_KEY_SEPARATOR, -1);
            for (String part : parts) {
                if (!OrgStructureAdapterConstants.OBJECT_KEY_PART.matcher(part).matches()) {
                    throw OrgStructureException.invalid("The document store gave a file location that cannot be used");
                }
            }
            return parts;
        }
        if (document.category() == null || document.ownerId() == null || document.storedFileName() == null) {
            throw OrgStructureException.invalid("The document store did not say where the file is kept");
        }
        return new String[] {document.category(), document.ownerId(), document.storedFileName()};
    }

    private static boolean looksLikeCsv(DmsDocument document) {
        String name = document.originalFileName() == null ? "" : document.originalFileName().toLowerCase(Locale.ROOT);
        String type = document.contentType() == null ? "" : document.contentType().toLowerCase(Locale.ROOT).split(";")[0].trim();
        return name.endsWith(OrgStructureAdapterConstants.CSV_EXTENSION) || OrgStructureAdapterConstants.CSV_CONTENT_TYPES.contains(type);
    }

    private static void identity(HttpHeaders headers, String callerId) {
        if (callerId != null && !callerId.isBlank()) {
            headers.set(OrgStructureAdapterConstants.USER_HEADER, callerId);
        }
        headers.set(OrgStructureAdapterConstants.ROLE_HEADER, OrgStructureAdapterConstants.INTERNAL_ROLE);
    }

    /** A 4xx from the document store is an answer about this file, not an outage. */
    private static OrgStructureException clientError(WebClientResponseException e, String documentId) {
        log.warn("DMS rejected the read of document {}: {}", documentId, e.getStatusCode());
        return switch (e.getStatusCode().value()) {
            case 404 -> OrgStructureException.notFound("The uploaded file was not found: " + documentId);
            case 401, 403 -> OrgStructureException.invalid("The document store would not let the import read that file");
            default -> OrgStructureException.invalid("The document store rejected the request (" + e.getStatusCode().value() + ")");
        };
    }
}
