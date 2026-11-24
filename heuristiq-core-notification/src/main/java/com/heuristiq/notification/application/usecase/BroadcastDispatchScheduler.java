package com.railcab.nms.application.usecase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.railcab.nms.application.dto.NotificationRequest;
import com.railcab.nms.application.dto.UamUserSummary;
import com.railcab.nms.application.port.in.NotificationDispatchUseCase;
import com.railcab.nms.application.port.out.AudienceResolutionException;
import com.railcab.nms.application.port.out.BroadcastAudienceResolver;
import com.railcab.nms.application.usecase.support.NotificationRequestBuilder;
import com.railcab.nms.application.usecase.support.NmsOriginConstants;
import com.railcab.nms.domain.model.Broadcast;
import com.railcab.nms.domain.model.BroadcastStatus;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.domain.repository.IBroadcastRepository;

import lombok.RequiredArgsConstructor;

/**
 * Scheduled execution of due broadcasts -- split out of {@link BroadcastService} so the
 * HTTP-facing CRUD use case (create/list/get/cancel) and the timer-triggered audience-resolution/
 * fan-out algorithm each have exactly one reason to change.
 */
@Service
@RequiredArgsConstructor
public class BroadcastDispatchScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BroadcastDispatchScheduler.class);

    private final IBroadcastRepository broadcastRepository;
    private final BroadcastAudienceResolver audienceResolver;
    private final NotificationDispatchUseCase dispatchService;

    @Scheduled(fixedDelayString = "${nms.broadcast.dispatch-fixed-delay-ms:60000}")
    public void dispatchDue() {
        List<Broadcast> due = broadcastRepository.findDue(BroadcastStatus.PENDING, LocalDateTime.now());
        due.forEach(this::dispatch);
    }

    private void dispatch(Broadcast broadcast) {
        logger.info("BroadcastDispatchScheduler: dispatching broadcast id={} event={} segment={}",
                broadcast.getId(), broadcast.getEvent(), broadcast.getSegment());
        try {
            // A broadcast of an organization reaches that organization's people only; UAM lists everyone to a service caller.
            List<UamUserSummary> recipients = audienceResolver.resolveSegment(broadcast.getSegment()).stream()
                    .filter(user -> broadcast.getTenantId() == null || broadcast.getTenantId().isBlank()
                            || broadcast.getTenantId().equals(user.tenantId()))
                    .toList();
            int sent = 0;
            for (UamUserSummary recipient : recipients) {
                if (dispatchToRecipient(broadcast, recipient)) {
                    sent++;
                }
            }
            broadcast.setRecipientCount(sent);
            broadcast.setStatus(BroadcastStatus.SENT);
            broadcast.setSentAt(LocalDateTime.now());
        } catch (AudienceResolutionException e) {
            // Nothing was sent: the audience could not be resolved completely, so we do not send to a partial one.
            logger.error("BroadcastDispatchScheduler: broadcast id={} FAILED before sending, audience could not be resolved: {}",
                    broadcast.getId(), e.getMessage());
            broadcast.setStatus(BroadcastStatus.FAILED);
        } catch (Exception e) {
            logger.error("BroadcastDispatchScheduler: dispatch failed for broadcast id={}: {}",
                    broadcast.getId(), e.getMessage(), e);
            broadcast.setStatus(BroadcastStatus.FAILED);
        }
        broadcast.setUpdatedAt(LocalDateTime.now());
        broadcastRepository.save(broadcast);
    }

    private boolean dispatchToRecipient(Broadcast broadcast, UamUserSummary recipient) {
        List<NotificationChannel> channels = broadcast.getChannels();
        NotificationRequest request = NotificationRequestBuilder.newRequest()
                .messageId(UUID.randomUUID().toString())
                .correlationId(broadcast.getId())
                .tenantId(broadcast.getTenantId())
                .event(broadcast.getEvent())
                .origin(NmsOriginConstants.BROADCAST_SOURCE_SERVICE, null, NmsOriginConstants.BROADCAST_ENTITY_TYPE, broadcast.getId())
                .variables(broadcast.getVariables())
                .channels(channels)
                .recipientsFor(channels, recipient)
                .build();
        try {
            return dispatchService.dispatch(request).stream().anyMatch(r -> r.success());
        } catch (Exception e) {
            logger.warn("BroadcastDispatchScheduler: dispatch to recipient={} failed: {}", recipient.id(), e.getMessage());
            return false;
        }
    }
}
