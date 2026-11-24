package com.railcab.dms.documenttype.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railcab.dms.documenttype.domain.DocumentType;
import com.railcab.dms.documenttype.domain.DocumentTypeConstants;
import com.railcab.dms.documenttype.domain.DocumentTypeDraft;
import com.railcab.dms.documenttype.domain.DocumentTypeException;
import com.railcab.dms.documenttype.domain.DocumentTypeRepository;
import com.railcab.dms.documenttype.domain.DocumentTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The owner side of the maker-checker contract: Rider_WorkflowService calls this after a checker approved a document
 * type change. It verifies the call is about a document type, rebuilds the target from the approved {@code after}
 * snapshot, refuses a maker approving their own change (403), recomputes {@code beforeHash} from the record as it is
 * now (409 when it changed or the code was taken), and applies the create or update with optimistic locking. A
 * repeated change request id answers success without changing anything.
 *
 * <p>The organization is the one inside the approved snapshot (written by this service on submit from the maker's
 * token), never anything else; every lookup and write is limited to it.
 */
@Service
public class DocumentTypeApplyService {
    private static final Logger log = LoggerFactory.getLogger(DocumentTypeApplyService.class);

    private final DocumentTypeRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final DocumentTypeRuleInvalidator ruleInvalidator;

    /** Without a rule cache in front of the lookup (nothing to invalidate). */
    public DocumentTypeApplyService(DocumentTypeRepository repository, ObjectMapper objectMapper, Clock clock) {
        this(repository, objectMapper, clock, DocumentTypeRuleInvalidator.NONE);
    }

