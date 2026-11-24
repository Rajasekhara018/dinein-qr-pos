package com.railcab.nms.adapters.out.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.railcab.nms.domain.model.Broadcast;
import com.railcab.nms.domain.model.InAppNotification;
import com.railcab.nms.domain.model.NotificationAttempt;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.domain.model.NotificationLog;
import com.railcab.nms.domain.model.NotificationMessage;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.TenantScope;

/** Mongo adapters without a database: the organization is mapped both ways and picks the scoped query. */
class TenantMongoAdaptersTest {

    private static final PageQuery UNRESTRICTED = new PageQuery(0, 20);
    private static final PageQuery ORG_A = new PageQuery(0, 20, TenantScope.limitedTo("org-a"));
    private static final PageQuery NO_ORG = new PageQuery(0, 20, TenantScope.limitedTo(null));

    private static <T> Page<T> empty() {
        return new PageImpl<>(List.of());
    }

    @Test
    void aLogKeepsItsOrganizationThroughTheDocumentAndBack() {
        NotificationLogMongoSpringRepository spring = mock(NotificationLogMongoSpringRepository.class);
        when(spring.save(any())).thenAnswer(i -> i.getArgument(0));
        NotificationLogMongoRepositoryAdapter adapter = new NotificationLogMongoRepositoryAdapter(spring);

        NotificationLog back = adapter.save(NotificationLog.builder().id("l1").tenantId("org-a").messageId("m1").build());

        ArgumentCaptor<NotificationLogDocument> doc = ArgumentCaptor.forClass(NotificationLogDocument.class);
        verify(spring).save(doc.capture());
        assertThat(doc.getValue().getTenantId()).isEqualTo("org-a");
        assertThat(back.getTenantId()).isEqualTo("org-a");
    }

    @Test
    void aLegacyLogWithoutAnOrganizationStaysWithoutOne() {
        NotificationLogMongoSpringRepository spring = mock(NotificationLogMongoSpringRepository.class);
        when(spring.save(any())).thenAnswer(i -> i.getArgument(0));

        NotificationLog back = new NotificationLogMongoRepositoryAdapter(spring)
                .save(NotificationLog.builder().id("l1").messageId("m1").build());

        assertThat(back.getTenantId()).isNull();
    }

    @Test
    void anAttemptMessageBroadcastAndInboxEntryKeepTheirOrganization() {
        NotificationAttemptMongoSpringRepository attempts = mock(NotificationAttemptMongoSpringRepository.class);
        when(attempts.save(any())).thenAnswer(i -> i.getArgument(0));
        NotificationMessageMongoSpringRepository messages = mock(NotificationMessageMongoSpringRepository.class);
        when(messages.save(any())).thenAnswer(i -> i.getArgument(0));
        BroadcastMongoSpringRepository broadcasts = mock(BroadcastMongoSpringRepository.class);
        when(broadcasts.save(any())).thenAnswer(i -> i.getArgument(0));
        InAppNotificationMongoSpringRepository inbox = mock(InAppNotificationMongoSpringRepository.class);
        when(inbox.save(any())).thenAnswer(i -> i.getArgument(0));

        NotificationAttempt attempt = new NotificationAttemptMongoRepositoryAdapter(attempts)
                .save(NotificationAttempt.builder().id("a1").tenantId("org-a").build());
        NotificationMessage message = new NotificationMessageMongoRepositoryAdapter(messages).save(messageOf("org-a"));
        Broadcast broadcast = new BroadcastMongoRepositoryAdapter(broadcasts)
                .save(Broadcast.builder().id("b1").tenantId("org-a").build());
        InAppNotification notification = new InAppNotificationMongoRepositoryAdapter(inbox).save(notificationOf("org-a"));

        assertThat(attempt.getTenantId()).isEqualTo("org-a");
        assertThat(message.getTenantId()).isEqualTo("org-a");
        assertThat(broadcast.getTenantId()).isEqualTo("org-a");
        assertThat(notification.getTenantId()).isEqualTo("org-a");
    }

    private static NotificationMessage messageOf(String tenantId) {
        NotificationMessage message = new NotificationMessage();
        message.setId("m1");
        message.setTenantId(tenantId);
        message.setMessageId("msg-1");
        return message;
    }

    private static InAppNotification notificationOf(String tenantId) {
        InAppNotification notification = new InAppNotification();
        notification.setId("n1");
        notification.setTenantId(tenantId);
        notification.setUserId("u1");
        return notification;
    }

    @Test
    void aLogListingForAnOrganizationUsesTheScopedQueryWithItsId() {
        NotificationLogMongoSpringRepository spring = mock(NotificationLogMongoSpringRepository.class);
        when(spring.findByEventScoped(anyString(), anyString(), any(Pageable.class))).thenReturn(empty());
        NotificationLogMongoRepositoryAdapter adapter = new NotificationLogMongoRepositoryAdapter(spring);

        adapter.findByEvent("OTP", ORG_A);

        verify(spring).findByEventScoped(eq("OTP"), eq("org-a"), any(Pageable.class));
        verify(spring, never()).findByEvent(anyString(), any(Pageable.class));
    }

