package com.heuristiq.notification.adapters.out.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.heuristiq.notification.domain.model.Broadcast;
import com.heuristiq.notification.domain.model.InAppNotification;
import com.heuristiq.notification.domain.model.NotificationAttempt;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.model.NotificationLog;
import com.heuristiq.notification.domain.model.NotificationMessage;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.domain.repository.TenantScope;

/** JPA adapters without a database: the organization is mapped both ways and picks the scoped query. */
class TenantJpaAdaptersTest {

    private static final PageQuery ORG_A = new PageQuery(0, 20, TenantScope.limitedTo("org-a"));
    private static final PageQuery NO_ORG = new PageQuery(0, 20, TenantScope.limitedTo(null));

    private static <T> Page<T> empty() {
        return new PageImpl<>(List.of());
    }

    @Test
    void aLogKeepsItsOrganizationThroughTheEntityAndBack() {
        NotificationLogJpaSpringRepository spring = mock(NotificationLogJpaSpringRepository.class);
        when(spring.save(any())).thenAnswer(i -> i.getArgument(0));

        NotificationLog back = new NotificationLogJpaRepositoryAdapter(spring)
                .save(NotificationLog.builder().id("l1").tenantId("org-a").messageId("m1").build());

        ArgumentCaptor<NotificationLogEntity> entity = ArgumentCaptor.forClass(NotificationLogEntity.class);
        verify(spring).save(entity.capture());
        assertThat(entity.getValue().getTenantId()).isEqualTo("org-a");
        assertThat(back.getTenantId()).isEqualTo("org-a");
    }

    @Test
    void anAttemptMessageBroadcastAndInboxEntryKeepTheirOrganization() {
        NotificationAttemptJpaSpringRepository attempts = mock(NotificationAttemptJpaSpringRepository.class);
        when(attempts.save(any())).thenAnswer(i -> i.getArgument(0));
        NotificationMessageJpaSpringRepository messages = mock(NotificationMessageJpaSpringRepository.class);
        when(messages.save(any())).thenAnswer(i -> i.getArgument(0));
        BroadcastJpaSpringRepository broadcasts = mock(BroadcastJpaSpringRepository.class);
        when(broadcasts.save(any())).thenAnswer(i -> i.getArgument(0));
        InAppNotificationJpaSpringRepository inbox = mock(InAppNotificationJpaSpringRepository.class);
        when(inbox.save(any())).thenAnswer(i -> i.getArgument(0));
        NotificationMessage message = new NotificationMessage();
        message.setMessageId("msg-1");
        message.setTenantId("org-a");
        InAppNotification notification = new InAppNotification();
        notification.setUserId("u1");
        notification.setTenantId("org-a");

        assertThat(new NotificationAttemptJpaRepositoryAdapter(attempts)
                .save(NotificationAttempt.builder().id("a1").tenantId("org-a").build()).getTenantId()).isEqualTo("org-a");
        assertThat(new NotificationMessageJpaRepositoryAdapter(messages).save(message).getTenantId()).isEqualTo("org-a");
        assertThat(new BroadcastJpaRepositoryAdapter(broadcasts)
                .save(Broadcast.builder().id("b1").tenantId("org-a").build()).getTenantId()).isEqualTo("org-a");
        assertThat(new InAppNotificationJpaRepositoryAdapter(inbox).save(notification).getTenantId()).isEqualTo("org-a");
    }

    @Test
    void aLogListingForAnOrganizationUsesTheScopedQuery() {
        NotificationLogJpaSpringRepository spring = mock(NotificationLogJpaSpringRepository.class);
        when(spring.findByCorrelationIdScoped(anyString(), anyString(), any(Pageable.class))).thenReturn(empty());

        new NotificationLogJpaRepositoryAdapter(spring).findByCorrelationId("corr-1", ORG_A);

        verify(spring).findByCorrelationIdScoped(eq("corr-1"), eq("org-a"), any(Pageable.class));
        verify(spring, never()).findByCorrelationId(anyString(), any(Pageable.class));
    }

