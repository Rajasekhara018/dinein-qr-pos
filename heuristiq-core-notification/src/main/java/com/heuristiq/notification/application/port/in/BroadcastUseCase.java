package com.railcab.nms.application.port.in;

import com.railcab.nms.application.dto.CreateBroadcastRequest;
import com.railcab.nms.domain.model.Broadcast;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;

/** Port the web adapter depends on instead of the concrete broadcast service. */
public interface BroadcastUseCase {

    Broadcast create(CreateBroadcastRequest request, String createdBy);

    PageResult<Broadcast> list(PageQuery pageQuery);

    Broadcast get(String id);

    Broadcast cancel(String id);
}
