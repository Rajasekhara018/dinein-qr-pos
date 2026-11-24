package com.heuristiq.notification.application.port.out;

import java.util.List;

import com.heuristiq.notification.application.dto.NotificationRequest;
import com.heuristiq.notification.domain.model.NotificationChannel;

/**
 * Resolves the recipient list for one channel from a {@link NotificationRequest}. Adding a new
 * channel means adding a new implementation, never modifying {@code NotificationDispatchService}
 * -- the same Strategy pattern already used for {@link ChannelProvider}.
 */
public interface RecipientResolver {

    NotificationChannel channel();

    List<String> resolve(NotificationRequest request);
}
