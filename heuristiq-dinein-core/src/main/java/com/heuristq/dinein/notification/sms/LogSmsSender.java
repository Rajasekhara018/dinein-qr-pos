package com.heuristq.dinein.notification.sms;

import com.heuristq.dinein.notification.RecipientMask;
import com.heuristq.dinein.notification.SenderRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Default provider: writes a log line instead of sending. */
@Slf4j
@Component
public class LogSmsSender implements SmsSender {

    @Override
    public String provider() {
        return SenderRegistry.LOG;
    }

    @Override
    public void send(SmsMessage message) {
        log.info("notification.sms.logged to={} template={} text={}", RecipientMask.phone(message.to()),
                message.template(), message.text());
    }
}
