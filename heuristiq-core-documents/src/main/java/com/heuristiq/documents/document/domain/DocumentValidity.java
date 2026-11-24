package com.heuristiq.documents.document.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Pure rules of document expiry (D12). For one owner and one tracked category, the date that counts is the latest
 * reviewer-confirmed {@code validUntil}; only when no date was confirmed yet does the latest date the captain entered count. So a
 * renewal the captain uploads lifts a block only once a reviewer confirms it, while a captain's own date still drives reminders until
 * then. A category is lapsed from the day after that date.
 */
public final class DocumentValidity {

    private DocumentValidity() {
    }

    /**
     * The document whose date counts (one owner, one category): the one with the latest confirmed date, else the one with the latest
     * date at all; the newer upload wins a tie. Empty when none carries a date.
     */
    public static Optional<DocumentMetadata> effectiveDocument(Collection<DocumentMetadata> documents) {
        Comparator<DocumentMetadata> latest = Comparator.comparing(DocumentMetadata::getValidUntil)
                .thenComparing(DocumentMetadata::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()));
        List<DocumentMetadata> dated = documents.stream()
                .filter(d -> !d.isDeleted() && d.getValidUntil() != null).toList();
        Optional<DocumentMetadata> confirmed = dated.stream().filter(DocumentMetadata::isValidUntilConfirmed).max(latest);
        return confirmed.isPresent() ? confirmed : dated.stream().max(latest);
    }

    /** The date that counts for these documents (one owner, one category); empty when none carries a date. */
    public static Optional<LocalDate> effectiveValidUntil(Collection<DocumentMetadata> documents) {
        return effectiveDocument(documents).map(DocumentMetadata::getValidUntil);
    }

    /** The tracked categories of one owner's documents that have lapsed on {@code today} (sorted). */
    public static Set<String> lapsedCategories(Collection<DocumentMetadata> ownerDocuments, LocalDate today) {
        return ownerDocuments.stream()
                .filter(d -> DocumentValidityConstants.TRACKED_CATEGORIES.contains(d.getCategory()))
                .collect(Collectors.groupingBy(DocumentMetadata::getCategory))
                .entrySet().stream()
                .filter(e -> effectiveValidUntil(e.getValue()).map(until -> until.isBefore(today)).orElse(false))
                .map(java.util.Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * The reminder due on {@code today} for a document valid until {@code validUntil}: the smallest of {@code reminderDays} that is at
     * least the days left (so a day the job missed is caught up with the next smaller reminder, never twice), or
     * {@link DocumentValidityConstants#LAPSED_NOTICE} in the first days after it lapsed. Empty when nothing is due: too far away,
     * long lapsed, or that reminder (or a later one) was already sent.
     *
     * @param alreadySent the smallest reminder already sent for this date, or null
     */
    public static OptionalInt dueReminder(LocalDate validUntil, LocalDate today, List<Integer> reminderDays, Integer alreadySent) {
        long daysLeft = ChronoUnit.DAYS.between(today, validUntil);
        int due;
        if (daysLeft < 0) {
            if (-daysLeft > DocumentValidityConstants.LAPSED_NOTICE_WINDOW_DAYS) {
                return OptionalInt.empty();
            }
            due = DocumentValidityConstants.LAPSED_NOTICE;
        } else {
            OptionalInt smallest = reminderDays.stream().filter(Objects::nonNull).mapToInt(Integer::intValue)
                    .filter(days -> days >= daysLeft).min();
            if (smallest.isEmpty()) {
                return OptionalInt.empty();
            }
            due = smallest.getAsInt();
        }
        return alreadySent != null && alreadySent <= due ? OptionalInt.empty() : OptionalInt.of(due);
    }
}
