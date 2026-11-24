package com.heuristq.dinein.notification.email;

import com.heuristq.dinein.notification.RecipientMask;
import com.heuristq.dinein.notification.SenderRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Default provider: writes a log line instead of sending. */
@Slf4j
@Component
public class LogEmailSender implements EmailSender {

    @Override
    public String provider() {
        return SenderRegistry.LOG;
    }

    @Override
    public void send(EmailMessage message) {
        log.info("notification.email.logged to={} subject={}", RecipientMask.email(message.to()), message.subject());
    }
}
