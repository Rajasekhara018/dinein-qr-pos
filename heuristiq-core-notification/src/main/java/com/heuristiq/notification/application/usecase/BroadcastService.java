package com.heuristiq.notification.application.usecase;

import com.heuristiq.notification.application.security.TenantAccess;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.heuristiq.notification.domain.model.Broadcast;
import com.heuristiq.notification.domain.model.BroadcastStatus;
import com.heuristiq.notification.domain.repository.IBroadcastRepository;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.domain.repository.PageResult;
import com.heuristiq.notification.application.dto.CreateBroadcastRequest;
import com.heuristiq.notification.application.port.in.BroadcastUseCase;
import com.heuristiq.notification.shared.NmsNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Broadcast CRUD only -- scheduled dispatch execution lives in
 * {@link BroadcastDispatchScheduler} so each class has one reason to change.
 */
@Service
@RequiredArgsConstructor
public class BroadcastService implements BroadcastUseCase {

    private final IBroadcastRepository broadcastRepository;

    @Override
    public Broadcast create(CreateBroadcastRequest request, String createdBy) {
        LocalDateTime now = LocalDateTime.now();
        Broadcast broadcast = Broadcast.builder()
                .tenantId(TenantAccess.stamp())
                .title(request.title())
                .event(request.event().trim().toUpperCase())
                .variables(request.variables())
                .segment(request.segment())
                .channels(request.channels())
                .scheduledAt(request.scheduledAt())
                .status(BroadcastStatus.PENDING)
                .createdBy(createdBy)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return broadcastRepository.save(broadcast);
    }

    @Override
    public PageResult<Broadcast> list(PageQuery pageQuery) {
        return broadcastRepository.findAll(pageQuery.withScope(TenantAccess.scope()));
    }

    @Override
    public Broadcast get(String id) {
        // Another organization's broadcast behaves as if it did not exist.
        return broadcastRepository.findById(id)
                .filter(broadcast -> TenantAccess.canSee(broadcast.getTenantId()))
                .orElseThrow(() -> new NmsNotFoundException("Broadcast not found"));
    }

    @Override
    public Broadcast cancel(String id) {
        Broadcast broadcast = get(id);
        if (broadcast.getStatus() != BroadcastStatus.PENDING) {
            throw new IllegalArgumentException("Only a pending broadcast can be cancelled");
        }
        broadcast.setStatus(BroadcastStatus.CANCELLED);
        broadcast.setUpdatedAt(LocalDateTime.now());
        return broadcastRepository.save(broadcast);
    }
}
