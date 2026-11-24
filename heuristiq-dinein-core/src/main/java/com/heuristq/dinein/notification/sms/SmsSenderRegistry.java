package com.heuristq.dinein.notification.sms;

import com.heuristq.dinein.notification.NotificationProperties;
import com.heuristq.dinein.notification.SenderRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SmsSenderRegistry extends SenderRegistry<SmsSender> {

    public SmsSenderRegistry(List<SmsSender> senders, NotificationProperties properties) {
        super("sms", senders, SmsSender::provider, properties.sms().provider());
    }
}
