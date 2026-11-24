package com.railcab.nms.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The matrimony onboarding events ship ready-made on every channel and only use placeholders their event provides. */
class MatrimonyOnboardingTemplatesTest {
    private static final List<NotificationEvent> EVENTS = List.of(
            NotificationEvent.MATRIMONY_ONBOARDING_RECEIVED, NotificationEvent.MATRIMONY_ONBOARDING_RETURNED,
            NotificationEvent.MATRIMONY_ONBOARDING_APPROVED, NotificationEvent.MATRIMONY_ONBOARDING_REJECTED,
            NotificationEvent.MATRIMONY_ACCOUNT_ACTIVATED, NotificationEvent.MATRIMONY_NOTIFICATION);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\w+}}");

    @Test
    void everyEventHasInAppPushEmailAndSmsContent() {
        for (NotificationEvent event : EVENTS) {
            Set<NotificationChannel> channels = DefaultNotificationTemplates.DEFAULTS.stream()
                    .filter(seed -> seed.event().equals(event.name()))
                    .map(DefaultNotificationTemplates.Seed::channel)
                    .collect(Collectors.toSet());
            assertThat(channels).as(event.name())
                    .containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.FCM_PUSH,
                            NotificationChannel.EMAIL, NotificationChannel.SMS);
        }
    }

    @Test
    void seedsOnlyUsePlaceholdersTheEventProvides() {
        for (NotificationEvent event : EVENTS) {
            List<String> allowed = EventAttributesConfig.forEvent(event.name());
            assertThat(allowed).as(event.name()).isNotEmpty();
            for (DefaultNotificationTemplates.Seed seed : DefaultNotificationTemplates.DEFAULTS) {
                if (!seed.event().equals(event.name())) {
                    continue;
                }
                Matcher matcher = PLACEHOLDER.matcher(seed.body() + " " + (seed.subject() == null ? "" : seed.subject()));
                while (matcher.find()) {
                    assertThat(allowed).as(seed.name()).contains(matcher.group());
                }
            }
        }
    }

    @Test
    void theExistingAccountNoticeIsAnEmailThatCarriesNoCodeOrPassword() {
        List<DefaultNotificationTemplates.Seed> seeds = DefaultNotificationTemplates.DEFAULTS.stream()
                .filter(seed -> seed.event().equals(NotificationEvent.MATRIMONY_SELF_REGISTER_EXISTING_ACCOUNT.name()))
                .toList();

        assertThat(seeds).hasSize(1);
        assertThat(seeds.get(0).channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(seeds.get(0).body()).contains("{{email}}").doesNotContain("{{otp}}").doesNotContain("assword}}");
        assertThat(EventAttributesConfig.forEvent("MATRIMONY_SELF_REGISTER_EXISTING_ACCOUNT")).containsExactly("{{email}}");
    }

    @Test
    void returnedAndRejectedExplainWhy() {
        for (NotificationEvent event : List.of(NotificationEvent.MATRIMONY_ONBOARDING_RETURNED,
                NotificationEvent.MATRIMONY_ONBOARDING_REJECTED)) {
            DefaultNotificationTemplates.DEFAULTS.stream()
                    .filter(seed -> seed.event().equals(event.name()))
                    .forEach(seed -> assertThat(seed.body()).as(seed.name()).contains("{{reason}}"));
        }
    }

    @Test
    void theRiderOnboardingTemplatesAreUntouched() {
        long riderSeeds = DefaultNotificationTemplates.DEFAULTS.stream()
                .filter(seed -> seed.event().equals(NotificationEvent.ONBOARDING_RECEIVED.name()))
                .count();
        assertThat(riderSeeds).isEqualTo(5);
    }
}
