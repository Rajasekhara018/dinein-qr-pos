package com.heuristiq.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** The franchise welcome ships as an email and an SMS, and only uses placeholders the event provides. */
class FranchiseWelcomeTemplatesTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\w+}}");
    private static final String EVENT = NotificationEvent.FRANCHISE_WELCOME.name();

    private static Set<String> placeholders(String text) {
        Matcher matcher = PLACEHOLDER.matcher(text == null ? "" : text);
        Set<String> found = new TreeSet<>();
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }

    private static List<DefaultNotificationTemplates.Seed> seeds() {
        return DefaultNotificationTemplates.DEFAULTS.stream().filter(seed -> seed.event().equals(EVENT)).toList();
    }

    @Test
    void thereIsOneEmailAndOneSmsTemplate() {
        assertThat(seeds()).extracting(DefaultNotificationTemplates.Seed::channel)
                .containsExactlyInAnyOrder(NotificationChannel.EMAIL, NotificationChannel.SMS);
    }

    @Test
    void theEmailNamesTheFranchiseTheApplicationsAndHowToSignIn() {
        DefaultNotificationTemplates.Seed email = seeds().stream().filter(s -> s.channel() == NotificationChannel.EMAIL).findFirst().orElseThrow();

        assertThat(email.subject()).isNotBlank().contains("{{franchiseCode}}");
        assertThat(email.body()).contains("{{personName}}", "{{roleLabel}}", "{{franchiseCode}}", "{{applications}}", "{{signInDetails}}");
        assertThat(email.body()).as("a button and a plain link to the sign-in page").contains("href='{{portalUrl}}'").contains("Open the sign-in page");
    }

    @Test
    void everyPlaceholderUsedIsOneTheEventProvides() {
        List<String> allowed = EventAttributesConfig.forEvent(EVENT);

        assertThat(allowed).containsExactlyInAnyOrder("{{personName}}", "{{roleLabel}}", "{{franchiseCode}}", "{{applications}}",
                "{{portalUrl}}", "{{signInDetails}}", "{{organizationName}}");
        for (DefaultNotificationTemplates.Seed seed : seeds()) {
            assertThat(allowed).as(seed.channel().name()).containsAll(placeholders(seed.body() + " " + seed.subject()));
        }
    }

    @Test
    void theRequestForMoreAgentsIsAnEmailWhoseEveryPlaceholderTheEventProvides() {
        String event = NotificationEvent.FRANCHISE_AGENT_REQUEST.name();
        List<DefaultNotificationTemplates.Seed> request = DefaultNotificationTemplates.DEFAULTS.stream().filter(seed -> seed.event().equals(event)).toList();

        assertThat(request).hasSize(1);
        assertThat(request.get(0).channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(EventAttributesConfig.forEvent(event)).containsAll(placeholders(request.get(0).body() + " " + request.get(0).subject()));
        assertThat(request.get(0).body()).contains("{{ownerName}}", "{{franchiseCode}}", "{{applicationName}}", "{{requestedAgents}}", "{{currentLimit}}");
    }

    @Test
    void theDemandAlertIsAnEmailNamingThePlaceTheApplicationAndTheVotes() {
        String event = NotificationEvent.FRANCHISE_DEMAND_THRESHOLD.name();
        List<DefaultNotificationTemplates.Seed> alert = DefaultNotificationTemplates.DEFAULTS.stream().filter(seed -> seed.event().equals(event)).toList();

        assertThat(alert).singleElement().extracting(DefaultNotificationTemplates.Seed::channel).isEqualTo(NotificationChannel.EMAIL);
        DefaultNotificationTemplates.Seed email = alert.get(0);
        assertThat(email.subject()).contains("{{votes}}", "{{applicationName}}", "{{placeName}}");
        assertThat(email.body()).contains("{{placeCode}}", "{{windowDays}}", "{{threshold}}", "href='{{portalUrl}}'");
        assertThat(EventAttributesConfig.forEvent(event)).containsAll(placeholders(email.body() + " " + email.subject()));
    }

    @Test
    void theSmsCarriesNoSignInDetailsSoNoUsernameTravelsByText() {
        DefaultNotificationTemplates.Seed sms = seeds().stream().filter(s -> s.channel() == NotificationChannel.SMS).findFirst().orElseThrow();

        assertThat(sms.body()).doesNotContain("{{signInDetails}}").contains("sent to your email");
    }
}
