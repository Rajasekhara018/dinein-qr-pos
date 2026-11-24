package com.heuristiq.notification.application.usecase;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.heuristiq.notification.domain.model.DefaultNotificationTemplates;
import com.heuristiq.notification.domain.model.NotificationTemplate;
import com.heuristiq.notification.domain.repository.ITemplateRepository;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.domain.repository.PageResult;
import com.heuristiq.notification.domain.repository.TenantScope;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.application.port.in.TemplateUseCase;
import com.heuristiq.notification.application.port.out.TemplateLookupCache;
import com.heuristiq.notification.application.security.TenantAccess;
import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.notification.shared.NmsNotFoundException;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TemplateService implements TemplateUseCase {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);


    /**
     * The brands the default templates were seeded with before the defaults were RealBoom's: a production database seeded
     * with no {@code nms.branding.*} configured holds the RailCab text, and one seeded by the dev profile the old dev brand.
     * A stored default that still equals the render of one of these was never edited, so it is safe to re-brand.
     */
    private static final List<NmsProperties.Branding> PREVIOUS_BRANDS = List.of(
            previousBrand("RailCab", "support@railcab.in"),
            previousBrand("Realboom", "support@realboom.in"));

    private final ITemplateRepository repository;
    private final NmsProperties properties;
    private final TemplateLookupCache lookupCache;

    private static NmsProperties.Branding previousBrand(String name, String supportEmail) {
        NmsProperties.Branding brand = new NmsProperties.Branding();
        brand.setName(name);
        brand.setSupportEmail(supportEmail);
        brand.setTagline("Ride Platform");
        return brand;
    }

    /**
     * Seeds the predefined auth/payment templates (see {@link DefaultNotificationTemplates})
     * for any event x channel combination that doesn't already have one. Never overwrites a
     * template an admin has already created or edited. The brand name and support address come
     * from {@code nms.branding.*}, so each profile seeds its own product name.
     */
    @PostConstruct
    void seedDefaultsIfMissing() {
        LocalDateTime now = LocalDateTime.now();
        List<NotificationTemplate> missing = DefaultNotificationTemplates.DEFAULTS.stream()
                .filter(seed -> !repository.existsByEventAndChannel(seed.event(), seed.channel()))
                .map(seed -> toTemplate(seed, now))
                .toList();
        if (!missing.isEmpty()) {
            try {
                repository.saveAll(missing);
            } finally {
                lookupCache.evictAll();
            }
        }
        rebrandUntouchedDefaults();
    }

    /**
     * Re-brands the shared default templates that are still exactly what an earlier brand seeded (see
     * {@link #PREVIOUS_BRANDS}), so a deployment that was seeded with the wrong product name is corrected on its next start.
     * A template an admin edited in any way no longer matches and is never touched; nor is one that already carries the
     * configured brand.
     */
    void rebrandUntouchedDefaults() {
        NmsProperties.Branding current = properties.getBranding();
        LocalDateTime now = LocalDateTime.now();
        List<NotificationTemplate> changed = new ArrayList<>();
        for (DefaultNotificationTemplates.Seed seed : DefaultNotificationTemplates.DEFAULTS) {
            Optional<NotificationTemplate> stored = repository.findByEventAndChannelAndActiveTrue(seed.event(), seed.channel());
            if (stored == null || stored.isEmpty()) {
                continue;
            }
            NotificationTemplate template = stored.get();
            String subject = applyBranding(seed.subject(), current);
            String body = applyBranding(seed.body(), current);
            if (matches(template, subject, body)) {
                continue;
            }
            boolean untouched = PREVIOUS_BRANDS.stream()
                    .anyMatch(brand -> matches(template, applyBranding(seed.subject(), brand), applyBranding(seed.body(), brand)));
            if (untouched) {
                template.setSubject(subject);
                template.setBody(body == null ? null : Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)));
                template.setUpdatedAt(now);
                changed.add(template);
            }
        }
        if (!changed.isEmpty()) {
            try {
                repository.saveAll(changed);
            } finally {
                lookupCache.evictAll();
            }
        }
    }

    /** True when the stored template has exactly this subject and (decoded) body. */
    private static boolean matches(NotificationTemplate template, String subject, String body) {
        if (!Objects.equals(template.getSubject(), subject)) {
            return false;
        }
        if (body == null || template.getBody() == null) {
            return body == null && template.getBody() == null;
        }
        try {
            return body.equals(new String(Base64.getDecoder().decode(template.getBody()), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
    }

    private NotificationTemplate toTemplate(DefaultNotificationTemplates.Seed seed, LocalDateTime now) {
        NotificationTemplate template = new NotificationTemplate();
        template.setEvent(normalizeEvent(seed.event()));
        template.setChannel(seed.channel());
        template.setName(seed.name());
        template.setSubject(applyBranding(seed.subject(), properties.getBranding()));
        template.setBody(Base64.getEncoder().encodeToString(applyBranding(seed.body(), properties.getBranding()).getBytes(StandardCharsets.UTF_8)));
        template.setActive(true);
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        return template;
    }

    /** Swaps the brand placeholders (name, support address, tagline, colours) for the configured values. */
    private static String applyBranding(String text, NmsProperties.Branding b) {
        if (text == null) {
            return null;
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(DefaultNotificationTemplates.SUPPORT_EMAIL_TOKEN, b.getSupportEmail());
        values.put(DefaultNotificationTemplates.MATRIMONY_NAME_TOKEN, b.getMatrimonyName());
        values.put(DefaultNotificationTemplates.TAGLINE_TOKEN, b.getTagline());
        values.put(DefaultNotificationTemplates.PRIMARY_COLOR_TOKEN, b.getPrimaryColor());
        values.put(DefaultNotificationTemplates.BRAND_PRIMARY_TINT_TOKEN, b.getPrimaryTintColor());
        values.put(DefaultNotificationTemplates.ACCENT_COLOR_TOKEN, b.getAccentColor());
        values.put(DefaultNotificationTemplates.COLOR_ALERT_TOKEN, b.getAlertColor());
        values.put(DefaultNotificationTemplates.COLOR_SUCCESS_TOKEN, b.getSuccessColor());
        values.put(DefaultNotificationTemplates.COLOR_APPROVED_TOKEN, b.getApprovedColor());
        values.put(DefaultNotificationTemplates.COLOR_DANGER_TOKEN, b.getDangerColor());
        values.put(DefaultNotificationTemplates.COLOR_WARNING_TOKEN, b.getWarningColor());
        values.put(DefaultNotificationTemplates.BRAND_NAME_TOKEN, b.getName());
        String result = text;
        for (Map.Entry<String, String> e : values.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }

    @Override
    public NotificationTemplate create(NotificationTemplate template) {
        template.setEvent(normalizeEvent(template.getEvent()));
        template.setTenantId(ownerOf(template.getTenantId()));
        boolean taken = template.getTenantId() == null
                ? repository.existsByEventAndChannel(template.getEvent(), template.getChannel())
                : repository.existsOrganizationTemplate(template.getEvent(), template.getChannel(), template.getTenantId());
        if (taken) {
            throw new IllegalArgumentException("A template for " + template.getEvent() + " / " + template.getChannel()
                    + (template.getTenantId() == null ? " already exists" : " already exists for this organization"));
        }
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        try {
            return repository.save(template);
        } finally {
            lookupCache.evictAll();
        }
    }

    @Override
    public Optional<NotificationTemplate> findById(String id) {
        return repository.findById(id).filter(t -> TenantAccess.canSee(t.getTenantId()));
    }

    @Override
    public List<NotificationTemplate> findByEvent(String event) {
        return repository.findByEventAndActiveTrue(normalizeEvent(event)).stream()
                .filter(t -> TenantAccess.canSee(t.getTenantId()))
                .toList();
    }

    @Override
    public Optional<NotificationTemplate> findByEventAndChannel(String event, NotificationChannel channel) {
        return findForTenant(event, channel, TenantAccess.stamp());
    }

    @Override
    public Optional<NotificationTemplate> findForTenant(String event, NotificationChannel channel, String tenantId) {
        String normalized = normalizeEvent(event);
        // The final answer (own template, else shared, else none) is cached per organization or shared, event and channel.
        return lookupCache.resolve(tenantId, normalized, channel, () -> loadForTenant(normalized, channel, tenantId));
    }

    private Optional<NotificationTemplate> loadForTenant(String normalized, NotificationChannel channel, String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            Optional<NotificationTemplate> own = repository.findOrganizationTemplate(normalized, channel, tenantId.trim());
            if (own.isPresent()) {
                return own;
            }
        }
        return repository.findByEventAndChannelAndActiveTrue(normalized, channel);
    }

    @Override
    public List<NotificationTemplate> findAll() {
        return repository.findByActiveTrue();
    }

    /**
     * The templates screen. A person of an organization works on the organization's OWN templates: on each visit any shared
     * template the organization does not have yet is copied into it ({@link #ensureOrganizationCopies}), so a new organization
     * starts with a full, editable set, and the list then shows only the organization's own (no shared duplicates, never
     * another organization's). The platform (no organization, or a backend caller) keeps seeing what its scope allows.
     */
    @Override
    public PageResult<NotificationTemplate> findAll(PageQuery pageQuery) {
        TenantScope scope = TenantAccess.scope();
        if (!scope.unrestricted() && scope.hasTenant()) {
            ensureOrganizationCopies(scope.tenantId());
            return repository.findOrganizationActive(scope.tenantId(), pageQuery);
        }
        return repository.findByActiveTrue(pageQuery.withScope(scope));
    }

    /**
     * Gives the organization its own copy of every active shared template it has no template for yet (by event and channel).
     * A template the organization owns, even one it switched off, is never copied again, so a deliberate removal sticks.
     * Copies keep the shared text as it is now; later changes to the shared template do not reach them. Idempotent: when two
     * requests copy at once, the one that loses on the unique (event, channel, organization) key is skipped.
     */
    void ensureOrganizationCopies(String tenantId) {
        Set<String> owned = new HashSet<>();
        for (NotificationTemplate own : repository.findOrganizationTemplates(tenantId)) {
            owned.add(copyKey(own.getEvent(), own.getChannel()));
        }
        LocalDateTime now = LocalDateTime.now();
        boolean copied = false;
        for (NotificationTemplate shared : repository.findSharedActive()) {
            if (!owned.add(copyKey(shared.getEvent(), shared.getChannel()))) {
                continue;
            }
            NotificationTemplate copy = new NotificationTemplate();
            copy.setTenantId(tenantId);
            copy.setEvent(shared.getEvent());
            copy.setChannel(shared.getChannel());
            copy.setName(shared.getName());
            copy.setSubject(shared.getSubject());
            copy.setBody(shared.getBody());
            copy.setExternalTemplateId(shared.getExternalTemplateId());
            copy.setActive(true);
            copy.setCreatedAt(now);
            copy.setUpdatedAt(now);
            try {
                repository.save(copy);
                copied = true;
            } catch (RuntimeException alreadyCopied) {
                // Another request copied this one first (unique event + channel + organization): nothing to do.
                log.debug("Template {} / {} already copied for organization {}", shared.getEvent(), shared.getChannel(), tenantId);
            }
        }
        if (copied) {
            lookupCache.evictAll();
        }
    }

    private static String copyKey(String event, NotificationChannel channel) {
        return event + '|' + channel;
    }

    /**
     * Changes a template. A person of an organization who edits a SHARED template gets their organization's own copy with
     * the change ({@link #saveOrganizationCopy}); the shared one, which every other organization uses, stays as it was.
     */
    @Override
    public NotificationTemplate update(String id, NotificationTemplate updated) {
        String callerOrganization = TenantAccess.stamp();
        return repository.findById(id).filter(t -> TenantAccess.canSee(t.getTenantId())).map(existing -> {
            if (existing.getTenantId() == null && callerOrganization != null) {
                return saveOrganizationCopy(updated, callerOrganization);
            }
            existing.setEvent(normalizeEvent(updated.getEvent()));
            existing.setChannel(updated.getChannel());
            existing.setName(updated.getName());
            existing.setSubject(updated.getSubject());
            existing.setBody(updated.getBody());
            existing.setExternalTemplateId(updated.getExternalTemplateId());
            existing.setActive(updated.isActive());
            existing.setUpdatedAt(LocalDateTime.now());
            try {
                return repository.save(existing);
            } finally {
                lookupCache.evictAll();
            }
        }).orElseThrow(() -> new NmsNotFoundException("Template not found: " + id));
    }

    /**
     * The organization's own version of a shared template, holding the edit. Sending prefers an organization's own template
     * ({@link #loadForTenant}), so the change applies to that organization only. Refused when the organization already has
     * its own template for the event and channel: that one is the one to edit.
     */
    private NotificationTemplate saveOrganizationCopy(NotificationTemplate updated, String tenantId) {
        String event = normalizeEvent(updated.getEvent());
        if (repository.existsOrganizationTemplate(event, updated.getChannel(), tenantId)) {
            throw new IllegalArgumentException("This organization already has its own template for " + event + " / "
                    + updated.getChannel() + "; edit that one instead");
        }
        LocalDateTime now = LocalDateTime.now();
        NotificationTemplate copy = new NotificationTemplate();
        copy.setTenantId(tenantId);
        copy.setEvent(event);
        copy.setChannel(updated.getChannel());
        copy.setName(updated.getName());
        copy.setSubject(updated.getSubject());
        copy.setBody(updated.getBody());
        copy.setExternalTemplateId(updated.getExternalTemplateId());
        copy.setActive(updated.isActive());
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        try {
            return repository.save(copy);
        } finally {
            lookupCache.evictAll();
        }
    }

    /**
     * Switches a template off. A person of an organization may not switch off a SHARED template: it would stop it for every
     * organization. They edit it instead, which gives their organization its own version.
     */
    @Override
    public void delete(String id) {
        String callerOrganization = TenantAccess.stamp();
        repository.findById(id).filter(t -> TenantAccess.canSee(t.getTenantId())).ifPresent(t -> {
            if (t.getTenantId() == null && callerOrganization != null) {
                throw new IllegalArgumentException("A shared template cannot be removed by an organization; edit it to make this "
                        + "organization's own version");
            }
            t.setActive(false);
            t.setUpdatedAt(LocalDateTime.now());
            try {
                repository.save(t);
            } finally {
                lookupCache.evictAll();
            }
        });
    }

    /**
     * Who owns a new template: nobody (a shared template) unless it names an organization, and then only the caller's own
     * organization, except that a backend caller may name any. A person's request never creates another organization's template.
     */
    private String ownerOf(String requested) {
        if (requested == null || requested.isBlank()) {
            return null;
        }
        String wanted = requested.trim();
        var scope = TenantAccess.scope();
        if (scope.unrestricted() || (scope.hasTenant() && scope.tenantId().equals(wanted))) {
            return wanted;
        }
        throw new IllegalArgumentException("A template can only be created for your own organization");
    }

    private String normalizeEvent(String event) {
        return event == null ? null : event.trim().toUpperCase();
    }
}
