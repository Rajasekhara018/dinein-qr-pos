package com.heuristiq.documents.expiry.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.document.domain.DocumentValidity;
import com.heuristiq.documents.document.domain.DocumentValidityConstants;
import com.heuristiq.documents.document.persistence.DocumentMetadataStore;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.expiry.config.DocumentExpiryProperties;

/**
 * Document expiry (D12): which drivers have a lapsed document (asked by Rider_DispatchService, which blocks them from going online and
 * from new offers), and the reminders before and on lapse (sent through NMS by the daily job). DMS owns the documents and their dates;
 * it never calls Dispatch, so the two services cannot call each other in a cycle.
 */
@Service
public class DocumentExpiryService {

    private static final Logger log = LoggerFactory.getLogger(DocumentExpiryService.class);

    private final DocumentMetadataStore store;
    private final ExpiryNotifier notifier;
    private final DocumentExpiryProperties properties;
    private final Clock clock;

    public DocumentExpiryService(DocumentMetadataStore store, ExpiryNotifier notifier, DocumentExpiryProperties properties, Clock clock) {
        this.store = store;
        this.notifier = notifier;
        this.properties = properties;
        this.clock = clock;
    }

    /** Today in the configured zone: a document valid until yesterday has lapsed. */
    public LocalDate today() {
        return clock.instant().atZone(properties.zone()).toLocalDate();
    }

    /**
     * The lapsed tracked documents of these owners in one organization, by owner (owners with none are left out).
     *
     * @throws DmsException 400 without an organization, with no owners or with more than {@code maxOwners}
     */
    public Map<String, Set<String>> lapsed(String tenantId, Collection<String> ownerIds) {
        if (tenantId == null || tenantId.isBlank()) {
            throw DmsException.badRequest(ExpiryConstants.TENANT_REQUIRED);
        }
        List<String> owners = ownerIds == null ? List.of()
                : ownerIds.stream().filter(Objects::nonNull).map(String::trim).filter(id -> !id.isEmpty()).distinct().toList();
        if (owners.isEmpty() || owners.size() > properties.maxOwners()) {
            throw DmsException.badRequest(ExpiryConstants.OWNERS_REQUIRED + properties.maxOwners());
        }
        LocalDate today = today();
        Map<String, Set<String>> lapsed = new LinkedHashMap<>();
        store.findByTenantOwnersAndCategories(tenantId.trim(), owners, DocumentValidityConstants.TRACKED_CATEGORIES).stream()
                .collect(Collectors.groupingBy(DocumentMetadata::getOwnerId))
                .forEach((owner, documents) -> {
                    Set<String> categories = DocumentValidity.lapsedCategories(documents, today);
                    if (!categories.isEmpty()) {
                        lapsed.put(owner, categories);
                    }
                });
        return lapsed;
    }

    /**
     * Sends the reminders due today: for every owner and tracked category whose counting date is within the reminder window (or lapsed
     * in the last days), the next reminder not yet sent. A reminder NMS did not accept is tried again on the next run.
     *
     * @return how many reminders were sent
     */
    public int sendDueReminders() {
        LocalDate today = today();
        int furthest = properties.reminderDays().stream().mapToInt(Integer::intValue).max().orElse(0);
        LocalDate from = today.minusDays(DocumentValidityConstants.LAPSED_NOTICE_WINDOW_DAYS);
        LocalDate to = today.plusDays(furthest);
        Set<String> seen = new HashSet<>();
        int sent = 0;
        for (int page = 0; ; page++) {
            List<DocumentMetadata> batch = store.findValidUntilBetween(DocumentValidityConstants.TRACKED_CATEGORIES, from, to, page,
                    properties.batchSize());
            for (DocumentMetadata candidate : batch) {
                if (candidate.getOwnerId() == null
                        || !seen.add(candidate.getTenantId() + '|' + candidate.getOwnerId() + '|' + candidate.getCategory())) {
                    continue;
                }
                sent += remind(candidate, today) ? 1 : 0;
            }
            if (batch.size() < properties.batchSize()) {
                break;
            }
        }
        return sent;
    }

    /** Sends the reminder due for this candidate's owner and category, on the document whose date counts. */
    private boolean remind(DocumentMetadata candidate, LocalDate today) {
        List<DocumentMetadata> sameKind = store.findByCategoryAndOwnerIdAndDeletedFalseOrderByCreatedAtDesc(candidate.getCategory(),
                        candidate.getOwnerId()).stream()
                .filter(d -> Objects.equals(d.getTenantId(), candidate.getTenantId())).toList();
        DocumentMetadata counting = DocumentValidity.effectiveDocument(sameKind).orElse(null);
        if (counting == null) {
            return false;
        }
        OptionalInt due = DocumentValidity.dueReminder(counting.getValidUntil(), today, properties.reminderDays(),
                counting.getExpiryNoticeDays());
        if (due.isEmpty()) {
            return false;
        }
        long daysLeft = ChronoUnit.DAYS.between(today, counting.getValidUntil());
        if (!notifier.notify(counting.getTenantId(), counting.getOwnerId(), counting.getId(), counting.getCategory(),
                counting.getValidUntil(), daysLeft, due.getAsInt())) {
            return false;
        }
        counting.setExpiryNoticeDays(due.getAsInt());
        store.save(counting);
        log.info("event=dms_expiry_reminder document={} category={} daysLeft={} reminder={}", counting.getId(), counting.getCategory(),
                daysLeft, due.getAsInt());
        return true;
    }
}
