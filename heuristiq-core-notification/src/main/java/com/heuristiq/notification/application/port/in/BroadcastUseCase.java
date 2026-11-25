package com.heuristiq.notification.application.port.in;

import com.heuristiq.notification.application.dto.CreateBroadcastRequest;
import com.heuristiq.notification.domain.model.Broadcast;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.domain.repository.PageResult;

/** Port the web adapter depends on instead of the concrete broadcast service. */
public interface BroadcastUseCase {

    Broadcast create(CreateBroadcastRequest request, String createdBy);

    PageResult<Broadcast> list(PageQuery pageQuery);

    Broadcast get(String id);

    Broadcast cancel(String id);
}