    @Autowired
    public DocumentTypeApplyService(DocumentTypeRepository repository, ObjectMapper objectMapper, Clock clock,
                                    DocumentTypeRuleInvalidator ruleInvalidator) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.ruleInvalidator = ruleInvalidator;
    }

    /**
     * Applies an approved change.
     *
     * @param body the raw JSON payload Workflow sent
     * @return the outcome (APPLIED also for a repeat of an already applied request)
     * @throws DocumentTypeException INVALID malformed, FORBIDDEN maker equals checker, CONFLICT stale
     */
    public ApplyOutcome apply(String body) {
        JsonNode root = parse(body);
        String changeRequestId = DocumentTypeSnapshots.text(root, DocumentTypeConstants.K_CHANGE_REQUEST_ID);
        String beforeHash = DocumentTypeSnapshots.text(root, DocumentTypeConstants.K_BEFORE_HASH);
        String approvedBy = DocumentTypeSnapshots.text(root, DocumentTypeConstants.K_APPROVED_BY);
        if (changeRequestId == null || beforeHash == null) {
            throw DocumentTypeException.invalid("changeRequestId and beforeHash are required");
        }
        if (!DocumentTypeConstants.SUBJECT_TYPE.equals(DocumentTypeSnapshots.text(root, DocumentTypeConstants.K_SUBJECT_TYPE))) {
            throw DocumentTypeException.invalid("Unsupported subjectType");
        }
        JsonNode after = root.get(DocumentTypeConstants.K_AFTER);
        if (after == null || !after.isObject()) {
            throw DocumentTypeException.invalid("after is required");
        }
        String tenantId = DocumentTypeSnapshots.text(after, DocumentTypeConstants.K_TENANT_ID);
        String code = DocumentTypeSnapshots.text(after, DocumentTypeConstants.K_CODE);
        String maker = DocumentTypeSnapshots.text(after, DocumentTypeConstants.K_MAKER);
        String action = DocumentTypeSnapshots.text(after, DocumentTypeConstants.K_ACTION);
        if (tenantId == null || code == null || maker == null || action == null) {
            throw DocumentTypeException.invalid("after must carry tenantId, code, maker and action");
        }
        code = code.trim().toUpperCase(Locale.ROOT);
        String subjectId = DocumentTypeSnapshots.text(root, DocumentTypeConstants.K_SUBJECT_ID);
        if (subjectId != null && !subjectId.equals(tenantId + DocumentTypeConstants.SUBJECT_ID_SEPARATOR + code)) {
            throw DocumentTypeException.invalid("subjectId does not match the approved snapshot");
        }
        if (maker.equals(approvedBy)) {
            throw DocumentTypeException.forbidden("The maker cannot approve their own change");
        }
        DocumentTypeDraft draft = DocumentTypeValidator.normalize(DocumentTypeSnapshots.fieldsOf(after), true);
        if (!draft.code().equals(code)) {
            throw DocumentTypeException.invalid("code does not match the approved snapshot");
        }

        DocumentType current = repository.findByCode(tenantId, code).orElse(null);
        if (current != null && current.recentChangeRequestIds().contains(changeRequestId)) {
            return new ApplyOutcome(DocumentTypeConstants.OUTCOME_APPLIED, current.id(), current.version(), changeRequestId);
        }
        if (!DocumentTypeSnapshots.hashOf(objectMapper, current).equals(beforeHash)) {
            throw DocumentTypeException.conflict("The document type changed after the request was made");
        }

        Instant now = clock.instant();
        DocumentType toSave;
        if (DocumentTypeConstants.ACTION_CREATE.equals(action)) {
            if (current != null) {
                throw DocumentTypeException.conflict("A document type with code " + code + " already exists");
            }
            toSave = build(UUID.randomUUID().toString(), tenantId, draft, null, now, now, maker, maker, changeRequestId,
                    List.of());
        } else if (DocumentTypeConstants.ACTION_UPDATE.equals(action)) {
            if (current == null) {
                throw DocumentTypeException.conflict("The document type no longer exists");
            }
            String declaredId = DocumentTypeSnapshots.text(after, DocumentTypeConstants.K_ID);
            if (declaredId != null && !declaredId.equals(current.id())) {
                throw DocumentTypeException.invalid("id does not match the approved snapshot");
            }
            toSave = build(current.id(), tenantId, draft, current.version(), current.createdAt(), now, current.createdBy(),
                    maker, changeRequestId, current.recentChangeRequestIds());
        } else {
            throw DocumentTypeException.invalid("Unknown action");
        }

        DocumentType saved = repository.save(toSave);
        // Every change to the rule (create, update, deactivate, formats, size, retention, scan) goes through this
        // save; drop the cached rule now that it is committed. Other instances converge within the cache TTL.
        ruleInvalidator.evict(tenantId, code);
        log.info("dms.documenttype.applied code={} action={} changeRequestId={}", code, action, changeRequestId);
        return new ApplyOutcome(DocumentTypeConstants.OUTCOME_APPLIED, saved.id(), saved.version(), changeRequestId);
    }

    private static DocumentType build(String id, String tenantId, DocumentTypeDraft draft, Long version, Instant createdAt,
                                      Instant updatedAt, String createdBy, String updatedBy, String changeRequestId,
                                      List<String> previousIds) {
        List<String> remembered = new ArrayList<>(previousIds);
        remembered.add(changeRequestId);
        while (remembered.size() > DocumentTypeConstants.MAX_REMEMBERED_CHANGE_REQUESTS) {
            remembered.remove(0);
        }
        return new DocumentType(id, tenantId, draft.code(), draft.category(), draft.flow(), draft.names(),
                draft.allowedFormats(), draft.maxFileSizeMb(), draft.required(), draft.expiryYears(), draft.retentionDays(),
                draft.scanRequired(), draft.verificationRequired(), draft.verificationMethod(), draft.active(),
                version, createdAt, updatedAt, createdBy, updatedBy, changeRequestId, List.copyOf(remembered), draft.verificationProvider());
    }

    private JsonNode parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                throw DocumentTypeException.invalid("The payload must be a JSON object");
            }
            return root;
        } catch (JsonProcessingException e) {
            throw DocumentTypeException.invalid("The payload is not valid JSON");
        }
    }

    /**
     * Result of an apply.
     *
     * @param status          always APPLIED (a repeat of an applied request also answers APPLIED)
     * @param id              the document type id
     * @param version         its lock version after the change
     * @param changeRequestId the request that was applied
     */
    public record ApplyOutcome(String status, String id, Long version, String changeRequestId) {
    }
}
