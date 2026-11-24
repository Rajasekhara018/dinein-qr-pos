package com.heuristq.dinein.notification.push;

import com.heuristq.dinein.notification.NotificationProperties;
import com.heuristq.dinein.notification.SenderRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PushSenderRegistry extends SenderRegistry<PushSender> {

    public PushSenderRegistry(List<PushSender> senders, NotificationProperties properties) {
        super("push", senders, PushSender::provider, properties.push().provider());
    }
}
