package com.heuristiq.uam.organization.operatingmodel.adapter.in;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.GlobalExceptionHandler;
import com.heuristiq.uam.common.UamCommonConstants;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.operatingmodel.adapter.out.TenantOrganizationDirectory;
import com.heuristiq.uam.organization.operatingmodel.application.OperatingModelService;
import com.heuristiq.uam.organization.operatingmodel.application.port.OperatingModelAudit;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModel;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelConstants;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelPeriod;
import com.heuristiq.uam.organization.operatingmodel.support.InMemoryOperatingModelScheduleStore;
import java.time.LocalDate;
import java.util.List;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.security.TenantContext;
import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The operating-model endpoints: shape of the answers, validation, and that one organization cannot reach another's. */
class OperatingModelControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T06:00:00Z"), ZoneOffset.UTC);

    private final Map<String, Tenant> rows = new HashMap<>();
    private InMemoryOperatingModelScheduleStore store;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TenantRepository tenants = mock(TenantRepository.class);
        when(tenants.findById(any())).thenAnswer(i -> Optional.ofNullable(rows.get(i.<String>getArgument(0))));
        rows.put("acme", Tenant.builder().id("acme").code("ACME").build());
        rows.put("globex", Tenant.builder().id("globex").code("GLOBEX").build());
        store = new InMemoryOperatingModelScheduleStore();
        OperatingModelService service = new OperatingModelService(new TenantOrganizationDirectory(tenants), store,
                mock(OperatingModelAudit.class), CLOCK, ZoneId.of("Asia/Kolkata"));
        CommonRequestMapper mapper = new CommonRequestMapper(JsonMapper.builder().findAndAddModules().build(),
                Validation.buildDefaultValidatorFactory().getValidator());
        mvc = MockMvcBuilders.standaloneSetup(new OperatingModelController(service, mapper), new InternalOperatingModelController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(String tenantId, String... authorities) {
        TenantContext.set(tenantId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin-1", "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private static String body(String changesJson) {
        return "{\"requestObject\":{\"scheduledChanges\":" + changesJson + "}}";
    }

    @Test
    void anOrganizationsStaffSeeItsModel() throws Exception {
        signInAs("acme");

        mvc.perform(get("/api/v1/uam/organizations/acme/operating-model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.organizationId").value("acme"))
                .andExpect(jsonPath("$.responseObject.currentModel").value("DIRECT"))
                .andExpect(jsonPath("$.responseObject.schedule").isEmpty());
    }

    @Test
    void schedulingASwitchAnswersTheNewStateAndTheLookupSeesIt() throws Exception {
        signInAs("acme");

        mvc.perform(put("/api/v1/uam/organizations/acme/operating-model").contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"model\":\"FRANCHISE\",\"effectiveFrom\":\"2026-11-08\"},"
                                + "{\"model\":\"DIRECT\",\"effectiveFrom\":\"2027-01-01\"}]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.currentModel").value("FRANCHISE"))
                .andExpect(jsonPath("$.responseObject.schedule.length()").value(2))
                .andExpect(jsonPath("$.responseObject.schedule[0].inForce").value(true))
                .andExpect(jsonPath("$.responseObject.schedule[1].inForce").value(false));

        mvc.perform(get("/api/v1/uam/internal/organizations/acme/operating-model").param("date", "2027-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.model").value("DIRECT"));
        mvc.perform(get("/api/v1/uam/internal/organizations/acme/operating-model"))
                .andExpect(jsonPath("$.responseObject.model").value("FRANCHISE"))
                .andExpect(jsonPath("$.responseObject.date").value("2026-11-08"));
    }

    @Test
    void aHybridOrganizationIsAnsweredAsHybridByTheInternalLookup() throws Exception {
        signInAs("acme");

        mvc.perform(put("/api/v1/uam/organizations/acme/operating-model").contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"model\":\"HYBRID\",\"effectiveFrom\":\"2026-11-08\"},"
                                + "{\"model\":\"FRANCHISE\",\"effectiveFrom\":\"2027-01-01\"}]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.currentModel").value("HYBRID"));

        mvc.perform(get("/api/v1/uam/internal/organizations/acme/operating-model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.model").value("HYBRID"));
        mvc.perform(get("/api/v1/uam/internal/organizations/acme/operating-model").param("date", "2027-01-01"))
                .andExpect(jsonPath("$.responseObject.model").value("FRANCHISE"));

        // Replacing the scheduled change by HYBRID while HYBRID is already in force is a conflict.
        mvc.perform(put("/api/v1/uam/organizations/acme/operating-model").contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"model\":\"HYBRID\",\"effectiveFrom\":\"2026-12-01\"}]")))
                .andExpect(status().isConflict());
    }

    @Test
    void aChangeInThePastIsRefused() throws Exception {
        signInAs("acme");

        mvc.perform(put("/api/v1/uam/organizations/acme/operating-model").contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"model\":\"FRANCHISE\",\"effectiveFrom\":\"2026-11-01\"}]")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aSwitchToTheModelInForceIsAConflict() throws Exception {
        signInAs("acme");

        mvc.perform(put("/api/v1/uam/organizations/acme/operating-model").contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"model\":\"DIRECT\",\"effectiveFrom\":\"2026-12-01\"}]")))
                .andExpect(status().isConflict());
    }

    @Test
    void anUnknownModelOrMissingFieldsAreValidationErrors() throws Exception {
        signInAs("acme");

        mvc.perform(put("/api/v1/uam/organizations/acme/operating-model").contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"model\":\"PARTNER\",\"effectiveFrom\":\"2026-12-01\"}]")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/uam/organizations/acme/operating-model").contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"model\":\"FRANCHISE\"}]")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/uam/organizations/acme/operating-model").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestObject\":{}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oneOrganizationCannotReadOrChangeAnothersModel() throws Exception {
        signInAs("acme");

        mvc.perform(get("/api/v1/uam/organizations/globex/operating-model"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(OperatingModelConstants.ERROR_ORGANIZATION_NOT_FOUND));
        mvc.perform(put("/api/v1/uam/organizations/globex/operating-model").contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"model\":\"FRANCHISE\",\"effectiveFrom\":\"2026-12-01\"}]")))
                .andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(store.find("globex")).isEmpty();
    }

    @Test
    void aSignedInPersonWhoseTokenNamesNoOrganizationReachesNone() throws Exception {
        signInAs(null);

        mvc.perform(get("/api/v1/uam/organizations/acme/operating-model"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/uam/organizations/acme/operating-model").contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"model\":\"FRANCHISE\",\"effectiveFrom\":\"2026-12-01\"}]")))
                .andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(store.find("acme")).isEmpty();
    }

    @Test
    void aScheduleChangedByAnotherAdminMeanwhileIsAConflict() throws Exception {
        signInAs("acme");
        // What the admin's write is checked against changes underneath it (another instance, another person).
        OperatingModelService racing = new OperatingModelService(
                id -> Optional.of(new com.heuristiq.uam.organization.operatingmodel.application.port.OrganizationDirectory.Organization(id, null)),
                new com.heuristiq.uam.organization.operatingmodel.application.port.OperatingModelScheduleStore() {
                    @Override
                    public List<OperatingModelPeriod> find(String organizationId) {
                        return List.of();
                    }

                    @Override
                    public void replace(String organizationId, List<OperatingModelPeriod> expected, List<OperatingModelPeriod> next,
                                        String userId, Instant at) {
                        store.putDirectly(organizationId, List.of(new OperatingModelPeriod(OperatingModel.FRANCHISE, LocalDate.of(2026, 12, 1))));
                        store.replace(organizationId, expected, next, userId, at);
                    }
                }, mock(OperatingModelAudit.class), CLOCK, ZoneId.of("Asia/Kolkata"));
        CommonRequestMapper mapper = new CommonRequestMapper(JsonMapper.builder().findAndAddModules().build(),
                Validation.buildDefaultValidatorFactory().getValidator());
        MockMvc racingMvc = MockMvcBuilders.standaloneSetup(new OperatingModelController(racing, mapper))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        racingMvc.perform(put("/api/v1/uam/organizations/acme/operating-model").contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"model\":\"FRANCHISE\",\"effectiveFrom\":\"2027-02-01\"}]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(OperatingModelConstants.ERROR_CHANGED_MEANWHILE));
    }

    @Test
    void aSuperAdminMayChangeAnyOrganization() throws Exception {
        signInAs("acme", UamCommonConstants.SUPER_ADMIN_AUTHORITY);

        mvc.perform(put("/api/v1/uam/organizations/globex/operating-model").contentType(MediaType.APPLICATION_JSON)
                        .content(body("[{\"model\":\"FRANCHISE\",\"effectiveFrom\":\"2026-12-01\"}]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.organizationId").value("globex"));
    }

    @Test
    void anUnknownOrganizationIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/uam/internal/organizations/missing/operating-model"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anEmptyListCancelsTheScheduledChanges() throws Exception {
        signInAs("acme");
        mvc.perform(put("/api/v1/uam/organizations/acme/operating-model").contentType(MediaType.APPLICATION_JSON)
                .content(body("[{\"model\":\"FRANCHISE\",\"effectiveFrom\":\"2026-12-01\"}]")));

        mvc.perform(put("/api/v1/uam/organizations/acme/operating-model").contentType(MediaType.APPLICATION_JSON)
                        .content(body("[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.schedule").isEmpty());
        org.assertj.core.api.Assertions.assertThat(store.find("acme")).isEmpty();
    }
}
