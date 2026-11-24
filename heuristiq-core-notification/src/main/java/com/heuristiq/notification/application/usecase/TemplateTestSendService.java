package com.railcab.nms.application.usecase;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.railcab.nms.application.dto.TemplateTestSendRequest;
import com.railcab.nms.application.dto.TemplateTestSendResponse;
import com.railcab.nms.application.port.in.TemplateTestSendUseCase;
import com.railcab.nms.application.port.out.ChannelProvider;
import com.railcab.nms.application.port.out.ProviderSendResult;
import com.railcab.nms.application.usecase.support.OrganizationVariables;
import com.railcab.nms.application.usecase.support.TemplateRenderer;
import com.railcab.nms.application.usecase.support.TemplateTestSendConstants;
import com.railcab.nms.application.usecase.support.TestSendRateLimiter;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.infrastructure.config.NmsProperties;
import com.railcab.nms.shared.NmsTooManyRequestsException;

/**
 * Renders the template exactly as typed in the editor (placeholders filled the same way real sends do,
 * plus the organization's own variables) and sends it once, marked as a test, through the real channel provider.
 * Nothing is stored: the message is not a notification, has no log entry and cannot be retried.
 */
@Service
public class TemplateTestSendService implements TemplateTestSendUseCase {

    private static final Logger log = LoggerFactory.getLogger(TemplateTestSendService.class);

    private final TemplateRenderer renderer;
    private final OrganizationVariables organizationVariables;
    private final List<ChannelProvider> channelProviders;
    private final TestSendRateLimiter rateLimiter;

    public TemplateTestSendService(TemplateRenderer renderer, OrganizationVariables organizationVariables,
                                   List<ChannelProvider> channelProviders, NmsProperties properties, Clock clock) {
        this.renderer = renderer;
        this.organizationVariables = organizationVariables;
        this.channelProviders = channelProviders;
        this.rateLimiter = new TestSendRateLimiter(clock, properties.getTemplateTest().getMaxPerHour());
    }

    @Override
    public TemplateTestSendResponse send(String callerId, String tenantId, TemplateTestSendRequest request) {
        NotificationChannel channel = request.channel();
        if (!TemplateTestSendConstants.TESTABLE_CHANNELS.contains(channel)) {
            throw new IllegalArgumentException("A test can only be sent for EMAIL, SMS or WHATSAPP templates");
        }
        String recipient = normalizeRecipient(channel, request.recipient());
        ChannelProvider provider = channelProviders.stream()
                .filter(p -> p.getChannel() == channel)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No provider is configured for " + channel));
        if (!rateLimiter.tryAcquire(StringUtils.hasText(callerId) ? callerId : "anonymous")) {
            throw new NmsTooManyRequestsException("Too many test messages; try again later");
        }

        Map<String, String> variables = organizationVariables.merge(tenantId, request.variables());
        String body = renderer.renderText(request.body(), variables);
        String subject = null;
        if (channel == NotificationChannel.EMAIL) {
            String rendered = StringUtils.hasText(request.subject()) ? renderer.renderText(request.subject(), variables) : "";
            subject = TemplateTestSendConstants.TEST_PREFIX + rendered;
        } else {
            body = TemplateTestSendConstants.TEST_PREFIX + body;
        }

        ProviderSendResult result = provider.sendDetailed(recipient, subject, body, null);
        log.info("Template test sent: channel={} success={} provider={} caller={}",
                channel, result.success(), result.providerName(), callerId);
        return new TemplateTestSendResponse(result.success(), result.providerName(), result.errorCode(), result.errorMessage());
    }

    private static String normalizeRecipient(NotificationChannel channel, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (channel == NotificationChannel.EMAIL) {
            if (!TemplateTestSendConstants.EMAIL.matcher(value).matches()) {
                throw new IllegalArgumentException("Enter a valid email address");
            }
            return value;
        }
        String phone = value.replaceAll("[\\s\\-()]", "");
        if (!TemplateTestSendConstants.PHONE.matcher(phone).matches()) {
            throw new IllegalArgumentException("Enter a valid phone number with country code");
        }
        return phone;
    }
}
