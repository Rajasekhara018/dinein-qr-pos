package com.railcab.nms.domain.repository;

import java.util.List;
import java.util.Optional;

import com.railcab.nms.domain.model.Broadcast;
import com.railcab.nms.domain.model.BroadcastStatus;

public interface IBroadcastRepository {

    Broadcast save(Broadcast broadcast);

    Optional<Broadcast> findById(String id);

    PageResult<Broadcast> findAll(PageQuery pageQuery);

    /** Due for dispatch: given status, with scheduledAt null or not after the given instant. */
    List<Broadcast> findDue(BroadcastStatus status, java.time.LocalDateTime notAfter);
}
