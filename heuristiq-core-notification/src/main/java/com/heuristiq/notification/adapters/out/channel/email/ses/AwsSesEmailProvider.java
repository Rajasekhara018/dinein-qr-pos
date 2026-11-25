package com.heuristiq.notification.adapters.out.channel.email.ses;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.notification.adapters.out.channel.email.EmailProvider;
import com.heuristiq.notification.application.port.out.ProviderSendResult;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import static com.heuristiq.notification.application.port.out.ChannelProviderConstants.*;

@Component("sesEmailProvider")
@RequiredArgsConstructor
public class AwsSesEmailProvider implements EmailProvider {

    private static final Logger logger = LoggerFactory.getLogger(AwsSesEmailProvider.class);

    private final NmsProperties props;

    // Lazily built and cached on first send -- credentials/region come from NmsProperties.Ses,
    // which isn't guaranteed populated at bean-construction time (relaxed binding order), and
    // most deployments never activate this provider (nms.provider.email defaults to "smtp").
    private volatile SesV2Client client;

    @PreDestroy
    void destroy() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public String getName() { return PROVIDER_SES; }

    @Override
    public boolean send(String to, String subject, String htmlBody) {
        return sendDetailed(to, subject, htmlBody).success();
    }

    @Override
    public ProviderSendResult sendDetailed(String to, String subject, String htmlBody) {
        NmsProperties.Ses ses = props.getSes();
        if (!StringUtils.hasText(ses.getAccessKey()) || !StringUtils.hasText(ses.getSecretKey())) {
            logger.error("AwsSesEmailProvider: access-key or secret-key not configured");
            return ProviderSendResult.failed(PROVIDER_SES, CODE_NOT_CONFIGURED, "SES access-key or secret-key not configured");
        }
        logger.info("AwsSesEmailProvider.send");
        try {
            SendEmailResponse response = client().sendEmail(SendEmailRequest.builder()
                    .fromEmailAddress(ses.getFromName() + " <" + ses.getFromAddress() + ">")
                    .destination(Destination.builder().toAddresses(to).build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(Content.builder().data(StringUtils.hasText(subject) ? subject : props.getBranding().defaultSubject()).charset("UTF-8").build())
                                    .body(Body.builder()
                                            .html(Content.builder().data(htmlBody != null ? htmlBody : "").charset("UTF-8").build())
                                            .build())
                                    .build())
                            .build())
                    .build());
            return ProviderSendResult.ok(PROVIDER_SES, response.messageId(), STATUS_ACCEPTED);
        } catch (SesV2Exception e) {
            logger.error("AwsSesEmailProvider.send failed: {}", e.getMessage(), e);
            return ProviderSendResult.failed(PROVIDER_SES, ProviderSendResult.CODE_EXCEPTION, e.getMessage());
        }
    }

    private SesV2Client client() {
        SesV2Client existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (client == null) {
                NmsProperties.Ses ses = props.getSes();
                client = SesV2Client.builder()
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(ses.getAccessKey(), ses.getSecretKey())))
                        .region(Region.of(ses.getRegion()))
                        .build();
            }
            return client;
        }
    }
}
