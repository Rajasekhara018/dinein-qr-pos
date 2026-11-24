package com.railcab.nms.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.railcab.nms.application.dto.NotificationDispatchResult;
import com.railcab.nms.application.dto.UamUserSummary;
import com.railcab.nms.application.port.in.NotificationDispatchUseCase;
import com.railcab.nms.application.port.out.AudienceResolutionException;
import com.railcab.nms.application.port.out.BroadcastAudienceResolver;
import com.railcab.nms.domain.model.Broadcast;
import com.railcab.nms.domain.model.BroadcastSegment;
import com.railcab.nms.domain.model.BroadcastStatus;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.domain.repository.IBroadcastRepository;

class BroadcastDispatchSchedulerTest {

    private final IBroadcastRepository repo = mock(IBroadcastRepository.class);
    private final BroadcastAudienceResolver resolver = mock(BroadcastAudienceResolver.class);
    private final NotificationDispatchUseCase dispatch = mock(NotificationDispatchUseCase.class);
    private final BroadcastDispatchScheduler scheduler = new BroadcastDispatchScheduler(repo, resolver, dispatch);

    private Broadcast due() {
        Broadcast b = Broadcast.builder().id("b1").event("ADMIN_BROADCAST").segment(BroadcastSegment.ALL)
                .channels(List.of(NotificationChannel.EMAIL)).status(BroadcastStatus.PENDING).build();
        when(repo.findDue(any(), any())).thenReturn(List.of(b));
        return b;
    }

    @Test
    void anUnresolvableAudienceFailsTheBroadcastAndSendsNothing() {
        due();
        when(resolver.resolveSegment(BroadcastSegment.ALL)).thenThrow(new AudienceResolutionException("page 3 failed"));

        scheduler.dispatchDue();

        ArgumentCaptor<Broadcast> saved = ArgumentCaptor.forClass(Broadcast.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(BroadcastStatus.FAILED);
        assertThat(saved.getValue().getSentAt()).isNull();
        verify(dispatch, never()).dispatch(any());
    }

    @Test
    void aBroadcastOfAnOrganizationReachesOnlyThatOrganizationsPeople() {
        Broadcast b = Broadcast.builder().id("b2").event("ADMIN_BROADCAST").segment(BroadcastSegment.ALL).tenantId("org-a")
                .channels(List.of(NotificationChannel.EMAIL)).status(BroadcastStatus.PENDING).build();
        when(repo.findDue(any(), any())).thenReturn(List.of(b));
        when(resolver.resolveSegment(BroadcastSegment.ALL)).thenReturn(List.of(
                new UamUserSummary("mine", "n", "a@b.com", null, List.of("CUSTOMER"), "ACTIVE", true, "org-a"),
                new UamUserSummary("theirs", "n", "c@d.com", null, List.of("CUSTOMER"), "ACTIVE", true, "org-b"),
                new UamUserSummary("legacy", "n", "e@f.com", null, List.of("CUSTOMER"), "ACTIVE", true)));
        when(dispatch.dispatch(any())).thenReturn(List.of(new NotificationDispatchResult(NotificationChannel.EMAIL, true, null)));

        scheduler.dispatchDue();

        ArgumentCaptor<Broadcast> saved = ArgumentCaptor.forClass(Broadcast.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getRecipientCount()).isEqualTo(1);
        verify(dispatch, org.mockito.Mockito.times(1)).dispatch(any());
    }

    @Test
    void aFullyResolvedAudienceIsSentAndMarkedSent() {
        due();
        when(resolver.resolveSegment(BroadcastSegment.ALL)).thenReturn(List.of(
                new UamUserSummary("u1", "n", "a@b.com", null, List.of("CUSTOMER"), "ACTIVE", true)));
        when(dispatch.dispatch(any())).thenReturn(List.of(new NotificationDispatchResult(NotificationChannel.EMAIL, true, null)));

        scheduler.dispatchDue();

        ArgumentCaptor<Broadcast> saved = ArgumentCaptor.forClass(Broadcast.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(BroadcastStatus.SENT);
        assertThat(saved.getValue().getRecipientCount()).isEqualTo(1);
    }
}
