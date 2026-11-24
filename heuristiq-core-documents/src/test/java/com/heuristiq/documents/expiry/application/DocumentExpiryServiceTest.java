package com.heuristiq.documents.expiry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.document.domain.DocumentValidityConstants;
import com.heuristiq.documents.document.persistence.DocumentMetadataStore;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.expiry.config.DocumentExpiryProperties;

/** D12: the lapse check Dispatch asks for (organization-scoped) and the daily reminders. */
class DocumentExpiryServiceTest {

    // 2026-11-16T20:00Z is already 2026-11-17 in India: the day is counted in the configured zone
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T20:00:00Z"), ZoneId.of("UTC"));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    private DocumentMetadataStore store;
    private ExpiryNotifier notifier;
    private DocumentExpiryService service;

    @BeforeEach
    void setUp() {
        store = mock(DocumentMetadataStore.class);
        notifier = mock(ExpiryNotifier.class);
        DocumentExpiryProperties properties = new DocumentExpiryProperties(true, "0 30 7 * * *", ZoneId.of("Asia/Kolkata"),
                List.of(30, 15, 7, 1), 2, 3, new DocumentExpiryProperties.Nms("http://nms", "", 5000, 3));
        service = new DocumentExpiryService(store, notifier, properties, CLOCK);
    }

    private static DocumentMetadata doc(String id, String tenant, String owner, String category, LocalDate until, boolean confirmed) {
        return DocumentMetadata.builder().id(id).tenantId(tenant).ownerId(owner).category(category).validUntil(until)
                .validUntilConfirmed(confirmed).createdAt(Instant.parse("2026-01-01T00:00:00Z")).build();
    }

    @Test
    void todayIsCountedInTheConfiguredZone() {
        assertThat(service.today()).isEqualTo(TODAY);
    }

    @Test
    void theLapseCheckAnswersOnlyTheOwnersWithALapsedTrackedDocumentOfThatOrganization() {
        when(store.findByTenantOwnersAndCategories(eq("org-a"), eq(List.of("d1", "d2")), eq(DocumentValidityConstants.TRACKED_CATEGORIES)))
                .thenReturn(List.of(doc("1", "org-a", "d1", "driver-insurance", TODAY.minusDays(1), true),
                        doc("2", "org-a", "d1", "driver-puc", TODAY.plusDays(40), true),
                        doc("3", "org-a", "d2", "driver-license", TODAY, true)));

        Map<String, Set<String>> lapsed = service.lapsed(" org-a ", List.of("d1", " d2 ", "d1"));

        assertThat(lapsed).containsOnlyKeys("d1");
        assertThat(lapsed.get("d1")).containsExactly("driver-insurance");
    }

    @Test
    void theLapseCheckNeedsAnOrganizationAndABoundedOwnerList() {
        assertThatThrownBy(() -> service.lapsed(" ", List.of("d1"))).isInstanceOf(DmsException.class);
        assertThatThrownBy(() -> service.lapsed("org-a", List.of())).isInstanceOf(DmsException.class);
        assertThatThrownBy(() -> service.lapsed("org-a", List.of("a", "b", "c", "d"))).isInstanceOf(DmsException.class);
        verify(store, never()).findByTenantOwnersAndCategories(any(), any(), any());
    }

    @Test
    void aDueReminderIsSentOnTheCountingDocumentAndRecordedSoItIsNotRepeated() {
        DocumentMetadata expiring = doc("ins-1", "org-a", "d1", "driver-insurance", TODAY.plusDays(7), true);
        when(store.findValidUntilBetween(any(), eq(TODAY.minusDays(7)), eq(TODAY.plusDays(30)), eq(0), eq(2)))
                .thenReturn(List.of(expiring));
        when(store.findByCategoryAndOwnerIdAndDeletedFalseOrderByCreatedAtDesc("driver-insurance", "d1")).thenReturn(List.of(expiring));
        when(notifier.notify(any(), any(), any(), any(), any(), anyLong(), anyInt())).thenReturn(true);

        assertThat(service.sendDueReminders()).isEqualTo(1);

        verify(notifier).notify("org-a", "d1", "ins-1", "driver-insurance", TODAY.plusDays(7), 7, 7);
        assertThat(expiring.getExpiryNoticeDays()).isEqualTo(7);
        verify(store).save(expiring);
    }

    @Test
    void aRenewedDocumentStopsTheRemindersOfTheOldOne() {
        DocumentMetadata old = doc("ins-1", "org-a", "d1", "driver-insurance", TODAY.plusDays(7), true);
        DocumentMetadata renewed = doc("ins-2", "org-a", "d1", "driver-insurance", TODAY.plusDays(365), true);
        when(store.findValidUntilBetween(any(), any(), any(), eq(0), eq(2))).thenReturn(List.of(old));
        when(store.findByCategoryAndOwnerIdAndDeletedFalseOrderByCreatedAtDesc("driver-insurance", "d1")).thenReturn(List.of(renewed, old));

        assertThat(service.sendDueReminders()).isZero();
        verify(notifier, never()).notify(any(), any(), any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void aReminderNmsDidNotAcceptIsNotRecordedAndIsTriedAgainNextRun() {
        DocumentMetadata expiring = doc("rc-1", "org-a", "d1", "driver-rc", TODAY.plusDays(1), true);
        when(store.findValidUntilBetween(any(), any(), any(), eq(0), eq(2))).thenReturn(List.of(expiring));
        when(store.findByCategoryAndOwnerIdAndDeletedFalseOrderByCreatedAtDesc("driver-rc", "d1")).thenReturn(List.of(expiring));
        when(notifier.notify(any(), any(), any(), any(), any(), anyLong(), anyInt())).thenReturn(false);

        assertThat(service.sendDueReminders()).isZero();
        assertThat(expiring.getExpiryNoticeDays()).isNull();
        verify(store, never()).save(any());
    }

    @Test
    void everyPageIsReadAndEachOwnerAndCategoryIsRemindedOnce() {
        DocumentMetadata a = doc("a", "org-a", "d1", "driver-puc", TODAY.plusDays(15), false);
        DocumentMetadata b = doc("b", "org-a", "d1", "driver-puc", TODAY.plusDays(14), false);
        DocumentMetadata c = doc("c", "org-b", "d2", "driver-permit", TODAY.minusDays(1), true);
        when(store.findValidUntilBetween(any(), any(), any(), eq(0), eq(2))).thenReturn(List.of(a, b));
        when(store.findValidUntilBetween(any(), any(), any(), eq(1), eq(2))).thenReturn(List.of(c));
        when(store.findByCategoryAndOwnerIdAndDeletedFalseOrderByCreatedAtDesc("driver-puc", "d1")).thenReturn(List.of(a, b));
        when(store.findByCategoryAndOwnerIdAndDeletedFalseOrderByCreatedAtDesc("driver-permit", "d2")).thenReturn(List.of(c));
        when(notifier.notify(any(), any(), any(), any(), any(), anyLong(), anyInt())).thenReturn(true);

        assertThat(service.sendDueReminders()).isEqualTo(2);

        verify(notifier).notify("org-a", "d1", "a", "driver-puc", TODAY.plusDays(15), 15, 15);
        verify(notifier).notify("org-b", "d2", "c", "driver-permit", TODAY.minusDays(1), -1, DocumentValidityConstants.LAPSED_NOTICE);
        verify(notifier, never()).notify(anyString(), anyString(), eq("b"), anyString(), any(), anyLong(), anyInt());
    }
}
