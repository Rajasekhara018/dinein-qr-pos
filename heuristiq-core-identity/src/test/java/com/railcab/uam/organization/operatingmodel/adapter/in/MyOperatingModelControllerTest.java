package com.heuristiq.uam.organization.operatingmodel.adapter.in;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.heuristiq.uam.common.GlobalExceptionHandler;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.operatingmodel.adapter.out.TenantOrganizationDirectory;
import com.heuristiq.uam.organization.operatingmodel.application.OperatingModelService;
import com.heuristiq.uam.organization.operatingmodel.application.port.OperatingModelAudit;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModel;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelPeriod;
import com.heuristiq.uam.organization.operatingmodel.support.InMemoryOperatingModelScheduleStore;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The self-service lookup: the session's own organization only, today's model, and a clear refusal without one. */
class MyOperatingModelControllerTest {

    private static final String PATH = "/api/v1/uam/identity/me/operating-model";
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
        mvc = MockMvcBuilders.standaloneSetup(new MyOperatingModelController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void anOrganizationWithNoScheduleIsDirect() throws Exception {
        TenantContext.set("acme");

        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.organizationId").value("acme"))
                .andExpect(jsonPath("$.responseObject.model").value("DIRECT"))
                .andExpect(jsonPath("$.responseObject.date").value("2026-11-08"));
    }

    @Test
    void eachSessionSeesOnlyItsOwnOrganizationsModel() throws Exception {
        store.putDirectly("globex", List.of(new OperatingModelPeriod(OperatingModel.FRANCHISE, LocalDate.of(2026, 1, 1))));

        TenantContext.set("globex");
        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.organizationId").value("globex"))
                .andExpect(jsonPath("$.responseObject.model").value("FRANCHISE"));

        TenantContext.set("acme");
        mvc.perform(get(PATH))
                .andExpect(jsonPath("$.responseObject.organizationId").value("acme"))
                .andExpect(jsonPath("$.responseObject.model").value("DIRECT"));
    }

    @Test
    void aChangeThatHasNotStartedYetIsNotAnsweredToday() throws Exception {
        store.putDirectly("acme", List.of(new OperatingModelPeriod(OperatingModel.HYBRID, LocalDate.of(2027, 1, 1))));
        TenantContext.set("acme");

        mvc.perform(get(PATH)).andExpect(jsonPath("$.responseObject.model").value("DIRECT"));
    }

    @Test
    void aSessionWithoutAnOrganizationIsRefused() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownOrganizationIsNotFound() throws Exception {
        TenantContext.set("nobody");

        mvc.perform(get(PATH)).andExpect(status().isNotFound());
    }
}
