package com.heuristiq.notification.domain.repository;

import java.util.List;
import java.util.Optional;

import com.heuristiq.notification.domain.model.Broadcast;
import com.heuristiq.notification.domain.model.BroadcastStatus;

public interface IBroadcastRepository {

    Broadcast save(Broadcast broadcast);

    Optional<Broadcast> findById(String id);

    PageResult<Broadcast> findAll(PageQuery pageQuery);

    /** Due for dispatch: given status, with scheduledAt null or not after the given instant. */
    List<Broadcast> findDue(BroadcastStatus status, java.time.LocalDateTime notAfter);
}
