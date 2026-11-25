package com.heuristiq.identity.organization.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.heuristiq.identity.application.InMemoryTenantApplicationRepository;
import com.heuristiq.identity.application.repository.ApplicationRepository;
import com.heuristiq.identity.common.GlobalExceptionHandler;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.enums.UamEnums.TenantStatus;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.organization.subscription.TenantApplication;
import com.heuristiq.identity.organization.subscription.TenantApplicationService;
import com.heuristiq.identity.organization.subscription.TenantApplicationStatus;

/** Which organizations an applicant may choose: ACTIVE + subscribed (today) + opted in; code and name only. */
class SelfSignupOrganizationTest {

    private static final String APP = "MATRIMONY_APP";

    private TenantRepository tenants;
    private InMemoryTenantApplicationRepository links;
    private SelfSignupOrganizationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        tenants = mock(TenantRepository.class);
        links = new InMemoryTenantApplicationRepository();
        service = new SelfSignupOrganizationService(tenants, links,
                new TenantApplicationService(tenants, mock(ApplicationRepository.class), links));
        mvc = MockMvcBuilders.standaloneSetup(new PublicSelfSignupOrganizationController(service),
                        new InternalSelfSignupOrganizationController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private void org(String id, String code, String name, TenantStatus status, boolean open, String app,
                     TenantApplicationStatus linkStatus, LocalDate validUntil) {
        Map<String, Object> settings = open ? Map.of("openForSelfSignup", true) : Map.of("dataScope", "ORGANIZATION");
        when(tenants.findById(id)).thenReturn(Optional.of(
                Tenant.builder().id(id).code(code).name(name).status(status).settings(settings).build()));
        links.save(TenantApplication.builder().tenantId(id).applicationCode(app).status(linkStatus).validUntil(validUntil).build());
    }

    private void eligible(String id, String code, String name) {
        org(id, code, name, TenantStatus.ACTIVE, true, APP, TenantApplicationStatus.ACTIVE, null);
    }

    @Test
    void listsOnlyEligibleOrganizationsSortedByNameWithCodeAndNameOnly() throws Exception {
        eligible("t-b", "BETA", "beta org");
        eligible("t-a", "ALPHA", "Alpha Org");
        org("t-opt", "NOOPT", "Not open", TenantStatus.ACTIVE, false, APP, TenantApplicationStatus.ACTIVE, null);
        org("t-sus", "SUSP", "Suspended", TenantStatus.SUSPENDED, true, APP, TenantApplicationStatus.ACTIVE, null);
        org("t-can", "CANC", "Cancelled", TenantStatus.ACTIVE, true, APP, TenantApplicationStatus.CANCELLED, null);
        org("t-exp", "EXPD", "Expired", TenantStatus.ACTIVE, true, APP, TenantApplicationStatus.ACTIVE, LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1));
        org("t-oth", "OTHER", "Other app", TenantStatus.ACTIVE, true, "TRAIN_APP", TenantApplicationStatus.ACTIVE, null);

        mvc.perform(get("/api/v1/uam/identity/auth/signup-organizations").param("applicationCode", "matrimony_app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.length()").value(2))
                .andExpect(jsonPath("$.responseObject[0].code").value("ALPHA"))
                .andExpect(jsonPath("$.responseObject[0].name").value("Alpha Org"))
                .andExpect(jsonPath("$.responseObject[1].code").value("BETA"))
                .andExpect(jsonPath("$.responseObject[0].id").doesNotExist())
                .andExpect(jsonPath("$.responseObject[0].organizationId").doesNotExist())
                .andExpect(jsonPath("$.responseObject[0].status").doesNotExist())
                .andExpect(jsonPath("$.responseObject[0].settings").doesNotExist());
    }

    @Test
    void anExpiredEndDateTodayStillCountsAsActive() {
        org("t1", "TODAY", "Today", TenantStatus.ACTIVE, true, APP, TenantApplicationStatus.ACTIVE, LocalDate.now(java.time.ZoneOffset.UTC));

        assertThat(service.list(new SelfSignupOrganizationServiceUseCase.ListInput(APP)).organizations())
                .extracting(SelfSignupOrganization::code).containsExactly("TODAY");
    }

