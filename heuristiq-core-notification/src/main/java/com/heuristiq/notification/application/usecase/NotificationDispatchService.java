package com.railcab.nms.application.usecase;

import com.railcab.nms.application.security.TenantAccess;
import com.railcab.nms.application.security.NotificationTenantContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.railcab.nms.domain.model.NotificationTemplate;
import com.railcab.nms.application.dto.NotificationDispatchResult;
import com.railcab.nms.application.dto.NotificationRequest;
import com.railcab.nms.application.port.in.NotificationDispatchUseCase;
import com.railcab.nms.application.port.out.ChannelProvider;
import com.railcab.nms.application.port.out.RecipientResolver;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.application.usecase.NotificationMessageService.Reservation;
import com.railcab.nms.application.usecase.support.ChannelResilienceDecorator;
import com.railcab.nms.application.usecase.support.NotificationAuditRecorder;
import com.railcab.nms.application.usecase.support.NotificationDispatchMetrics;
import com.railcab.nms.application.usecase.support.OrganizationVariables;
import com.railcab.nms.application.usecase.support.PushDataBuilder;
import com.railcab.nms.application.usecase.support.RecipientMasker;
import com.railcab.nms.application.usecase.support.TemplateRenderer;
import com.railcab.nms.application.dto.ChannelSendOutcome;
import com.railcab.nms.application.port.in.TemplateUseCase;

import lombok.RequiredArgsConstructor;

/**
 * Core orchestrator.
 *
 * <p>For each incoming {@link NotificationRequest}:
 * <ol>
 *   <li>Determine which channels to attempt (request override or every channel
 *       that has an active template for the event).</li>
 *   <li>For each channel: load the active template, render it, call the
 *       appropriate {@link ChannelProvider} through {@link ChannelResilienceDecorator},
 *       and record the attempt via {@link NotificationDispatchMetrics}/{@link NotificationAuditRecorder}.</li>
 * </ol>
 *
 * <p>Reliability (circuit breaker/bulkhead/retry), metrics, and audit-log persistence are each
 * owned by a dedicated collaborator rather than inlined here -- this class is orchestration only.
 */
@Service
@RequiredArgsConstructor
public class NotificationDispatchService implements NotificationDispatchUseCase {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final TemplateUseCase templateService;
    private final TemplateRenderer templateRenderer;
    private final NotificationMessageService messageService;
    private final Executor notificationExecutor;

    private final ChannelResilienceDecorator resilienceDecorator;
    private final NotificationAuditRecorder auditRecorder;
    private final OrganizationVariables organizationVariables;
    private final NotificationDispatchMetrics metrics;

    /** All {@link ChannelProvider} beans auto-collected by Spring */
    private final List<ChannelProvider> channelProviders;

    /** All {@link RecipientResolver} beans auto-collected by Spring -- adding a channel means
     *  adding a new resolver, never modifying this class. */
    private final List<RecipientResolver> recipientResolvers;

