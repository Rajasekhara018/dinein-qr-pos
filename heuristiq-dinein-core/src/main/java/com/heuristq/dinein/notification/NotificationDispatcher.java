package com.heuristq.dinein.notification;

import com.heuristq.dinein.notification.NotificationRequest.PushTarget;
import com.heuristq.dinein.notification.NotificationTemplates.Message;
import com.heuristq.dinein.notification.domain.DeliveryStatus;
import com.heuristq.dinein.notification.domain.NotificationLogEntity;
import com.heuristq.dinein.notification.domain.NotificationLogRepository;
import com.heuristq.dinein.notification.email.EmailMessage;
import com.heuristq.dinein.notification.email.EmailSenderRegistry;
import com.heuristq.dinein.notification.push.InvalidPushTokenException;
import com.heuristq.dinein.notification.push.PushMessage;
import com.heuristq.dinein.notification.push.PushSenderRegistry;
import com.heuristq.dinein.notification.sms.SmsMessage;
import com.heuristq.dinein.notification.sms.SmsSenderRegistry;
import com.heuristq.dinein.staff.domain.StaffRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Delivers one request on every channel its recipients imply, one recipient at a time. Each attempt is recorded in
 * {@code notification_log} (SENT / FAILED / SKIPPED); a failing provider or recipient never stops the others and
 * nothing is thrown to the caller. Runs on the notification executor, never inside a business transaction.
 */
@Slf4j
@Component
public class NotificationDispatcher {

    private static final String IN_APP_PROVIDER = "DB";

    private final NotificationProperties properties;
    private final NotificationTemplates templates;
    private final EmailSenderRegistry emailSenders;
    private final SmsSenderRegistry smsSenders;
    private final PushSenderRegistry pushSenders;
    private final InAppNotificationService inAppService;
    private final PushSubscriptionService pushSubscriptions;
    private final NotificationLogRepository logRepository;

    public NotificationDispatcher(NotificationProperties properties, NotificationTemplates templates,
                                  EmailSenderRegistry emailSenders, SmsSenderRegistry smsSenders,
                                  PushSenderRegistry pushSenders, InAppNotificationService inAppService,
                                  PushSubscriptionService pushSubscriptions, NotificationLogRepository logRepository) {
        this.properties = properties;
        this.templates = templates;
        this.emailSenders = emailSenders;
        this.smsSenders = smsSenders;
        this.pushSenders = pushSenders;
        this.inAppService = inAppService;
        this.pushSubscriptions = pushSubscriptions;
        this.logRepository = logRepository;
    }

    public void dispatch(NotificationRequest request) {
        try {
            String restaurant = templates.restaurantName(request.restaurantId());
            NotificationRequest.Recipients to = request.recipients();
            to.staffRoles().forEach(role -> inApp(request, role, restaurant));
            to.emails().forEach(email -> email(request, email, restaurant));
            to.phones().forEach(phone -> sms(request, phone, restaurant));
            to.push().forEach(target -> push(request, target, restaurant));
        } catch (RuntimeException e) {
            log.error("notification.dispatch_failed event={} orderId={}", request.event(), request.orderId(), e);
        }
    }

    private void inApp(NotificationRequest request, StaffRole role, String restaurant) {
        if (!properties.inApp().enabled()) {
            record(NotificationChannel.IN_APP, IN_APP_PROVIDER, request, role.name(), DeliveryStatus.SKIPPED, "channel disabled");
            return;
        }
        attempt(NotificationChannel.IN_APP, IN_APP_PROVIDER, request, role.name(), () -> {
            Message m = templates.render(request.event(), NotificationChannel.IN_APP, request.data(), restaurant);
            inAppService.createForRole(role, request.event(), m, request.orderId(), request.restaurantId());
        });
    }

