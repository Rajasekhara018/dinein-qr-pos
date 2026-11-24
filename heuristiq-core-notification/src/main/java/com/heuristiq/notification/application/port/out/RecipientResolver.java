package com.railcab.nms.application.port.out;

import java.util.List;

import com.railcab.nms.application.dto.NotificationRequest;
import com.railcab.nms.domain.model.NotificationChannel;

/**
 * Resolves the recipient list for one channel from a {@link NotificationRequest}. Adding a new
 * channel means adding a new implementation, never modifying {@code NotificationDispatchService}
 * -- the same Strategy pattern already used for {@link ChannelProvider}.
 */
public interface RecipientResolver {

    NotificationChannel channel();

    List<String> resolve(NotificationRequest request);
}
