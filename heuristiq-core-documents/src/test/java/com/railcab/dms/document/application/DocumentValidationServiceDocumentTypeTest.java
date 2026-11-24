package com.railcab.dms.document.application;

import com.railcab.dms.document.audit.DocumentReasonCodes;
import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.documenttype.application.BlockedFileTypes;
import com.railcab.dms.documenttype.application.DocumentTypeApplyService;
import com.railcab.dms.documenttype.application.DocumentTypeFileCheckService;
import com.railcab.dms.documenttype.application.DocumentTypeQueryService;
import com.railcab.dms.documenttype.application.DocumentTypeSubmitService;
import com.railcab.dms.documenttype.domain.DocumentTypeConstants;
import com.railcab.dms.documenttype.domain.DocumentTypeException;
import com.railcab.dms.documenttype.support.FakeChangeRequestGateway;
import com.railcab.dms.documenttype.support.InMemoryDocumentTypeRepository;
import com.railcab.dms.exception.DmsException;
import com.railcab.dms.security.DmsPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.MAPPER;
import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.ORG_A;
import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.ORG_B;
import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.applyPayload;
import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.caller;
import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.fields;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;

/** Upload validation with and without a document type, and the "check a file" use case built on it. */
class DocumentValidationServiceDocumentTypeTest {

    private static final String CATEGORY = "matrimony-documents";

    private final DmsProperties properties = new DmsProperties();
    private InMemoryDocumentTypeRepository repository;
    private FakeChangeRequestGateway gateway;
    private DocumentTypeQueryService query;
    private DocumentValidationService validation;
    private DocumentTypeSubmitService submit;
    private DocumentTypeApplyService apply;
    private DocumentScanService scan;

