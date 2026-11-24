package com.heuristq.dinein.notification.email;

import com.heuristq.dinein.notification.NotificationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP through Spring's {@link JavaMailSender} ({@code spring.mail.host/port/username/password}). Only created when
 * selected, so the application starts without any mail configuration.
 */
@Component
@ConditionalOnProperty(prefix = "app.notifications.email", name = "provider", havingValue = "SMTP")
public class SmtpEmailSender implements EmailSender {

    public static final String CODE = "SMTP";

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public String provider() {
        return CODE;
    }

    @Override
    public void send(EmailMessage message) {
        String from = properties.email().from();
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("app.notifications.email.from is not set");
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        mailSender.send(mail);
    }
}