    @Test
    void aLogListingForAUserWithoutAnOrganizationUsesTheScopedQueryWithAnEmptyId() {
        NotificationLogMongoSpringRepository spring = mock(NotificationLogMongoSpringRepository.class);
        when(spring.findAllScoped(anyString(), any(Pageable.class))).thenReturn(empty());

        new NotificationLogMongoRepositoryAdapter(spring).findAll(NO_ORG);

        verify(spring).findAllScoped(eq(""), any(Pageable.class));
    }

    @Test
    void anUnrestrictedLogListingDoesNotFilter() {
        NotificationLogMongoSpringRepository spring = mock(NotificationLogMongoSpringRepository.class);
        when(spring.findByChannel(any(), any(Pageable.class))).thenReturn(empty());

        new NotificationLogMongoRepositoryAdapter(spring).findByChannel(NotificationChannel.SMS, UNRESTRICTED);

        verify(spring).findByChannel(eq(NotificationChannel.SMS), any(Pageable.class));
        verify(spring, never()).findByChannelScoped(any(), anyString(), any(Pageable.class));
    }

    @Test
    void theDeliveryCountsForAnOrganizationUseTheScopedCount() {
        NotificationLogMongoSpringRepository spring = mock(NotificationLogMongoSpringRepository.class);
        when(spring.countByChannelAndSuccessScoped(NotificationChannel.SMS, true, "org-a")).thenReturn(4L);
        NotificationLogMongoRepositoryAdapter adapter = new NotificationLogMongoRepositoryAdapter(spring);

        assertThat(adapter.countByChannelAndSuccess(NotificationChannel.SMS, true, TenantScope.limitedTo("org-a"))).isEqualTo(4L);
        adapter.countByChannelAndSuccess(NotificationChannel.SMS, false, TenantScope.UNRESTRICTED);
        verify(spring).countByChannelAndSuccessFalse(NotificationChannel.SMS);
    }

    @Test
    void theScopedLogDateRangeKeepsTheBounds() {
        NotificationLogMongoSpringRepository spring = mock(NotificationLogMongoSpringRepository.class);
        LocalDateTime from = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 2, 0, 0);
        when(spring.findBySentAtBetweenScoped(any(), any(), anyString(), any(Pageable.class))).thenReturn(empty());

        new NotificationLogMongoRepositoryAdapter(spring).findBySentAtBetween(from, to, ORG_A);

        verify(spring).findBySentAtBetweenScoped(eq(from), eq(to), eq("org-a"), any(Pageable.class));
    }

    @Test
    void anotherOrganizationsInboxEntryIsNotMarkedAsRead() {
        InAppNotificationMongoSpringRepository spring = mock(InAppNotificationMongoSpringRepository.class);
        InAppNotificationDocument other = new InAppNotificationDocument();
        other.setId("n1");
        other.setUserId("u1");
        other.setTenantId("org-b");
        when(spring.findById("n1")).thenReturn(Optional.of(other));
        InAppNotificationMongoRepositoryAdapter adapter = new InAppNotificationMongoRepositoryAdapter(spring);

        adapter.markAsRead("n1", "u1", TenantScope.limitedTo("org-a"));

        verify(spring, never()).save(any());
        assertThat(other.isRead()).isFalse();

        adapter.markAsRead("n1", "u1", TenantScope.limitedTo("org-b"));

        assertThat(other.isRead()).isTrue();
    }

    @Test
    void aDeviceTokenIsStoredWithTheOrganizationOfItsRegistration() {
        DeviceTokenMongoSpringRepository spring = mock(DeviceTokenMongoSpringRepository.class);
        when(spring.findFirstByUserIdAndToken("u1", "tok")).thenReturn(Optional.empty());
        DeviceTokenMongoRepositoryAdapter adapter = new DeviceTokenMongoRepositoryAdapter(spring);

        adapter.register("u1", "tok", new com.railcab.nms.domain.model.DeviceRegistration(
                "android", null, null, null, null, null, null, null, "org-a"));

        ArgumentCaptor<DeviceTokenDocument> saved = ArgumentCaptor.forClass(DeviceTokenDocument.class);
        verify(spring).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo("org-a");
    }

    @Test
    void pushTokenLookupForAnOrganizationUsesTheScopedQuery() {
        DeviceTokenMongoSpringRepository spring = mock(DeviceTokenMongoSpringRepository.class);
        DeviceTokenDocument doc = new DeviceTokenDocument();
        doc.setToken("tok");
        when(spring.findByUserIdInScoped(any(), eq("org-a"))).thenReturn(List.of(doc));

        List<String> tokens = new DeviceTokenMongoRepositoryAdapter(spring)
                .findTokensByUserIds(List.of("u1"), TenantScope.limitedTo("org-a"));

        assertThat(tokens).containsExactly("tok");
        verify(spring, never()).findByUserIdIn(any());
    }
}
