package com.railcab.nms.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.railcab.nms.domain.model.NotificationTemplate;
import com.railcab.nms.domain.repository.ITemplateRepository;
import com.railcab.nms.infrastructure.config.NmsProperties;

class TemplateSeedBrandingTest {

    @Test
    void seededTemplatesUseTheConfiguredBrand() {
        ITemplateRepository repository = mock(ITemplateRepository.class);
        when(repository.existsByEventAndChannel(anyString(), any())).thenReturn(false);
        NmsProperties properties = new NmsProperties();
        properties.getBranding().setName("Realboom");
        properties.getBranding().setMatrimonyName("Acme Matrimony");
        properties.getBranding().setSupportEmail("support@realboom.in");
        properties.getBranding().setPrimaryColor("#111111");
        properties.getBranding().setTagline("Acme");
        properties.getBranding().setAccentColor("#222222");

        properties.getCache().setEnabled(false);
        new TemplateService(repository, properties, new com.railcab.nms.infrastructure.cache.CaffeineTemplateLookupCache(properties, com.github.benmanes.caffeine.cache.Ticker.systemTicker(), null)).seedDefaultsIfMissing();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationTemplate>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).isNotEmpty().allSatisfy(t -> {
            String body = new String(Base64.getDecoder().decode(t.getBody()), StandardCharsets.UTF_8);
            assertThat(t.getSubject() == null ? "" : t.getSubject()).doesNotContain("RailCab");
            assertThat(body).doesNotContain("RailCab").doesNotContain("support@railcab.in")
                    .doesNotContain("%BRAND_").doesNotContain("%COLOR_").doesNotContain("Ride Platform").doesNotContain("%MATRIMONY_").doesNotContain("%SUPPORT_").doesNotContain("#0b5fff").doesNotContain("#0b6e4f");
        });
    }
}
