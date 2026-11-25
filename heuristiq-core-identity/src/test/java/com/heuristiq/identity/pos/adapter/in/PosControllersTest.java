package com.heuristiq.identity.pos.adapter.in;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.GlobalExceptionHandler;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.pos.application.PosCaller;
import com.heuristiq.identity.pos.application.PosOrganizationService;
import com.heuristiq.identity.pos.application.port.PosOrganizationDirectory;
import com.heuristiq.identity.pos.domain.BranchDraft;
import com.heuristiq.identity.pos.domain.LegalEntity;
import com.heuristiq.identity.pos.domain.LegalEntityDraft;
import com.heuristiq.identity.pos.domain.PosAddress;
import com.heuristiq.identity.pos.support.InMemoryPosStores;
import com.heuristiq.identity.security.TenantContext;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The POS endpoints: answer shape, validation keys, versions, and that other tenants' or branches' data is never confirmed. */
class PosControllersTest {

    private static final String T1 = "/api/v1/uam/organizations/t1";
    private static final String T2 = "/api/v1/uam/organizations/t2";

    private MockMvc mvc;
    private String entityId;
    private String otherTenantBranchId;
    private String branchA;
    private String branchB;

    @BeforeEach
    void setUp() {
        TenantRepository tenants = mock(TenantRepository.class);
        when(tenants.findById(any())).thenReturn(Optional.empty());
        when(tenants.findById("t1")).thenReturn(Optional.of(Tenant.builder().id("t1").code("ACME").build()));
        when(tenants.findById("t2")).thenReturn(Optional.of(Tenant.builder().id("t2").code("GLOBEX").build()));
        PosOrganizationDirectory directory = new PosOrganizationDirectory() {
            @Override
            public boolean tenantExists(String tenantId) {
                return Set.of("t1", "t2").contains(tenantId);
            }

            @Override
            public boolean isActiveMember(String tenantId, String userId) {
                return true;
            }
        };
        InMemoryPosStores stores = new InMemoryPosStores();
        PosOrganizationService service = new PosOrganizationService(directory, stores.legalEntities(), stores.branches(),
                stores.staff(), (t, a, type, id, before, after) -> { },
                Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC));
        PosAddress address = new PosAddress("1 MG Road", null, "Hyderabad", "Telangana", "500001", "IN");
        PosCaller admin1 = new PosCaller("admin", "t1", true);
        PosCaller admin2 = new PosCaller("admin", "t2", true);
        LegalEntity entity = service.createLegalEntity(admin1, "t1",
                new LegalEntityDraft("IN", "Acme Foods Pvt Ltd", null, List.of(), address, "acme", null));
        entityId = entity.id();
        LegalEntity other = service.createLegalEntity(admin2, "t2",
                new LegalEntityDraft("IN", "Globex Pvt Ltd", null, List.of(), address, "glx", null));
        otherTenantBranchId = service.createBranch(admin2, "t2",
                new BranchDraft(other.id(), "GLX-1", "Globex Main", "Asia/Kolkata", null, null, null, null, address)).id();
        branchA = service.createBranch(admin1, "t1",
                new BranchDraft(entityId, "HYD-1", "Main", "Asia/Kolkata", null, null, null, null, address)).id();
        branchB = service.createBranch(admin1, "t1",
                new BranchDraft(entityId, "HYD-2", "Second", "Asia/Kolkata", null, null, null, null, address)).id();
        service.replaceBranchStaff(admin1, "t1", branchA, Set.of("cashier-1"));

