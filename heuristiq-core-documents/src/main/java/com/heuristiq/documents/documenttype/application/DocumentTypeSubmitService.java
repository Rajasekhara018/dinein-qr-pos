package com.heuristiq.documents.documenttype.application;

import com.heuristiq.documents.documenttype.domain.DocumentType;
import com.heuristiq.documents.documenttype.domain.DocumentTypeConstants;
import com.heuristiq.documents.documenttype.domain.DocumentTypeDraft;
import com.heuristiq.documents.documenttype.domain.DocumentTypeException;
import com.heuristiq.documents.documenttype.domain.DocumentTypeFields;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRepository;
import com.heuristiq.documents.documenttype.domain.DocumentTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * The maker's side of maker-checker: validates a proposed create or update and submits it to the approval service.
 * Nothing is stored here; the record only changes when a checker approves and Workflow calls back
 * ({@link DocumentTypeApplyService}). If the approval service cannot be reached the call fails with a 503-kind
 * exception and nothing has changed.
 */
@Service
public class DocumentTypeSubmitService {
    private static final Logger log = LoggerFactory.getLogger(DocumentTypeSubmitService.class);

    private final DocumentTypeRepository repository;
    private final ChangeRequestGateway gateway;

    public DocumentTypeSubmitService(DocumentTypeRepository repository, ChangeRequestGateway gateway) {
        this.repository = repository;
        this.gateway = gateway;
    }

    /**
     * Proposes a new document type for the caller's organization.
     *
     * @throws DocumentTypeException INVALID for bad fields, CONFLICT when the code exists, APPROVAL_UNAVAILABLE
     */
    public SubmissionResult submitCreate(CallerContext caller, DocumentTypeFields fields) {
        requireCaller(caller);
        DocumentTypeDraft draft = DocumentTypeValidator.normalize(fields, true);
        if (repository.findByCode(caller.tenantId(), draft.code()).isPresent()) {
            throw DocumentTypeException.conflict("A document type with code " + draft.code() + " already exists");
        }
        Map<String, Object> before = DocumentTypeSnapshots.stateOf(null);
        Map<String, Object> after = DocumentTypeSnapshots.proposedOf(null, caller.tenantId(), draft.code(), draft,
                caller.userId(), DocumentTypeConstants.ACTION_CREATE, null);
        return submit(caller, draft.code(), before, after, "Create");
    }

    /**
     * Proposes a change to an existing document type of the caller's organization. The code is immutable and taken
     * from the stored record.
     *
     * @param expectedVersion the version the maker was looking at; a different stored version is a CONFLICT (stale)
     * @throws DocumentTypeException NOT_FOUND, INVALID, CONFLICT (stale), APPROVAL_UNAVAILABLE
     */
    public SubmissionResult submitUpdate(CallerContext caller, String id, DocumentTypeFields fields, Long expectedVersion) {
        requireCaller(caller);
        DocumentType current = repository.findById(caller.tenantId(), id)
                .orElseThrow(() -> DocumentTypeException.notFound("Document type not found"));
        DocumentTypeDraft draft = DocumentTypeValidator.normalize(fields, false);
        if (expectedVersion != null && !expectedVersion.equals(current.version())) {
            throw DocumentTypeException.conflict("The document type changed after you opened it. Reload and try again");
        }
        Map<String, Object> before = DocumentTypeSnapshots.stateOf(current);
        Map<String, Object> after = DocumentTypeSnapshots.proposedOf(current.id(), caller.tenantId(), current.code(), draft,
                caller.userId(), DocumentTypeConstants.ACTION_UPDATE, current.version());
        return submit(caller, current.code(), before, after, "Update");
    }

    private SubmissionResult submit(CallerContext caller, String code, Map<String, Object> before, Map<String, Object> after,
                                    String verb) {
        var submission = new ChangeRequestGateway.Submission(
                DocumentTypeConstants.SUBJECT_TYPE,
                caller.tenantId() + DocumentTypeConstants.SUBJECT_ID_SEPARATOR + code,
                DocumentTypeConstants.TITLE_PREFIX + code,
                verb + " document type " + code,
                before, after, null);
        ChangeRequestGateway.SubmittedChangeRequest created = gateway.submit(submission, caller.bearerToken());
        log.info("dms.documenttype.submitted code={} changeRequestId={}", code, created.id());
        return new SubmissionResult(created.id(), created.status());
    }

    private static void requireCaller(CallerContext caller) {
        if (caller == null || !StringUtils.hasText(caller.tenantId())) {
            throw DocumentTypeException.invalid("Your session has no organization");
        }
        if (!StringUtils.hasText(caller.userId()) || !StringUtils.hasText(caller.bearerToken())) {
            throw DocumentTypeException.invalid("Sign in again to submit a change for approval");
        }
    }
}