    /**
     * Dispatches the notification for all applicable channels.
     *
     * @return list of per-channel results (channel name → success flag)
     */
    @Override
    public List<NotificationDispatchResult> dispatch(NotificationRequest request) {
        request.setEvent(normalizeEvent(request.getEvent()));
        // The organization comes from the verified caller; a backend caller (or work with no caller, such as Kafka
        // or the broadcast scheduler) may name one itself, a person's request body is never trusted for it.
        request.setTenantId(TenantAccess.stampOrRequested(request.getTenantId()));
        // Every message knows which organization sends it, so one template reads right for each ({{organizationName}} ...).
        request.setVariables(organizationVariables.merge(request.getTenantId(), request.getVariables()));
        logger.info("NotificationDispatchService.dispatch: event={}", request.getEvent());

        Reservation reservation = messageService.reserve(request);
        if (!reservation.accepted()) {
            logger.info("Duplicate notification messageId={} ignored", request.getMessageId());
            return List.of(new NotificationDispatchResult(null, true, "Duplicate messageID ignored"));
        }

        List<NotificationChannel> targetChannels = resolveChannels(request);
        List<CompletableFuture<NotificationDispatchResult>> futures = new ArrayList<>();

        for (NotificationChannel channel : targetChannels) {
            ChannelProvider provider = findProvider(channel);
            if (provider == null) {
                logger.warn("No ChannelProvider registered for channel={}", channel);
                metrics.recordSkipped("no_provider");
                continue;
            }

            templateService.findForTenant(request.getEvent(), channel, request.getTenantId()).ifPresentOrElse(
                    template -> {
                        futures.add(CompletableFuture.supplyAsync(
                                () -> dispatchChannel(provider, template, request),
                                notificationExecutor));
                    },
                    () -> {
                        logger.info("No active template for event={} channel={}", request.getEvent(), channel);
                        metrics.recordSkipped("no_template");
                    }
            );
        }

        List<NotificationDispatchResult> results = futures.stream()
                .map(this::joinDispatch)
                .toList();
        boolean anySuccess = results.stream().anyMatch(NotificationDispatchResult::success);
        String error = results.stream()
                .filter(result -> !result.success() && result.error() != null)
                .map(NotificationDispatchResult::error)
                .findFirst()
                .orElse(anySuccess ? null : "No channel dispatched successfully");
        messageService.complete(request.getMessageId(), anySuccess, error);
        return results;
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private NotificationDispatchResult dispatchChannel(ChannelProvider provider,
            NotificationTemplate template,
            NotificationRequest request) {

        NotificationChannel channel = provider.getChannel();
        Map<String, String> variables = request.getVariables();

        String renderedBody = templateRenderer.render(template.getBody(), variables);
        String renderedSubject = template.getSubject() != null
                ? renderInline(template.getSubject(), variables)
                : null;

        List<String> recipients = recipientsFor(channel, request);
        if (recipients.isEmpty()) {
            logger.info("No recipients for channel={}, skipping", channel);
            metrics.recordSkipped("no_recipients");
            return new NotificationDispatchResult(channel, false, "No recipients provided");
        }

        boolean anySuccess = false;
        String lastError = null;
        // Structured data (push data contract) so the apps can route a push; VOICE also carries the message id.
        Map<String, String> data = PushDataBuilder.forChannel(channel, request);

        for (String recipient : recipients) {
            long start = System.nanoTime();
            ChannelSendOutcome outcome = resilienceDecorator.callDetailed(channel,
                    () -> NotificationTenantContext.callAs(request.getTenantId(),
                            () -> provider.sendDetailed(recipient, renderedSubject, renderedBody,
                                    template.getExternalTemplateId(), data)));
            metrics.record(provider, outcome, Duration.ofNanos(System.nanoTime() - start));
            auditRecorder.record(request, provider, recipient, renderedBody, outcome);

            lastError = outcome.error();
            if (outcome.success()) {
                anySuccess = true;
            } else {
                logger.warn("Provider send failed channel={} recipient={} attempts={}: {}",
                        channel, maskRecipient(channel, recipient), outcome.attempts(), outcome.error());
            }
        }

        return new NotificationDispatchResult(channel, anySuccess, anySuccess ? null : lastError);
    }

    /** A push token is a credential and a phone number or address is personal data -- never log either in full. */
    private static String maskRecipient(NotificationChannel channel, String recipient) {
        if (recipient == null) {
            return null;
        }
        if (channel != NotificationChannel.FCM_PUSH) {
            return RecipientMasker.mask(recipient);
        }
        return recipient.length() <= 8 ? "***" : recipient.substring(0, 8) + "...";
    }

    private List<String> recipientsFor(NotificationChannel channel, NotificationRequest request) {
        return recipientResolvers.stream()
                .filter(r -> r.channel() == channel)
                .findFirst()
                .map(r -> r.resolve(request))
                .orElse(List.of());
    }

    private List<NotificationChannel> resolveChannels(NotificationRequest request) {
        if (request.getChannels() != null && !request.getChannels().isEmpty()) {
            return request.getChannels();
        }
        return templateService.findByEvent(request.getEvent())
                .stream()
                .map(NotificationTemplate::getChannel)
                .distinct()
                .toList();
    }

    private ChannelProvider findProvider(NotificationChannel channel) {
        return channelProviders.stream()
                .filter(p -> p.getChannel() == channel)
                .findFirst()
                .orElse(null);
    }

    private NotificationDispatchResult joinDispatch(CompletableFuture<NotificationDispatchResult> future) {
        try {
            return future.join();
        } catch (Exception e) {
            logger.error("Notification dispatch task failed: {}", e.getMessage(), e);
            return new NotificationDispatchResult(null, false, e.getMessage());
        }
    }

    private String normalizeEvent(String event) {
        return event == null ? null : event.trim().toUpperCase();
    }

    /** Inline {{key}} replacement for the subject line (no Base64 decoding needed). */
    private String renderInline(String template, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) return template;
        String result = template;
        for (var entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