    private void email(NotificationRequest request, String email, String restaurant) {
        String provider = emailSenders.activeProvider();
        String masked = RecipientMask.email(email);
        if (!properties.email().enabled()) {
            record(NotificationChannel.EMAIL, provider, request, masked, DeliveryStatus.SKIPPED, "channel disabled");
            return;
        }
        attempt(NotificationChannel.EMAIL, provider, request, masked, () -> {
            Message m = templates.render(request.event(), NotificationChannel.EMAIL, request.data(), restaurant);
            emailSenders.active().send(new EmailMessage(email, m.subject(), m.body()));
        });
    }

    private void sms(NotificationRequest request, String phone, String restaurant) {
        String provider = smsSenders.activeProvider();
        String masked = RecipientMask.phone(phone);
        if (!properties.sms().enabled()) {
            record(NotificationChannel.SMS, provider, request, masked, DeliveryStatus.SKIPPED, "channel disabled");
            return;
        }
        attempt(NotificationChannel.SMS, provider, request, masked, () -> {
            Message m = templates.render(request.event(), NotificationChannel.SMS, request.data(), restaurant);
            Map<String, String> variables = new HashMap<>(request.data());
            variables.put("restaurant", restaurant);
            smsSenders.active().send(new SmsMessage(SmsMessage.e164(phone, properties.sms().defaultCountryCode()),
                    m.body(), request.event().name(), variables));
        });
    }

    private void push(NotificationRequest request, PushTarget target, String restaurant) {
        String provider = pushSenders.activeProvider();
        String masked = RecipientMask.token(target.token());
        if (!properties.push().enabled()) {
            record(NotificationChannel.PUSH, provider, request, masked, DeliveryStatus.SKIPPED, "channel disabled");
            return;
        }
        if (!provider.equalsIgnoreCase(target.provider())) {
            record(NotificationChannel.PUSH, target.provider(), request, masked, DeliveryStatus.SKIPPED,
                    "provider not active");
            return;
        }
        attempt(NotificationChannel.PUSH, provider, request, masked, () -> {
            Message m = templates.render(request.event(), NotificationChannel.PUSH, request.data(), restaurant);
            Map<String, String> data = new HashMap<>();
            data.put("event", request.event().name());
            if (request.orderId() != null) {
                data.put("orderId", String.valueOf(request.orderId()));
            }
            try {
                pushSenders.active().send(new PushMessage(target.token(), m.subject(), m.body(),
                        templates.absolute(m.link()), data));
            } catch (InvalidPushTokenException e) {
                pushSubscriptions.deactivate(target.subscriptionId());
                throw e;
            }
            pushSubscriptions.markUsed(target.subscriptionId());
        });
    }

    private void attempt(NotificationChannel channel, String provider, NotificationRequest request, String masked,
                         Runnable send) {
        try {
            send.run();
            record(channel, provider, request, masked, DeliveryStatus.SENT, null);
            log.info("notification.sent channel={} provider={} event={} orderId={} to={}",
                    channel, provider, request.event(), request.orderId(), masked);
        } catch (RuntimeException e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("notification.failed channel={} provider={} event={} orderId={} to={} error={}",
                    channel, provider, request.event(), request.orderId(), masked, error);
            record(channel, provider, request, masked, DeliveryStatus.FAILED, error);
        }
    }

    private void record(NotificationChannel channel, String provider, NotificationRequest request, String masked,
                        DeliveryStatus status, String error) {
        try {
            NotificationLogEntity entry = new NotificationLogEntity();
            entry.setChannel(channel);
            entry.setProvider(provider);
            entry.setTemplate(request.event().name());
            entry.setRecipientMasked(masked);
            entry.setStatus(status);
            entry.setError(error == null || error.length() <= 500 ? error : error.substring(0, 500));
            entry.setRelatedOrderId(request.orderId());
            logRepository.save(entry);
        } catch (RuntimeException e) {
            log.warn("notification.log_failed channel={} event={} error={}", channel, request.event(), e.getMessage());
        }
    }
}
