package com.railcab.nms.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.railcab.nms.application.dto.CreateBroadcastRequest;
import com.railcab.nms.application.security.NmsPrincipal;
import com.railcab.nms.domain.model.Broadcast;
import com.railcab.nms.domain.model.BroadcastSegment;
import com.railcab.nms.domain.model.BroadcastStatus;
import com.railcab.nms.domain.model.DeviceRegistration;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.domain.repository.IBroadcastRepository;
import com.railcab.nms.domain.repository.IDeviceTokenRepository;
import com.railcab.nms.domain.repository.IInAppNotificationRepository;
import com.railcab.nms.domain.repository.INotificationAttemptRepository;
import com.railcab.nms.domain.repository.INotificationLogRepository;
import com.railcab.nms.domain.repository.INotificationMessageRepository;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;
import com.railcab.nms.domain.repository.TenantScope;
import com.railcab.nms.shared.NmsNotFoundException;

/** Organization limits applied by the read services, and the organization stamped on new broadcasts and device tokens. */
class TenantScopedServicesTest {

    private static final PageQuery PAGE = new PageQuery(0, 20);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(String tenantId, boolean internal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new NmsPrincipal(internal ? "internal-service" : "u-1", tenantId, internal), null, List.of()));
    }

    private static ArgumentCaptor<PageQuery> pageCaptor() {
        return ArgumentCaptor.forClass(PageQuery.class);
    }

    private static <T> PageResult<T> emptyPage() {
        return new PageResult<>(List.of(), 0, 20, 0);
    }

    // logs

    @Test
    void theLogListingIsLimitedToTheCallersOrganization() {
        INotificationLogRepository repository = mock(INotificationLogRepository.class);
        when(repository.findAll(any())).thenReturn(emptyPage());
        signInAs("org-a", false);

        new NotificationLogService(repository).findAll(PAGE);

        ArgumentCaptor<PageQuery> query = pageCaptor();
        verify(repository).findAll(query.capture());
        assertThat(query.getValue().scope()).isEqualTo(TenantScope.limitedTo("org-a"));
    }

    @Test
    void theChannelStatisticsOnlyCountTheCallersOrganization() {
        INotificationLogRepository repository = mock(INotificationLogRepository.class);
        signInAs("org-a", false);

        new NotificationLogService(repository).stats();

        verify(repository).countByChannelAndSuccess(NotificationChannel.SMS, true, TenantScope.limitedTo("org-a"));
        verify(repository).countByChannelAndSuccess(NotificationChannel.SMS, false, TenantScope.limitedTo("org-a"));
    }

    @Test
    void aUserWithoutAnOrganizationOnlyListsLogsWithNoOrganization() {
        INotificationLogRepository repository = mock(INotificationLogRepository.class);
        when(repository.findByEvent(any(), any())).thenReturn(emptyPage());
        signInAs(null, false);

        new NotificationLogService(repository).findByEvent("otp", PAGE);

        ArgumentCaptor<PageQuery> query = pageCaptor();
        verify(repository).findByEvent(eq("OTP"), query.capture());
        assertThat(query.getValue().scope()).isEqualTo(TenantScope.limitedTo(null));
    }

    @Test
    void anInternalCallerListsLogsOfEveryOrganization() {
        INotificationLogRepository repository = mock(INotificationLogRepository.class);
        when(repository.findByChannel(any(), any())).thenReturn(emptyPage());
        signInAs(null, true);

        new NotificationLogService(repository).findByChannel(NotificationChannel.EMAIL, PAGE);

        ArgumentCaptor<PageQuery> query = pageCaptor();
        verify(repository).findByChannel(eq(NotificationChannel.EMAIL), query.capture());
        assertThat(query.getValue().scope().unrestricted()).isTrue();
    }

    // attempts and messages

    @Test
    void theAttemptListingIsLimitedAndStillCapsThePageSize() {
        INotificationAttemptRepository repository = mock(INotificationAttemptRepository.class);
        when(repository.findByMessageId(any(), any())).thenReturn(emptyPage());
        signInAs("org-a", false);

        new NotificationAttemptQueryService(repository).findByMessageId("m-1", new PageQuery(0, 5000));

        ArgumentCaptor<PageQuery> query = pageCaptor();
        verify(repository).findByMessageId(eq("m-1"), query.capture());
        assertThat(query.getValue().size()).isEqualTo(NotificationAttemptQueryService.MAX_PAGE_SIZE);
        assertThat(query.getValue().scope()).isEqualTo(TenantScope.limitedTo("org-a"));
    }

    @Test
    void theMessageListingIsLimitedToTheCallersOrganization() {
        INotificationMessageRepository repository = mock(INotificationMessageRepository.class);
        when(repository.findAll(any())).thenReturn(emptyPage());
        when(repository.findByCorrelationId(any(), any())).thenReturn(emptyPage());
        signInAs("org-a", false);
        NotificationMessageQueryService service = new NotificationMessageQueryService(repository);

        service.list(null, null, PAGE);
        service.list(null, "corr-1", PAGE);

        ArgumentCaptor<PageQuery> all = pageCaptor();
        verify(repository).findAll(all.capture());
        assertThat(all.getValue().scope()).isEqualTo(TenantScope.limitedTo("org-a"));
        ArgumentCaptor<PageQuery> byCorrelation = pageCaptor();
        verify(repository).findByCorrelationId(eq("corr-1"), byCorrelation.capture());
        assertThat(byCorrelation.getValue().scope()).isEqualTo(TenantScope.limitedTo("org-a"));
    }

    // broadcasts

    private static Broadcast broadcast(String tenantId) {
        return Broadcast.builder().id("b-1").tenantId(tenantId).status(BroadcastStatus.PENDING).build();
    }

    private static IBroadcastRepository broadcastsHolding(Broadcast stored) {
        IBroadcastRepository repository = mock(IBroadcastRepository.class);
        when(repository.findById("b-1")).thenReturn(Optional.of(stored));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        return repository;
    }

    private static CreateBroadcastRequest createRequest() {
        return new CreateBroadcastRequest("Title", "ADMIN_BROADCAST", null, BroadcastSegment.ALL,
                List.of(NotificationChannel.IN_APP), null);
    }

    @Test
    void anotherOrganizationsBroadcastIsNotFound() {
        BroadcastService service = new BroadcastService(broadcastsHolding(broadcast("org-b")));
        signInAs("org-a", false);

        assertThatThrownBy(() -> service.get("b-1")).isInstanceOf(NmsNotFoundException.class);
        assertThatThrownBy(() -> service.cancel("b-1")).isInstanceOf(NmsNotFoundException.class);
    }

    @Test
    void aBroadcastOfTheCallersOrganizationOrOfNoOrganizationIsFound() {
        signInAs("org-a", false);

        assertThat(new BroadcastService(broadcastsHolding(broadcast("org-a"))).get("b-1").getId()).isEqualTo("b-1");
        assertThat(new BroadcastService(broadcastsHolding(broadcast(null))).get("b-1").getId()).isEqualTo("b-1");
    }

    @Test
    void aUserWithoutAnOrganizationCannotSeeAnOrganizationsBroadcast() {
        BroadcastService service = new BroadcastService(broadcastsHolding(broadcast("org-a")));
        signInAs(null, false);

        assertThatThrownBy(() -> service.get("b-1")).isInstanceOf(NmsNotFoundException.class);
    }

    @Test
    void anInternalCallerWithoutAnOrganizationCanReachAnyBroadcast() {
        BroadcastService service = new BroadcastService(broadcastsHolding(broadcast("org-b")));
        signInAs(null, true);

        assertThat(service.get("b-1").getTenantId()).isEqualTo("org-b");
    }

    @Test
    void aNewBroadcastIsStampedWithTheCallersOrganization() {
        IBroadcastRepository repository = mock(IBroadcastRepository.class);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        signInAs("org-a", false);

        Broadcast created = new BroadcastService(repository).create(createRequest(), "u-1");

        assertThat(created.getTenantId()).isEqualTo("org-a");
    }

    @Test
    void aBroadcastCreatedByAnInternalCallerWithoutAnOrganizationStoresNone() {
        IBroadcastRepository repository = mock(IBroadcastRepository.class);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        signInAs(null, true);

        Broadcast created = new BroadcastService(repository).create(createRequest(), "u-1");

        assertThat(created.getTenantId()).isNull();
    }

    @Test
    void theBroadcastListingIsLimitedToTheCallersOrganization() {
        IBroadcastRepository repository = mock(IBroadcastRepository.class);
        when(repository.findAll(any())).thenReturn(emptyPage());
        signInAs("org-a", false);

        new BroadcastService(repository).list(PAGE);

        ArgumentCaptor<PageQuery> query = pageCaptor();
        verify(repository).findAll(query.capture());
        assertThat(query.getValue().scope()).isEqualTo(TenantScope.limitedTo("org-a"));
    }

    // inbox and device tokens

    @Test
    void theInboxIsLimitedToTheCallersOrganization() {
        IInAppNotificationRepository repository = mock(IInAppNotificationRepository.class);
        when(repository.findByUserId(any(), any())).thenReturn(emptyPage());
        signInAs("org-a", false);
        InAppNotificationService service = new InAppNotificationService(repository);

        service.list("u-1", PAGE);
        service.unreadCount("u-1");
        service.markAsRead("n-1", "u-1");
        service.markAllAsRead("u-1");

        ArgumentCaptor<PageQuery> query = pageCaptor();
        verify(repository).findByUserId(eq("u-1"), query.capture());
        assertThat(query.getValue().scope()).isEqualTo(TenantScope.limitedTo("org-a"));
        verify(repository).countUnreadByUserId("u-1", TenantScope.limitedTo("org-a"));
        verify(repository).markAsRead("n-1", "u-1", TenantScope.limitedTo("org-a"));
        verify(repository).markAllAsRead("u-1", TenantScope.limitedTo("org-a"));
    }

    @Test
    void aRegisteredDeviceTokenIsStampedWithTheCallersOrganization() {
        IDeviceTokenRepository repository = mock(IDeviceTokenRepository.class);
        signInAs("org-a", false);

        new DeviceTokenService(repository).register("u-1", "tok",
                new DeviceRegistration("android", "DRIVER", null, null, null, null, null, null));

        ArgumentCaptor<DeviceRegistration> details = ArgumentCaptor.forClass(DeviceRegistration.class);
        verify(repository).register(eq("u-1"), eq("tok"), details.capture());
        assertThat(details.getValue().tenantId()).isEqualTo("org-a");
        assertThat(details.getValue().platform()).isEqualTo("android");
    }

    @Test
    void aTokenRegisteredWithOnlyAPlatformIsStampedToo() {
        IDeviceTokenRepository repository = mock(IDeviceTokenRepository.class);
        signInAs("org-a", false);

        new DeviceTokenService(repository).register("u-1", "tok", "web");

        ArgumentCaptor<DeviceRegistration> details = ArgumentCaptor.forClass(DeviceRegistration.class);
        verify(repository).register(eq("u-1"), eq("tok"), details.capture());
        assertThat(details.getValue().tenantId()).isEqualTo("org-a");
    }
}
