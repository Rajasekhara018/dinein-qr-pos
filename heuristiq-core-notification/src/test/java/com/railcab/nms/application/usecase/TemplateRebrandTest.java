package com.railcab.nms.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.github.benmanes.caffeine.cache.Ticker;
import com.railcab.nms.domain.model.DefaultNotificationTemplates;
import com.railcab.nms.domain.model.NotificationTemplate;
import com.railcab.nms.domain.repository.ITemplateRepository;
import com.railcab.nms.infrastructure.cache.CaffeineTemplateLookupCache;
import com.railcab.nms.infrastructure.config.NmsProperties;

/**
 * A deployment that was seeded before the default brand was RealBoom's keeps the wrong product name in its stored default
 * templates. They are re-branded on the next start, but only when nobody edited them.
 */
class TemplateRebrandTest {

    private static NmsProperties properties(Consumer<NmsProperties.Branding> brand) {
        NmsProperties properties = new NmsProperties();
        properties.getCache().setEnabled(false);
        brand.accept(properties.getBranding());
        return properties;
    }

    private static TemplateService service(ITemplateRepository repository, NmsProperties properties) {
        return new TemplateService(repository, properties,
                new CaffeineTemplateLookupCache(properties, Ticker.systemTicker(), null));
    }

    private static String body(NotificationTemplate template) {
        return new String(Base64.getDecoder().decode(template.getBody()), StandardCharsets.UTF_8);
    }

    /** The default templates as an older deployment stored them, seeded with the given (old) brand. */
    private static List<NotificationTemplate> seededWith(Consumer<NmsProperties.Branding> oldBrand) {
        ITemplateRepository empty = mock(ITemplateRepository.class);
        when(empty.existsByEventAndChannel(anyString(), any())).thenReturn(false);
        service(empty, properties(oldBrand)).seedDefaultsIfMissing();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationTemplate>> saved = ArgumentCaptor.forClass(List.class);
        verify(empty).saveAll(saved.capture());
        return new ArrayList<>(saved.getValue());
    }

    /** A repository holding exactly these templates as the shared, active ones. */
    private static ITemplateRepository holding(List<NotificationTemplate> stored) {
        ITemplateRepository repository = mock(ITemplateRepository.class);
        when(repository.existsByEventAndChannel(anyString(), any())).thenReturn(true);
        for (DefaultNotificationTemplates.Seed seed : DefaultNotificationTemplates.DEFAULTS) {
            stored.stream().filter(t -> t.getEvent().equalsIgnoreCase(seed.event()) && t.getChannel() == seed.channel()).findFirst()
                    .ifPresent(t -> when(repository.findByEventAndChannelAndActiveTrue(seed.event(), seed.channel())).thenReturn(Optional.of(t)));
        }
        return repository;
    }

    private static void oldProductionBrand(NmsProperties.Branding b) {
        b.setName("RailCab");
        b.setSupportEmail("support@railcab.in");
        b.setTagline("Ride Platform");
    }

    @Test
    void theDefaultBrandIsRealBoom() {
        NmsProperties.Branding brand = new NmsProperties().getBranding();

        assertThat(brand.getName()).isEqualTo("RealBoom");
        assertThat(brand.getSupportEmail()).isEqualTo("support@realboom.in");
        assertThat(brand.getTagline()).doesNotContain("Ride");
        assertThat(brand.defaultSubject()).isEqualTo("RealBoom Notification");
    }

    @Test
    void untouchedRailCabTemplatesAreRebrandedToTheConfiguredBrand() {
        List<NotificationTemplate> stored = seededWith(TemplateRebrandTest::oldProductionBrand);
        // Brand-free defaults (the application-readiness alerts name no product) carry nothing to re-brand.
        assertThat(stored.stream().filter(t -> body(t).contains("RailCab"))).isNotEmpty();
        ITemplateRepository repository = holding(stored);

        service(repository, properties(b -> { })).rebrandUntouchedDefaults();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationTemplate>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).isNotEmpty().allSatisfy(t -> {
            assertThat(body(t)).doesNotContain("RailCab").doesNotContain("support@railcab.in").doesNotContain("Ride Platform");
            assertThat(body(t)).contains("RealBoom");
            assertThat(t.getSubject() == null ? "" : t.getSubject()).doesNotContain("RailCab");
        });
    }

    @Test
    void untouchedTemplatesOfTheOldDevBrandAreRebrandedToo() {
        List<NotificationTemplate> stored = seededWith(b -> {
            b.setName("Realboom");
            b.setSupportEmail("support@realboom.in");
            b.setTagline("Ride Platform");
        });
        ITemplateRepository repository = holding(stored);

        service(repository, properties(b -> { })).rebrandUntouchedDefaults();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationTemplate>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).isNotEmpty().allSatisfy(t -> assertThat(body(t)).doesNotContain("Ride Platform").doesNotContain("Realboom"));
    }

    @Test
    void aTemplateAnAdminEditedIsNeverTouched() {
        List<NotificationTemplate> stored = seededWith(TemplateRebrandTest::oldProductionBrand);
        NotificationTemplate edited = stored.get(0);
        edited.setBody(Base64.getEncoder().encodeToString((body(edited) + "<p>Our own footer, RailCab forever</p>").getBytes(StandardCharsets.UTF_8)));
        ITemplateRepository repository = holding(List.of(edited));

        service(repository, properties(b -> { })).rebrandUntouchedDefaults();

        verify(repository, never()).saveAll(any());
        assertThat(body(edited)).contains("Our own footer, RailCab forever");
    }

    @Test
    void anAdminEditedSubjectIsNeverTouchedEither() {
        List<NotificationTemplate> stored = seededWith(TemplateRebrandTest::oldProductionBrand);
        NotificationTemplate edited = stored.get(0);
        edited.setSubject("A subject written by an admin");
        ITemplateRepository repository = holding(List.of(edited));

        service(repository, properties(b -> { })).rebrandUntouchedDefaults();

        verify(repository, never()).saveAll(any());
        assertThat(edited.getSubject()).isEqualTo("A subject written by an admin");
    }

    @Test
    void templatesThatAlreadyCarryTheConfiguredBrandAreLeftAlone() {
        List<NotificationTemplate> stored = seededWith(b -> { });
        ITemplateRepository repository = holding(stored);

        service(repository, properties(b -> { })).rebrandUntouchedDefaults();

        verify(repository, never()).saveAll(any());
    }

    @Test
    void aDeploymentThatConfiguresItsOwnBrandIsNotRewrittenBackToTheDefault() {
        List<NotificationTemplate> stored = seededWith(b -> b.setName("Acme"));
        ITemplateRepository repository = holding(stored);

        service(repository, properties(b -> b.setName("Acme"))).rebrandUntouchedDefaults();

        verify(repository, never()).saveAll(any());
    }

    @Test
    void nothingIsStoredYetMeansNothingToRebrand() {
        ITemplateRepository repository = mock(ITemplateRepository.class);
        when(repository.findByEventAndChannelAndActiveTrue(anyString(), any())).thenReturn(Optional.empty());

        service(repository, properties(b -> { })).rebrandUntouchedDefaults();

        verify(repository, never()).saveAll(any());
    }
}
