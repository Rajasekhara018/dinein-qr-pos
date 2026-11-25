package com.heuristiq.documents.document.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

/** D12: which date counts, when a document has lapsed and which reminder is due. */
class DocumentValidityTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);
    private static final List<Integer> REMINDERS = List.of(30, 15, 7, 1);

    private static DocumentMetadata doc(String category, String until, boolean confirmed, String created) {
        return DocumentMetadata.builder().id(category + until).ownerId("d1").category(category)
                .validUntil(until == null ? null : LocalDate.parse(until)).validUntilConfirmed(confirmed)
                .createdAt(Instant.parse(created)).build();
    }

    @Test
    void aConfirmedDateWinsOverALaterDateTheCaptainEntered() {
        DocumentMetadata confirmed = doc("driver-insurance", "2026-11-10", true, "2025-11-10T00:00:00Z");
        DocumentMetadata claimed = doc("driver-insurance", "2027-11-10", false, "2026-11-12T00:00:00Z");

        assertThat(DocumentValidity.effectiveDocument(List.of(confirmed, claimed))).contains(confirmed);
        assertThat(DocumentValidity.lapsedCategories(List.of(confirmed, claimed), TODAY)).containsExactly("driver-insurance");
    }

    @Test
    void withoutAConfirmedDateTheCaptainsLatestDateCounts() {
        DocumentMetadata older = doc("driver-puc", "2026-12-01", false, "2026-06-01T00:00:00Z");
        DocumentMetadata newer = doc("driver-puc", "2027-05-01", false, "2026-11-01T00:00:00Z");

        assertThat(DocumentValidity.effectiveValidUntil(List.of(older, newer))).contains(LocalDate.of(2027, 5, 1));
    }

    @Test
    void aConfirmedRenewalLiftsTheLapse() {
        List<DocumentMetadata> docs = List.of(doc("driver-insurance", "2026-11-10", true, "2025-11-10T00:00:00Z"),
                doc("driver-insurance", "2027-11-10", true, "2026-11-16T00:00:00Z"));

        assertThat(DocumentValidity.lapsedCategories(docs, TODAY)).isEmpty();
    }

    @Test
    void aDocumentValidUntilTodayHasNotLapsedYetAndUntrackedOrUndatedOnesNeverLapse() {
        List<DocumentMetadata> docs = List.of(doc("driver-license", "2026-11-17", true, "2020-01-01T00:00:00Z"),
                doc("driver-aadhaar", "2020-01-01", true, "2020-01-01T00:00:00Z"),
                doc("driver-rc", null, false, "2020-01-01T00:00:00Z"));

        assertThat(DocumentValidity.lapsedCategories(docs, TODAY)).isEmpty();
        assertThat(DocumentValidity.lapsedCategories(docs, TODAY.plusDays(1))).containsExactly("driver-license");
    }

    @Test
    void deletedDocumentsAreIgnored() {
        DocumentMetadata deleted = doc("driver-rc", "2030-01-01", true, "2026-01-01T00:00:00Z");
        deleted.setDeleted(true);

        assertThat(DocumentValidity.effectiveDocument(List.of(deleted))).isEmpty();
    }

    @Test
    void theReminderDueIsTheSmallestNotBelowTheDaysLeft() {
        assertThat(DocumentValidity.dueReminder(TODAY.plusDays(30), TODAY, REMINDERS, null)).isEqualTo(OptionalInt.of(30));
        assertThat(DocumentValidity.dueReminder(TODAY.plusDays(20), TODAY, REMINDERS, null)).isEqualTo(OptionalInt.of(30));
        assertThat(DocumentValidity.dueReminder(TODAY.plusDays(14), TODAY, REMINDERS, 30)).isEqualTo(OptionalInt.of(15));
        assertThat(DocumentValidity.dueReminder(TODAY, TODAY, REMINDERS, 7)).isEqualTo(OptionalInt.of(1));
        assertThat(DocumentValidity.dueReminder(TODAY.plusDays(31), TODAY, REMINDERS, null)).isEmpty();
    }

    @Test
    void aReminderIsNeverSentTwiceAndAMissedDayCatchesUpWithoutRepeating() {
        assertThat(DocumentValidity.dueReminder(TODAY.plusDays(14), TODAY, REMINDERS, 15)).isEmpty();
        // the job missed the 15-day reminder: at 6 days left the 7-day one goes out, once
        assertThat(DocumentValidity.dueReminder(TODAY.plusDays(6), TODAY, REMINDERS, 30)).isEqualTo(OptionalInt.of(7));
        assertThat(DocumentValidity.dueReminder(TODAY.plusDays(6), TODAY, REMINDERS, 7)).isEmpty();
    }

    @Test
    void theLapsedNoticeGoesOutOnceInTheFirstDaysAfterLapsing() {
        assertThat(DocumentValidity.dueReminder(TODAY.minusDays(1), TODAY, REMINDERS, 1))
                .isEqualTo(OptionalInt.of(DocumentValidityConstants.LAPSED_NOTICE));
        assertThat(DocumentValidity.dueReminder(TODAY.minusDays(2), TODAY, REMINDERS, DocumentValidityConstants.LAPSED_NOTICE)).isEmpty();
        assertThat(DocumentValidity.dueReminder(TODAY.minusDays(DocumentValidityConstants.LAPSED_NOTICE_WINDOW_DAYS + 1), TODAY, REMINDERS,
                null)).isEmpty();
    }
}
