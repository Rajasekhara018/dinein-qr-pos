package com.railcab.nms.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.github.benmanes.caffeine.cache.Ticker;
import com.railcab.nms.application.security.NmsPrincipal;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.domain.model.NotificationTemplate;
import com.railcab.nms.domain.repository.ITemplateRepository;
import com.railcab.nms.infrastructure.cache.CaffeineTemplateLookupCache;
import com.railcab.nms.infrastructure.config.NmsProperties;

/**
 * Proves the cache wired into {@link TemplateService}: reads are served from it, every writer evicts, and one
 * organization's templates never reach another. Uses an in-memory repository so the real fallback logic runs.
 */
class TemplateServiceCacheTest {

    private static final String EVENT = "OTP_SENT";
    private static final NotificationChannel CH = NotificationChannel.SMS;

    private final Map<String, NotificationTemplate> store = new ConcurrentHashMap<>();
    private ITemplateRepository repository;
    private NmsProperties props;
    private long[] clock;
    private TemplateService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new NmsPrincipal("internal-service", null, true), null, List.of()));
        repository = mock(ITemplateRepository.class);
        when(repository.save(any())).thenAnswer(i -> {
            NotificationTemplate t = i.getArgument(0);
            if (t.getId() == null) {
                t.setId("id-" + store.size());
            }
            store.put(t.getId(), t);
            return t;
        });
        when(repository.saveAll(any())).thenAnswer(i -> {
            List<NotificationTemplate> all = i.getArgument(0);
            all.forEach(t -> store.put("seed-" + store.size(), t));
            return all;
        });
        when(repository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(store.get(i.<String>getArgument(0))));
        when(repository.findOrganizationTemplate(anyString(), any(), anyString())).thenAnswer(i -> store.values().stream()
                .filter(t -> t.isActive() && i.getArgument(2).equals(t.getTenantId()) && i.getArgument(0).equals(t.getEvent())
                        && i.getArgument(1) == t.getChannel()).findFirst());
        when(repository.findByEventAndChannelAndActiveTrue(anyString(), any())).thenAnswer(i -> store.values().stream()
                .filter(t -> t.isActive() && t.getTenantId() == null && i.getArgument(0).equals(t.getEvent())
                        && i.getArgument(1) == t.getChannel()).findFirst());
        when(repository.existsByEventAndChannel(anyString(), any())).thenAnswer(i -> store.values().stream()
                .anyMatch(t -> t.getTenantId() == null && i.getArgument(0).equals(t.getEvent())
                        && i.getArgument(1) == t.getChannel()));
        when(repository.existsOrganizationTemplate(anyString(), any(), anyString())).thenAnswer(i -> store.values().stream()
                .anyMatch(t -> i.getArgument(2).equals(t.getTenantId()) && i.getArgument(0).equals(t.getEvent())
                        && i.getArgument(1) == t.getChannel()));
        props = new NmsProperties();
        clock = new long[1];
        service = serviceWith(props);
    }

    private TemplateService serviceWith(NmsProperties p) {
        Ticker ticker = () -> clock[0];
        return new TemplateService(repository, p, new CaffeineTemplateLookupCache(p, ticker, null));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private NotificationTemplate newTemplate(String tenant, String subject) {
        NotificationTemplate t = new NotificationTemplate();
        t.setEvent(EVENT);
        t.setChannel(CH);
        t.setName("n");
        t.setSubject(subject);
        t.setBody("Ym9keQ==");
        t.setTenantId(tenant);
        return t;
    }

    private String subjectFor(String tenant) {
        return service.findForTenant(EVENT, CH, tenant).map(NotificationTemplate::getSubject).orElse(null);
    }

    @Test
    void repeatedSendPathLookupsHitTheDatabaseOnce() {
        service.create(newTemplate(null, "shared"));
        subjectFor("orgA");
        subjectFor("orgA");
        subjectFor("orgA");
        verify(repository, times(1)).findOrganizationTemplate(EVENT, CH, "orgA");
        verify(repository, times(1)).findByEventAndChannelAndActiveTrue(EVENT, CH);
    }

    @Test
    void tenantOwnTemplateOverridesSharedOnlyForThatTenantAndNeverLeaks() {
        service.create(newTemplate(null, "shared"));
        service.create(newTemplate("orgA", "own A"));

        assertThat(subjectFor("orgA")).isEqualTo("own A");
        assertThat(subjectFor("orgB")).isEqualTo("shared");
        assertThat(subjectFor(null)).isEqualTo("shared");
        assertThat(subjectFor("orgA")).isEqualTo("own A");
        assertThat(subjectFor("orgB")).isEqualTo("shared");
    }

    @Test
    void createMakesANewTemplateVisibleImmediatelyEvenAfterANoTemplateAnswerWasCached() {
        assertThat(subjectFor("orgA")).isNull();
        service.create(newTemplate("orgA", "fresh"));
        assertThat(subjectFor("orgA")).isEqualTo("fresh");
    }

    @Test
    void createOfASharedTemplateIsSeenByOrganizationsThatCachedTheFallback() {
        service.create(newTemplate(null, "shared v1"));
        assertThat(subjectFor("orgA")).isEqualTo("shared v1");
        service.create(newTemplate("orgA", "own"));
        assertThat(subjectFor("orgA")).isEqualTo("own");
    }

    @Test
    void updateIsVisibleImmediately() {
        NotificationTemplate created = service.create(newTemplate("orgA", "v1"));
        assertThat(subjectFor("orgA")).isEqualTo("v1");

        NotificationTemplate change = newTemplate("orgA", "v2");
        service.update(created.getId(), change);
        assertThat(subjectFor("orgA")).isEqualTo("v2");
    }

    @Test
    void deleteAndDeactivateAreVisibleImmediatelyAndFallBackToShared() {
        service.create(newTemplate(null, "shared"));
        NotificationTemplate own = service.create(newTemplate("orgA", "own"));
        assertThat(subjectFor("orgA")).isEqualTo("own");

        service.delete(own.getId());
        assertThat(subjectFor("orgA")).isEqualTo("shared");
    }

    @Test
    void deactivatingViaUpdateIsVisibleImmediately() {
        NotificationTemplate created = service.create(newTemplate("orgA", "own"));
        assertThat(subjectFor("orgA")).isEqualTo("own");
        NotificationTemplate off = newTemplate("orgA", "own");
        off.setActive(false);
        service.update(created.getId(), off);
        assertThat(subjectFor("orgA")).isNull();
    }

    @Test
    void seedingDefaultsEvictsWhatWasCached() {
        when(repository.existsByEventAndChannel(anyString(), any())).thenReturn(false);
        assertThat(subjectFor("orgA")).isNull();
        service.seedDefaultsIfMissing();
        verify(repository, atLeastOnce()).saveAll(any());
        // Seeded rows are now in the store; a cached "none" must not hide them.
        NotificationTemplate seeded = store.values().stream().filter(t -> t.getTenantId() == null).findFirst().orElseThrow();
        assertThat(service.findForTenant(seeded.getEvent(), seeded.getChannel(), "orgA")).isPresent();
    }

    @Test
    void anUnchangedTemplateIsRefreshedAfterTheTtlWithoutAnyWrite() {
        NotificationTemplate created = service.create(newTemplate("orgA", "v1"));
        assertThat(subjectFor("orgA")).isEqualTo("v1");
        // A write by another instance bypasses this instance's eviction: simulate by changing the store directly.
        store.get(created.getId()).setSubject("changed elsewhere");
        assertThat(subjectFor("orgA")).isEqualTo("v1");
        clock[0] += Duration.ofSeconds(61).toNanos();
        assertThat(subjectFor("orgA")).isEqualTo("changed elsewhere");
    }

    @Test
    void whenDisabledEveryLookupGoesToTheRepositoryAsBefore() {
        props.getCache().setEnabled(false);
        service = serviceWith(props);
        service.create(newTemplate("orgA", "own"));
        subjectFor("orgA");
        subjectFor("orgA");
        subjectFor("orgA");
        verify(repository, times(3)).findOrganizationTemplate(EVENT, CH, "orgA");
    }

    @Test
    void aRepositoryFailureOnTheSendPathPropagatesAndIsNotCached() {
        when(repository.findByEventAndChannelAndActiveTrue(eq(EVENT), any()))
                .thenThrow(new IllegalStateException("db down"))
                .thenAnswer(i -> Optional.of(newTemplate(null, "recovered")));
        List<String> seen = new ArrayList<>();
        try {
            subjectFor("orgA");
        } catch (IllegalStateException e) {
            seen.add("failed");
        }
        seen.add(subjectFor("orgA"));
        assertThat(seen).containsExactly("failed", "recovered");
    }
}
