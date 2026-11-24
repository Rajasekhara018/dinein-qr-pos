package com.heuristiq.notification.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.heuristiq.notification.application.dto.OrganizationProfile;
import com.heuristiq.notification.application.security.NmsPrincipal;
import com.heuristiq.notification.application.usecase.support.OrganizationVariables;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.model.NotificationTemplate;
import com.heuristiq.notification.domain.repository.ITemplateRepository;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.domain.repository.PageResult;
import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.notification.shared.NmsNotFoundException;

class OrganizationTemplateTest {

    private ITemplateRepository repository;
    private TemplateService service;

    @BeforeEach
    void setUp() {
        repository = mock(ITemplateRepository.class);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        NmsProperties props = new NmsProperties();
        props.getCache().setEnabled(false);
        service = new TemplateService(repository, props, new com.heuristiq.notification.infrastructure.cache.CaffeineTemplateLookupCache(props, com.github.benmanes.caffeine.cache.Ticker.systemTicker(), null));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void callerOf(String tenantId, boolean internal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new NmsPrincipal(internal ? "internal-service" : "u-1", tenantId, internal), null, List.of()));
    }

    private NotificationTemplate template(String id, String tenantId) {
        NotificationTemplate t = new NotificationTemplate();
        t.setId(id);
        t.setEvent("OTP_SENT");
        t.setChannel(NotificationChannel.SMS);
        t.setName("OTP");
        t.setBody("Ym9keQ==");
        t.setTenantId(tenantId);
        return t;
    }

    @Test
    void anOrganizationUsesItsOwnTemplateWhenItHasOne() {
        NotificationTemplate own = template("own", "acme");
        when(repository.findOrganizationTemplate("OTP_SENT", NotificationChannel.SMS, "acme")).thenReturn(Optional.of(own));

        assertThat(service.findForTenant("otp_sent", NotificationChannel.SMS, "acme")).containsSame(own);
    }

    @Test
    void anOrganizationWithoutItsOwnTemplateGetsTheSharedOne() {
        NotificationTemplate shared = template("shared", null);
        when(repository.findOrganizationTemplate(any(), any(), any())).thenReturn(Optional.empty());
        when(repository.findByEventAndChannelAndActiveTrue("OTP_SENT", NotificationChannel.SMS)).thenReturn(Optional.of(shared));

        assertThat(service.findForTenant("OTP_SENT", NotificationChannel.SMS, "acme")).containsSame(shared);
        assertThat(service.findForTenant("OTP_SENT", NotificationChannel.SMS, null)).containsSame(shared);
        verify(repository, never()).findOrganizationTemplate("OTP_SENT", NotificationChannel.SMS, null);
    }

    @Test
    void aPersonCreatesATemplateOnlyForTheirOwnOrganization() {
        callerOf("acme", false);
        NotificationTemplate request = template(null, "acme");

        assertThat(service.create(request).getTenantId()).isEqualTo("acme");

        assertThatThrownBy(() -> service.create(template(null, "globex"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTemplateWithNoOrganizationIsShared() {
        callerOf("acme", false);

        assertThat(service.create(template(null, null)).getTenantId()).isNull();
    }

    @Test
    void anOrganizationCannotCreateASecondTemplateForTheSameEventAndChannel() {
        callerOf("acme", false);
        when(repository.existsOrganizationTemplate("OTP_SENT", NotificationChannel.SMS, "acme")).thenReturn(true);

        assertThatThrownBy(() -> service.create(template(null, "acme"))).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void anotherOrganizationsTemplateIsInvisibleAndCannotBeChangedOrRemoved() {
        callerOf("acme", false);
        NotificationTemplate theirs = template("t1", "globex");
        when(repository.findById("t1")).thenReturn(Optional.of(theirs));

        assertThat(service.findById("t1")).isEmpty();
        assertThatThrownBy(() -> service.update("t1", template("t1", "globex"))).isInstanceOf(NmsNotFoundException.class);
        service.delete("t1");
        verify(repository, never()).save(any());
    }

    @Test
    void anUpdateNeverMovesATemplateToAnotherOrganization() {
        callerOf("acme", false);
        NotificationTemplate own = template("t1", "acme");
        when(repository.findById("t1")).thenReturn(Optional.of(own));

        assertThat(service.update("t1", template("t1", "globex")).getTenantId()).isEqualTo("acme");
    }

    @Test
    void editingASharedTemplateGivesTheOrganizationItsOwnCopyAndLeavesTheSharedOneAlone() {
        callerOf("acme", false);
        NotificationTemplate shared = template("shared", null);
        when(repository.findById("shared")).thenReturn(Optional.of(shared));
        NotificationTemplate edit = template("shared", null);
        edit.setSubject("Acme OTP");

        NotificationTemplate saved = service.update("shared", edit);

        assertThat(saved).isNotSameAs(shared);
        assertThat(saved.getId()).isNull();
        assertThat(saved.getTenantId()).isEqualTo("acme");
        assertThat(saved.getSubject()).isEqualTo("Acme OTP");
        assertThat(saved.getEvent()).isEqualTo("OTP_SENT");
        assertThat(shared.getSubject()).as("the shared template other organizations use is unchanged").isNull();
        verify(repository, never()).save(shared);
    }

    @Test
    void anOrganizationThatAlreadyHasItsOwnVersionMustEditThatOne() {
        callerOf("acme", false);
        when(repository.findById("shared")).thenReturn(Optional.of(template("shared", null)));
        when(repository.existsOrganizationTemplate("OTP_SENT", NotificationChannel.SMS, "acme")).thenReturn(true);

        assertThatThrownBy(() -> service.update("shared", template("shared", null))).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void anOrganizationCannotSwitchOffASharedTemplate() {
        callerOf("acme", false);
        NotificationTemplate shared = template("shared", null);
        when(repository.findById("shared")).thenReturn(Optional.of(shared));

        assertThatThrownBy(() -> service.delete("shared")).isInstanceOf(IllegalArgumentException.class);
        assertThat(shared.isActive()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void thePlatformStillEditsAndSwitchesOffSharedTemplates() {
        callerOf(null, true);
        NotificationTemplate shared = template("shared", null);
        when(repository.findById("shared")).thenReturn(Optional.of(shared));
        NotificationTemplate edit = template("shared", null);
        edit.setSubject("New");

        assertThat(service.update("shared", edit)).isSameAs(shared);
        assertThat(shared.getSubject()).isEqualTo("New");
        service.delete("shared");
        assertThat(shared.isActive()).isFalse();
    }

    private NotificationTemplate template(String id, String tenantId, String event, NotificationChannel channel, boolean active) {
        NotificationTemplate t = template(id, tenantId);
        t.setEvent(event);
        t.setChannel(channel);
        t.setActive(active);
        return t;
    }

    @Test
    void anOrganizationOpeningTheTemplatesGetsItsOwnCopyOfEverySharedTemplateItLacks() {
        callerOf("acme", false);
        when(repository.findOrganizationTemplates("acme")).thenReturn(List.of(
                template("own", "acme", "OTP_SENT", NotificationChannel.SMS, true),
                template("off", "acme", "PAYMENT_FAILED", NotificationChannel.EMAIL, false)));
        when(repository.findSharedActive()).thenReturn(List.of(
                template("s1", null, "OTP_SENT", NotificationChannel.SMS, true),
                template("s2", null, "PAYMENT_FAILED", NotificationChannel.EMAIL, true),
                template("s3", null, "PAYMENT_SUCCESS", NotificationChannel.EMAIL, true)));
        PageResult<NotificationTemplate> page = new PageResult<>(List.of(), 0, 20, 0);
        when(repository.findOrganizationActive(any(), any())).thenReturn(page);

        assertThat(service.findAll(new PageQuery(0, 20))).isSameAs(page);

        ArgumentCaptor<NotificationTemplate> saved = ArgumentCaptor.forClass(NotificationTemplate.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEvent()).as("only the one it lacks; its own and the one it switched off are kept").isEqualTo("PAYMENT_SUCCESS");
        assertThat(saved.getValue().getTenantId()).isEqualTo("acme");
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().isActive()).isTrue();
        verify(repository).findOrganizationActive(org.mockito.ArgumentMatchers.eq("acme"), any());
        verify(repository, never()).findByActiveTrue(any(PageQuery.class));
    }

    @Test
    void aCopyThatAnotherRequestMadeFirstIsSkipped() {
        callerOf("acme", false);
        when(repository.findOrganizationTemplates("acme")).thenReturn(List.of());
        when(repository.findSharedActive()).thenReturn(List.of(template("s1", null, "OTP_SENT", NotificationChannel.SMS, true)));
        when(repository.save(any())).thenThrow(new IllegalStateException("duplicate key"));

        service.ensureOrganizationCopies("acme");

        verify(repository).save(any());
    }

    @Test
    void thePlatformStillListsWhatItsScopeAllowsAndCopiesNothing() {
        callerOf(null, true);
        PageResult<NotificationTemplate> page = new PageResult<>(List.of(), 0, 20, 0);
        when(repository.findByActiveTrue(any(PageQuery.class))).thenReturn(page);

        assertThat(service.findAll(new PageQuery(0, 20))).isSameAs(page);
        verify(repository, never()).findSharedActive();
        verify(repository, never()).save(any());
    }

    @Test
    void messagesCarryTheSendingOrganizationsName() {
        OrganizationVariables variables = new OrganizationVariables(
                id -> "acme".equals(id) ? Optional.of(new OrganizationProfile("Acme Corp", "ACME", "help@acme.com", null)) : Optional.empty(),
                "RealBoom");

        Map<String, String> merged = variables.merge("acme", Map.of("otp", "123456", "organizationName", "Override"));

        assertThat(merged).containsEntry("otp", "123456").containsEntry("organizationCode", "ACME")
                .containsEntry("organizationSupportEmail", "help@acme.com").containsEntry("organizationSupportPhone", "")
                .as("a value the caller sends wins").containsEntry("organizationName", "Override");
        assertThat(variables.merge("acme", null)).containsEntry("organizationName", "Acme Corp");
    }

    @Test
    void withNoOrganizationOrNoAnswerTheMessageUsesThePlatformName() {
        OrganizationVariables variables = new OrganizationVariables(id -> Optional.empty(), "RealBoom");

        assertThat(variables.merge(null, null)).containsEntry("organizationName", "RealBoom");
        assertThat(variables.merge("unknown", Map.of())).containsEntry("organizationName", "RealBoom");
    }
}