    @BeforeEach
    void setUp() {
        repository = new InMemoryDocumentTypeRepository();
        gateway = new FakeChangeRequestGateway();
        query = new DocumentTypeQueryService(repository, new BlockedFileTypes() {
            @Override
            public Collection<String> extensions() {
                return properties.getScan().getBlockedExtensions();
            }

            @Override
            public Collection<String> contentTypes() {
                return properties.getScan().getBlockedContentTypes();
            }
        });
        validation = new DocumentValidationService(properties, query);
        submit = new DocumentTypeSubmitService(repository, gateway);
        apply = new DocumentTypeApplyService(repository, MAPPER, Clock.systemUTC());
        scan = mock(DocumentScanService.class);
        approve(ORG_A, "PHOTO_ID", List.of("PDF"), 1, true);
        as(ORG_A);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void as(String tenant) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new DmsPrincipal("user-1", "ADMIN", tenant), null, List.of()));
    }

    private void approve(String tenant, String code, List<String> formats, int mb, boolean active) {
        submit.submitCreate(caller(tenant, "maker"), new com.railcab.dms.documenttype.domain.DocumentTypeFields(code, CATEGORY,
                null, Map.of("en", code), formats, mb, true, null, active));
        apply.apply(applyPayload(gateway.submissions.get(gateway.submissions.size() - 1), "cr-" + tenant + code, "checker",
                DocumentTypeConstants.SUBJECT_TYPE));
    }

    private static MockMultipartFile file(String name, String type, int size) {
        return new MockMultipartFile("file", name, type, new byte[size]);
    }

    @Test
    void withoutDocumentTypeBehaviourIsTodaysCategoryRule() {
        // The category rule allows .docx and 10 MB; the type PHOTO_ID (PDF only, 1 MB) must not influence this.
        assertDoesNotThrow(() -> validation.validate(file("a.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 2 * 1024 * 1024), CATEGORY, null));
        assertDoesNotThrow(() -> validation.validate(file("a.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 10), CATEGORY, null, ""));
        assertThrows(DmsException.class, () -> validation.validate(file("a.exe", "application/pdf", 10), CATEGORY, null));
    }

    @Test
    void documentTypeRuleReplacesTheCategoryRule() {
        assertDoesNotThrow(() -> validation.validate(file("id.pdf", "application/pdf", 100), CATEGORY, null, "PHOTO_ID"));
        assertThrows(DmsException.class,
                () -> validation.validate(file("id.png", "image/png", 100), CATEGORY, null, "PHOTO_ID"));
        DmsException tooBig = assertThrows(DmsException.class,
                () -> validation.validate(file("id.pdf", "application/pdf", 2 * 1024 * 1024), CATEGORY, null, "photo_id"));
        assertEquals("Document exceeds the configured maximum file size", tooBig.getMessage());
    }

    @Test
    void unknownInactiveWrongCategoryOrOtherOrganizationsTypeIsBadRequest() {
        approve(ORG_A, "OLD_TYPE", List.of("PDF"), 1, false);
        MockMultipartFile ok = file("id.pdf", "application/pdf", 10);
        for (Runnable call : List.<Runnable>of(
                () -> validation.validate(ok, CATEGORY, null, "NOPE"),
                () -> validation.validate(ok, CATEGORY, null, "OLD_TYPE"),
                () -> validation.validate(ok, "invoices", null, "PHOTO_ID"),
                () -> {
                    as(ORG_B);
                    validation.validate(ok, CATEGORY, null, "PHOTO_ID");
                })) {
            as(ORG_A);
            DmsException ex = assertThrows(DmsException.class, call::run);
            assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus());
        }
    }

    @Test
    void documentTypeWithoutAnyAvailableRulesIsBadRequest() {
        DocumentValidationService bare = new DocumentValidationService(properties);
        assertThrows(DmsException.class, () -> bare.validate(file("a.pdf", "application/pdf", 1), CATEGORY, null, "PHOTO_ID"));
    }

    @Test
    void checkFileReportsEveryReasonAndAcceptsAGoodFile() {
        DocumentTypeFileCheckService check = new DocumentTypeFileCheckService(query, validation, scan);

        var good = check.check(ORG_A, null, "PHOTO_ID", file("id.pdf", "application/pdf", 100));
        assertTrue(good.accepted());
        assertTrue(good.reasons().isEmpty());
        assertEquals(1, good.rule().maxFileSizeMb());

        var bad = check.check(ORG_A, CATEGORY, "PHOTO_ID", file("id.png", "image/png", 2 * 1024 * 1024));
        assertFalse(bad.accepted());
        assertEquals(List.of(DocumentReasonCodes.FILE_TOO_LARGE, DocumentReasonCodes.CONTENT_TYPE_NOT_ALLOWED,
                DocumentReasonCodes.EXTENSION_NOT_ALLOWED), bad.reasons().stream().map(r -> r.code()).toList());

        var empty = check.check(ORG_A, null, "PHOTO_ID", file("id.pdf", "application/pdf", 0));
        assertEquals(DocumentTypeConstants.REASON_EMPTY_FILE, empty.reasons().get(0).code());
    }

    @Test
    void checkFileAddsBlockedContentReasonAndStoresNothing() {
        MockMultipartFile disguised = file("id.pdf", "application/pdf", 10);
        doThrow(DmsException.badRequest("Document content does not match a safe file type"))
                .when(scan).rejectStaticViolations(disguised);
        var result = new DocumentTypeFileCheckService(query, validation, scan).check(ORG_A, null, "PHOTO_ID", disguised);
        assertFalse(result.accepted());
        assertEquals(DocumentTypeConstants.REASON_BLOCKED, result.reasons().get(0).code());
        assertEquals("Document content does not match a safe file type", result.reasons().get(0).message());
    }

    @Test
    void checkFileIsLimitedToTheCallersOrganization() {
        DocumentTypeFileCheckService check = new DocumentTypeFileCheckService(query, validation, scan);
        DocumentTypeException ex = assertThrows(DocumentTypeException.class,
                () -> check.check(ORG_B, null, "PHOTO_ID", file("id.pdf", "application/pdf", 10)));
        assertEquals(DocumentTypeException.Kind.INVALID, ex.kind());
        assertThrows(DocumentTypeException.class,
                () -> check.check(ORG_A, "invoices", "PHOTO_ID", file("id.pdf", "application/pdf", 10)));
    }
}
