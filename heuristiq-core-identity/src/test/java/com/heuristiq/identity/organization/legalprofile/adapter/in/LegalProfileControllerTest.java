package com.heuristiq.identity.organization.legalprofile.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.GlobalExceptionHandler;
import com.heuristiq.identity.common.UamCommonConstants;
import com.heuristiq.identity.organization.OrgWideConstants;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.legalprofile.adapter.out.TenantLegalOrganizationDirectory;
import com.heuristiq.identity.organization.legalprofile.application.LegalProfileService;
import com.heuristiq.identity.organization.legalprofile.application.port.LegalProfileAudit;
import com.heuristiq.identity.organization.legalprofile.domain.LegalProfileConstants;
import com.heuristiq.identity.organization.legalprofile.support.InMemoryLegalProfileStore;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.security.SecurityConstants;
import com.heuristiq.identity.security.TenantContext;
import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
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

/** The legal-profile endpoints: answer shape, validation keys, versions, and that one organization cannot reach another's. */
class LegalProfileControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"), ZoneOffset.UTC);
    private static final String ACME = "/api/v1/uam/organizations/acme/legal-profile";
    private static final String GLOBEX = "/api/v1/uam/organizations/globex/legal-profile";

    private final Map<String, Tenant> rows = new HashMap<>();
    private InMemoryLegalProfileStore store;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TenantRepository tenants = mock(TenantRepository.class);
        when(tenants.findById(any())).thenAnswer(i -> Optional.ofNullable(rows.get(i.<String>getArgument(0))));
        when(tenants.save(any())).thenAnswer(i -> {
            Tenant t = i.getArgument(0);
            rows.put(t.getId(), t);
            return t;
        });
        rows.put("acme", Tenant.builder().id("acme").code("ACME").legalName("Acme Pvt Ltd").build());
        rows.put("globex", Tenant.builder().id("globex").code("GLOBEX").legalName("Globex LLC").build());
        store = new InMemoryLegalProfileStore();
        LegalProfileService service = new LegalProfileService(new TenantLegalOrganizationDirectory(tenants), store,
                mock(LegalProfileAudit.class), CLOCK);
        CommonRequestMapper mapper = new CommonRequestMapper(JsonMapper.builder().findAndAddModules().build(),
                Validation.buildDefaultValidatorFactory().getValidator());
        mvc = MockMvcBuilders.standaloneSetup(new LegalProfileController(service, mapper))
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
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private static String body(String version) {
        return "{\"requestObject\":{\"entityType\":\"PRIVATE_COMPANY\",\"legalName\":\"Acme Mobility Pvt Ltd\",\"tradingName\":\"Acme Rides\","
                + "\"registrationNumber\":\"U12345KA2020PTC123456\",\"registrationCountry\":\"IN\",\"taxRegistered\":true,"
                + "\"taxIds\":[{\"type\":\"PAN\",\"value\":\"ABCDE1234F\"},{\"type\":\"GSTIN\",\"value\":\"29ABCDE1234F1Z5\"}],"
                + "\"addresses\":[{\"kind\":\"REGISTERED\",\"line1\":\"12 MG Road\",\"line2\":null,\"city\":\"Bengaluru\","
                + "\"region\":\"Karnataka\",\"postalCode\":\"560001\",\"country\":\"IN\"}],"
                + "\"version\":" + version + "}}";
    }

    @Test
    void anOrganizationWithoutAProfileAnswersAnEmptyOneNot404() throws Exception {
        signInAs("acme");

        mvc.perform(get(ACME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.entityType").doesNotExist())
                .andExpect(jsonPath("$.responseObject.legalName").value("Acme Pvt Ltd"))
                .andExpect(jsonPath("$.responseObject.taxRegistered").value(false))
                .andExpect(jsonPath("$.responseObject.taxIds").isEmpty())
                .andExpect(jsonPath("$.responseObject.addresses").isEmpty())
                .andExpect(jsonPath("$.responseObject.version").doesNotExist());
    }

    @Test
    void savingAnswersTheNewStateAndASecondSaveNeedsTheNewVersion() throws Exception {
        signInAs("acme");

        // Standalone MockMvc has no security filter: the signed-in person reaches the controller as the request principal.
        mvc.perform(put(ACME).principal(SecurityContextHolder.getContext().getAuthentication())
                        .contentType(MediaType.APPLICATION_JSON).content(body("null")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.entityType").value("PRIVATE_COMPANY"))
                .andExpect(jsonPath("$.responseObject.legalName").value("Acme Mobility Pvt Ltd"))
                .andExpect(jsonPath("$.responseObject.taxIds.length()").value(2))
                .andExpect(jsonPath("$.responseObject.taxIds[0].type").value("PAN"))
                .andExpect(jsonPath("$.responseObject.addresses[0].kind").value("REGISTERED"))
                .andExpect(jsonPath("$.responseObject.version").value(1))
                .andExpect(jsonPath("$.responseObject.updatedBy").value("admin-1"))
                .andExpect(jsonPath("$.responseObject.updatedAt").exists());
        assertThat(rows.get("acme").getLegalName()).isEqualTo("Acme Mobility Pvt Ltd");

        mvc.perform(put(ACME).contentType(MediaType.APPLICATION_JSON).content(body("null")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(LegalProfileConstants.ERROR_STALE_VERSION));
        mvc.perform(put(ACME).contentType(MediaType.APPLICATION_JSON).content(body("1")))
                .andExpect(status().isOk());
    }

    @Test
    void anInvalidValueAnswers400WithItsKeyAndField() throws Exception {
        signInAs("acme");

        mvc.perform(put(ACME).contentType(MediaType.APPLICATION_JSON).content(body("null").replace("560001", "060001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(LegalProfileConstants.ERROR_INVALID_POSTAL_CODE))
                .andExpect(jsonPath("$.errorCode").value("addresses[0].postalCode"));
        assertThat(store.find("acme")).isEmpty();
    }

    @Test
    void aMissingRequestObjectIsABadRequest() throws Exception {
        signInAs("acme");

        mvc.perform(put(ACME).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oneOrganizationCannotReadOrChangeAnothersProfile() throws Exception {
        signInAs("acme");

        mvc.perform(get(GLOBEX))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(LegalProfileConstants.ERROR_ORGANIZATION_NOT_FOUND));
        mvc.perform(put(GLOBEX).contentType(MediaType.APPLICATION_JSON).content(body("null")))
                .andExpect(status().isNotFound());
        assertThat(store.find("globex")).isEmpty();
        assertThat(rows.get("globex").getLegalName()).isEqualTo("Globex LLC");
    }

    @Test
    void aSignedInPersonWhoseTokenNamesNoOrganizationReachesNone() throws Exception {
        signInAs(null);

        mvc.perform(get(ACME)).andExpect(status().isNotFound());
        mvc.perform(put(ACME).contentType(MediaType.APPLICATION_JSON).content(body("null"))).andExpect(status().isNotFound());
        assertThat(store.find("acme")).isEmpty();
    }

    @Test
    void anOrganizationWideCallerMayReadAndChangeAnyOrganization() throws Exception {
        signInAs("acme", OrgWideConstants.AUTHORITY);

        mvc.perform(put(GLOBEX).contentType(MediaType.APPLICATION_JSON).content(body("null")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.version").value(1));
        mvc.perform(get(GLOBEX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.legalName").value("Acme Mobility Pvt Ltd"));
        assertThat(store.find("globex")).isPresent();
        assertThat(store.find("acme")).isEmpty();
    }

    @Test
    void aSuperAdminMayReachAnyOrganization() throws Exception {
        signInAs("acme", UamCommonConstants.SUPER_ADMIN_AUTHORITY);

        mvc.perform(get(GLOBEX)).andExpect(status().isOk()).andExpect(jsonPath("$.responseObject.legalName").value("Globex LLC"));
    }

    @Test
    void aServiceCallIsNotScopedToASession() throws Exception {
        signInAs(null, SecurityConstants.SERVICE_AUTHORITY);

        mvc.perform(get(GLOBEX)).andExpect(status().isOk());
    }

    @Test
    void anUnknownOrganizationIsNotFoundEvenForAnOrganizationWideCaller() throws Exception {
        signInAs("acme", OrgWideConstants.AUTHORITY);

        mvc.perform(get("/api/v1/uam/organizations/missing/legal-profile")).andExpect(status().isNotFound());
    }
}
