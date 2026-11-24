package com.heuristiq.notification.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.model.NotificationTemplate;
import com.heuristiq.notification.domain.model.RideLifecycleTemplates;
import com.heuristiq.notification.domain.repository.ITemplateRepository;
import com.heuristiq.notification.infrastructure.config.NmsProperties;

/** The ride lifecycle defaults are created once per event and channel, are shared and active, and never replace an admin's template. */
class TemplateRideDefaultsTest {

    private ITemplateRepository repository;
    private TemplateService service;

    @BeforeEach
    void setUp() {
        repository = mock(ITemplateRepository.class);
        NmsProperties properties = new NmsProperties();
        properties.getBranding().setName("Realboom");
        properties.getCache().setEnabled(false);
        service = new TemplateService(repository, properties,
                new com.heuristiq.notification.infrastructure.cache.CaffeineTemplateLookupCache(properties, com.github.benmanes.caffeine.cache.Ticker.systemTicker(), null));
    }

    @SuppressWarnings("unchecked")
    private List<NotificationTemplate> savedByTheFirstRun() {
        ArgumentCaptor<List<NotificationTemplate>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        return saved.getValue();
    }

    @Test
    void aFreshDatabaseGetsEveryRideDefaultSharedAndActive() {
        when(repository.existsByEventAndChannel(anyString(), any())).thenReturn(false);

        service.seedDefaultsIfMissing();

        List<NotificationTemplate> saved = savedByTheFirstRun();
        for (var seed : RideLifecycleTemplates.seeds()) {
            assertThat(saved).as(seed.event() + " " + seed.channel()).anySatisfy(t -> {
                assertThat(t.getEvent()).isEqualTo(seed.event());
                assertThat(t.getChannel()).isEqualTo(seed.channel());
                assertThat(t.isActive()).isTrue();
                assertThat(t.getTenantId()).isNull();
                assertThat(new String(Base64.getDecoder().decode(t.getBody()), StandardCharsets.UTF_8)).doesNotContain("%BRAND_NAME%");
            });
        }
    }

    @Test
    void aSecondRunCreatesNothingBecauseEveryPairAlreadyExists() {
        when(repository.existsByEventAndChannel(anyString(), any())).thenReturn(true);

        service.seedDefaultsIfMissing();
        service.seedDefaultsIfMissing();

        verify(repository, never()).saveAll(any());
    }

    @Test
    void onlyAMissingPairIsCreated() {
        when(repository.existsByEventAndChannel(anyString(), any())).thenReturn(true);
        when(repository.existsByEventAndChannel("DRIVER_ARRIVED", NotificationChannel.SMS)).thenReturn(false);

        service.seedDefaultsIfMissing();

        List<NotificationTemplate> saved = savedByTheFirstRun();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getEvent()).isEqualTo("DRIVER_ARRIVED");
        assertThat(saved.get(0).getChannel()).isEqualTo(NotificationChannel.SMS);
    }

    @Test
    void aTemplateAnAdminEditedIsLeftExactlyAsItIs() {
        when(repository.existsByEventAndChannel(anyString(), any())).thenReturn(true);
        NotificationTemplate edited = new NotificationTemplate();
        edited.setEvent("BOOKING_CONFIRMED");
        edited.setChannel(NotificationChannel.IN_APP);
        edited.setSubject("Your cab is booked");
        edited.setBody(Base64.getEncoder().encodeToString("Custom wording {{bookingId}}".getBytes(StandardCharsets.UTF_8)));
        when(repository.findByEventAndChannelAndActiveTrue("BOOKING_CONFIRMED", NotificationChannel.IN_APP)).thenReturn(Optional.of(edited));

        service.seedDefaultsIfMissing();

        verify(repository, never()).saveAll(any());
        assertThat(edited.getSubject()).isEqualTo("Your cab is booked");
        assertThat(new String(Base64.getDecoder().decode(edited.getBody()), StandardCharsets.UTF_8)).isEqualTo("Custom wording {{bookingId}}");
    }
}