    @Test
    void aMessageListingForAUserWithoutAnOrganizationBindsAnEmptyId() {
        NotificationMessageJpaSpringRepository spring = mock(NotificationMessageJpaSpringRepository.class);
        when(spring.findAllScoped(anyString(), any(Pageable.class))).thenReturn(empty());

        new NotificationMessageJpaRepositoryAdapter(spring).findAll(NO_ORG);

        verify(spring).findAllScoped(eq(""), any(Pageable.class));
    }

    @Test
    void anUnrestrictedAttemptListingDoesNotFilter() {
        NotificationAttemptJpaSpringRepository spring = mock(NotificationAttemptJpaSpringRepository.class);
        when(spring.findByMessageIdOrderByStartedAtAscAttemptNumberAsc(anyString(), any(Pageable.class))).thenReturn(empty());

        new NotificationAttemptJpaRepositoryAdapter(spring).findByMessageId("m-1", new PageQuery(0, 20));

        verify(spring).findByMessageIdOrderByStartedAtAscAttemptNumberAsc(eq("m-1"), any(Pageable.class));
        verify(spring, never()).findByMessageIdScoped(anyString(), anyString(), any(Pageable.class));
    }

    @Test
    void theDeliveryCountsForAnOrganizationUseTheScopedCount() {
        NotificationLogJpaSpringRepository spring = mock(NotificationLogJpaSpringRepository.class);
        when(spring.countByChannelAndSuccessScoped(NotificationChannel.EMAIL, false, "org-a")).thenReturn(2L);

        assertThat(new NotificationLogJpaRepositoryAdapter(spring)
                .countByChannelAndSuccess(NotificationChannel.EMAIL, false, TenantScope.limitedTo("org-a"))).isEqualTo(2L);
    }

    @Test
    void theInboxForAnOrganizationUsesTheScopedQueriesAndUpdates() {
        InAppNotificationJpaSpringRepository spring = mock(InAppNotificationJpaSpringRepository.class);
        when(spring.findByUserIdScoped(anyString(), anyString(), any(Pageable.class))).thenReturn(empty());
        InAppNotificationJpaRepositoryAdapter adapter = new InAppNotificationJpaRepositoryAdapter(spring);

        adapter.findByUserId("u1", ORG_A);
        adapter.markAsRead("n1", "u1", TenantScope.limitedTo("org-a"));
        adapter.markAllAsRead("u1", TenantScope.limitedTo("org-a"));

        verify(spring).findByUserIdScoped(eq("u1"), eq("org-a"), any(Pageable.class));
        verify(spring).markAsReadScoped("n1", "u1", "org-a");
        verify(spring).markAllAsReadScoped("u1", "org-a");
        verify(spring, never()).markAsRead(anyString(), anyString());
    }

    @Test
    void anUnrestrictedInboxUpdateDoesNotFilter() {
        InAppNotificationJpaSpringRepository spring = mock(InAppNotificationJpaSpringRepository.class);

        new InAppNotificationJpaRepositoryAdapter(spring).markAllAsRead("u1", TenantScope.UNRESTRICTED);

        verify(spring).markAllAsRead("u1");
    }

    @Test
    void aDeviceTokenIsStoredWithTheOrganizationOfItsRegistration() {
        DeviceTokenJpaSpringRepository spring = mock(DeviceTokenJpaSpringRepository.class);
        when(spring.findFirstByUserIdAndToken("u1", "tok")).thenReturn(java.util.Optional.empty());

        new DeviceTokenJpaRepositoryAdapter(spring).register("u1", "tok", new com.heuristiq.notification.domain.model.DeviceRegistration(
                "android", null, null, null, null, null, null, null, "org-a"));

        ArgumentCaptor<DeviceTokenEntity> saved = ArgumentCaptor.forClass(DeviceTokenEntity.class);
        verify(spring).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo("org-a");
    }
}
