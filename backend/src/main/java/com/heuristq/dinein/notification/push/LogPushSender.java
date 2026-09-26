package com.heuristq.dinein.notification.push;

import com.heuristq.dinein.notification.RecipientMask;
import com.heuristq.dinein.notification.SenderRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Default provider: writes a log line instead of sending. */
@Slf4j
@Component
public class LogPushSender implements PushSender {

    @Override
    public String provider() {
        return SenderRegistry.LOG;
    }

    @Override
    public void send(PushMessage message) {
        log.info("notification.push.logged token={} title={}", RecipientMask.token(message.token()), message.title());
    }
}