    @Test
    void unknownMissingOrMalformedApplicationCodeGivesAnEmptyListNotAnError() throws Exception {
        eligible("t1", "ALPHA", "Alpha");

        for (String code : new String[] {"NO_SUCH_APP", "bad code!", "x'; drop", ""}) {
            mvc.perform(get("/api/v1/uam/identity/auth/signup-organizations").param("applicationCode", code))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.responseObject.length()").value(0));
        }
        mvc.perform(get("/api/v1/uam/identity/auth/signup-organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.length()").value(0));
    }

    @Test
    void theListIsCapped() {
        for (int i = 0; i < SelfSignupConstants.MAX_PUBLIC_ORGANIZATIONS + 5; i++) {
            eligible("t" + i, "ORG" + i, String.format("Org %04d", i));
        }

        assertThat(service.list(new SelfSignupOrganizationServiceUseCase.ListInput(APP)).organizations())
                .hasSize(SelfSignupConstants.MAX_PUBLIC_ORGANIZATIONS);
    }

    @Test
    void aMissingNameFallsBackToTheCode() {
        eligible("t1", "NONAME", null);

        assertThat(service.list(new SelfSignupOrganizationServiceUseCase.ListInput(APP)).organizations())
                .containsExactly(new SelfSignupOrganization("NONAME", "NONAME"));
    }

    @Test
    void resolveReturnsTheIdOfAnEligibleOrganizationOnly() throws Exception {
        eligible("t1", "ALPHA", "Alpha");

        assertThat(service.resolve(new SelfSignupOrganizationServiceUseCase.ResolveInput(APP, " alpha ")).organization())
                .isEqualTo(new ResolvedSelfSignupOrganization("t1", "ALPHA", "Alpha"));
        mvc.perform(get("/api/v1/uam/internal/self-signup-organizations/resolve")
                        .param("applicationCode", APP).param("organizationCode", "ALPHA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.organizationId").value("t1"));
    }

    @Test
    void internalListCarriesIdsForTheSameEligibleOrganizationsOnly() throws Exception {
        eligible("t-b", "BETA", "Beta");
        eligible("t-a", "ALPHA", "Alpha");
        org("t-opt", "NOOPT", "n", TenantStatus.ACTIVE, false, APP, TenantApplicationStatus.ACTIVE, null);

        mvc.perform(get("/api/v1/uam/internal/self-signup-organizations").param("applicationCode", APP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.length()").value(2))
                .andExpect(jsonPath("$.responseObject[0].organizationId").value("t-a"))
                .andExpect(jsonPath("$.responseObject[1].organizationId").value("t-b"));
        mvc.perform(get("/api/v1/uam/internal/self-signup-organizations").param("applicationCode", "bad code!"))
                .andExpect(jsonPath("$.responseObject.length()").value(0));
    }

    @Test
    void resolveRefusesEveryIneligibleOrganizationWithTheSameNotFound() throws Exception {
        org("t-opt", "NOOPT", "n", TenantStatus.ACTIVE, false, APP, TenantApplicationStatus.ACTIVE, null);
        org("t-sus", "SUSP", "s", TenantStatus.SUSPENDED, true, APP, TenantApplicationStatus.ACTIVE, null);
        org("t-uns", "UNSUB", "u", TenantStatus.ACTIVE, true, "TRAIN_APP", TenantApplicationStatus.ACTIVE, null);

        for (String code : new String[] {"NOOPT", "SUSP", "UNSUB", "MISSING", "bad code!"}) {
            assertThatThrownBy(() -> service.resolve(new SelfSignupOrganizationServiceUseCase.ResolveInput(APP, code)))
                    .isInstanceOfSatisfying(UamException.class, e -> {
                        assertThat(e.getStatus().value()).isEqualTo(404);
                        assertThat(e.getMessage()).isEqualTo(SelfSignupConstants.MSG_NOT_AVAILABLE);
                    });
        }
        assertThatThrownBy(() -> service.resolve(new SelfSignupOrganizationServiceUseCase.ResolveInput(null, "NOOPT")))
                .isInstanceOf(UamException.class);
        mvc.perform(get("/api/v1/uam/internal/self-signup-organizations/resolve")
                        .param("applicationCode", APP).param("organizationCode", "NOOPT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherApplicationsOrganizationIsNeverResolvedForThisOne() {
        org("t1", "ALPHA", "Alpha", TenantStatus.ACTIVE, true, "TRAIN_APP", TenantApplicationStatus.ACTIVE, null);

        assertThatThrownBy(() -> service.resolve(new SelfSignupOrganizationServiceUseCase.ResolveInput(APP, "ALPHA")))
                .isInstanceOf(UamException.class);
    }
}
