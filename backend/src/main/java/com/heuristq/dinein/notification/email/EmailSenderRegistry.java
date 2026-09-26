package com.heuristq.dinein.notification.email;

import com.heuristq.dinein.notification.NotificationProperties;
import com.heuristq.dinein.notification.SenderRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmailSenderRegistry extends SenderRegistry<EmailSender> {

    public EmailSenderRegistry(List<EmailSender> senders, NotificationProperties properties) {
        super("email", senders, EmailSender::provider, properties.email().provider());
    }
}
