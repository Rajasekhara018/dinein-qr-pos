package com.railcab.nms.adapters.out.channel.email.smtp;

import com.railcab.nms.infrastructure.config.NmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.railcab.nms.adapters.out.channel.email.EmailProvider;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

import static com.railcab.nms.application.port.out.ChannelProviderConstants.*;

@Component("smtpEmailProvider")
@RequiredArgsConstructor
public class SmtpEmailProvider implements EmailProvider {

    private static final Logger logger = LoggerFactory.getLogger(SmtpEmailProvider.class);

    private final JavaMailSender mailSender;
    private final NmsProperties props;

    @Value("${nms.email.from:}")
    private String from;

    @Override
    public String getName() { return PROVIDER_SMTP; }

    @Override
    public boolean send(String to, String subject, String htmlBody) {
        logger.info("SmtpEmailProvider.send: to={}", to);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(StringUtils.hasText(from) ? from : props.getEmail().getFrom());
            helper.setTo(to);
            helper.setSubject(StringUtils.hasText(subject) ? subject : props.getBranding().defaultSubject());
            helper.setText(htmlBody != null ? htmlBody : "", true);
            mailSender.send(message);
            logger.info("SmtpEmailProvider: sent to {}", to);
            return true;
        } catch (Exception e) {
            logger.error("SmtpEmailProvider.send failed: {}", e.getMessage(), e);
            return false;
        }
    }
}