        CommonRequestMapper mapper = new CommonRequestMapper(JsonMapper.builder().findAndAddModules().build(),
                Validation.buildDefaultValidatorFactory().getValidator());
        UamUserRepository users = mock(UamUserRepository.class);
        when(users.findById(any())).thenReturn(Optional.empty());
        when(users.findById("rest-admin")).thenReturn(Optional.of(UamUser.builder().id("rest-admin").tenantId("t1").build()));
        PosCallers callers = new PosCallers(tenants, users);
        mvc = MockMvcBuilders.standaloneSetup(new LegalEntityController(service, mapper, callers),
                new BranchController(service, mapper, callers), new BranchStaffController(service, mapper, callers))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(String userId, String tenantId, String... authorities) {
        TenantContext.set(tenantId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userId, "n/a",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private String branchBody(String code, String version) {
        return "{\"requestObject\":{\"legalEntityId\":\"" + entityId + "\",\"code\":\"" + code + "\",\"name\":\"Branch\","
                + "\"timeZone\":\"Asia/Kolkata\",\"address\":{\"line1\":\"2 Park St\",\"city\":\"Hyderabad\","
                + "\"region\":\"Telangana\",\"postalCode\":\"500002\",\"country\":\"IN\"},\"version\":" + version + "}}";
    }

    @Test
    void createBranchReturnsVersion1() throws Exception {
        signInAs("admin", "t1", "ROLE_ACME_ADMIN");

        mvc.perform(post(T1 + "/branches").contentType(MediaType.APPLICATION_JSON).content(branchBody("blr-1", "null")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.version").value(1))
                .andExpect(jsonPath("$.responseObject.code").value("BLR-1"))
                .andExpect(jsonPath("$.responseObject.id").isNotEmpty());
    }

    @Test
    void invalidDraftAnswers400WithField() throws Exception {
        signInAs("admin", "t1", "ROLE_ACME_ADMIN");

        mvc.perform(post(T1 + "/branches").contentType(MediaType.APPLICATION_JSON).content(branchBody("x", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("pos.branch.code.invalid"))
                .andExpect(jsonPath("$.errorCode").value("code"));
    }

    @Test
    void staleVersionAnswers409() throws Exception {
        signInAs("admin", "t1", "ROLE_ACME_ADMIN");

        mvc.perform(put(T1 + "/branches/" + branchA).contentType(MediaType.APPLICATION_JSON).content(branchBody("HYD-1", "7")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("pos.staleVersion"));
        mvc.perform(put(T1 + "/branches/" + branchA).contentType(MediaType.APPLICATION_JSON).content(branchBody("HYD-1", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.version").value(2));
    }

    @Test
    void anotherTenantsBranchIsNotFound() throws Exception {
        signInAs("admin", "t1", "ROLE_ACME_ADMIN");

        mvc.perform(get(T1 + "/branches/" + otherTenantBranchId)).andExpect(status().isNotFound());
        mvc.perform(get(T2 + "/branches/" + otherTenantBranchId)).andExpect(status().isNotFound());
        mvc.perform(get(T2 + "/branches")).andExpect(status().isNotFound());
    }

    @Test
    void branchScopedCallerCannotReachOtherBranch() throws Exception {
        signInAs("cashier-1", "t1", "ROLE_CASHIER");

        mvc.perform(get(T1 + "/branches/" + branchA)).andExpect(status().isOk());
        mvc.perform(get(T1 + "/branches/" + branchB)).andExpect(status().isNotFound());
        mvc.perform(get(T1 + "/branches/" + branchB + "/staff")).andExpect(status().isNotFound());
        mvc.perform(get(T1 + "/branches")).andExpect(status().isOk()).andExpect(jsonPath("$.responseObject.length()").value(1));
        mvc.perform(post(T1 + "/legal-entities").contentType(MediaType.APPLICATION_JSON).content("{\"requestObject\":{}}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void staffCanBeReplacedAndRead() throws Exception {
        signInAs("admin", "t1", "ROLE_ACME_ADMIN");

        mvc.perform(put(T1 + "/branches/" + branchB + "/staff").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestObject\":{\"userIds\":[\"u1\",\"u2\"]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.userIds.length()").value(2));
        mvc.perform(get(T1 + "/branches/" + branchB + "/staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.userIds.length()").value(2));
    }

    @Test
    void restaurantAdminOfAnotherHomeTenantCannotCreateBranchHere() throws Exception {
        signInAs("rest-admin", "t2", "ROLE_POS_RESTAURANT_ADMIN");

        mvc.perform(post(T2 + "/branches").contentType(MediaType.APPLICATION_JSON).content(branchBody("blr-9", "null")))
                .andExpect(status().isNotFound());
    }

    @Test
    void legalEntityRoundTrip() throws Exception {
        signInAs("admin", "t1", "ROLE_ACME_ADMIN");

        mvc.perform(get(T1 + "/legal-entities/" + entityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.currency").value("INR"))
                .andExpect(jsonPath("$.responseObject.version").value(1));
    }
}
