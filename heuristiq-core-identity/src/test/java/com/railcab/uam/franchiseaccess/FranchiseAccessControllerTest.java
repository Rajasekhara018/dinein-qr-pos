package com.heuristiq.uam.franchiseaccess;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.UserResponse;
import com.heuristiq.uam.orgstructure.domain.OrgStructureException;
import com.heuristiq.uam.security.InternalServiceSecurityConfig;
import com.heuristiq.uam.security.SecurityConstants;

/** Every franchise-access endpoint, behind the real internal security chain: no or a wrong service token is rejected. */
class FranchiseAccessControllerTest {
    private static final String BASE = "/api/v1/uam/internal/franchise-access";
    private static final String TOKEN = "s3cret";

    /** The service the controller delegates to; replaced per test. */
    static FranchiseAccessService service;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(InternalServiceSecurityConfig.class)
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        FranchiseAccessController controller() {
            return new FranchiseAccessController(service);
        }

        @Bean
        FranchiseAccessExceptionAdvice advice() {
            return new FranchiseAccessExceptionAdvice();
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(FranchiseAccessService.class);
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        MockEnvironment environment = new MockEnvironment().withProperty("rider.internal.service-token", TOKEN);
        context.setEnvironment(environment);
        context.register(TestConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class)).build();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authed(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request.header(SecurityConstants.INTERNAL_SERVICE_TOKEN_HEADER, TOKEN).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void withoutTheServiceTokenEveryEndpointIsRejectedAndNothingIsCalled() throws Exception {
        mvc.perform(get(BASE + "/applications/MATRI_APP/exists")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/applications/MATRI_APP")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/users/by-email").param("email", "a@b.c")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/users/u1")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/tenants/org-1/members/u1")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/tenants/org-1/members").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"u1\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/tenants/org-1/subscriptions/MATRI_APP")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/tenants/org-1/users/u1/privileges/COMMISSION_PLAN_CREATE")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/tenants/org-1/support-email")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/tenants/org-1/role-codes")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/email-in-use").param("email", "a@b.c")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/roles/ensure").contentType(MediaType.APPLICATION_JSON).content("{\"roleCode\":\"R\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/users/u1/roles").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/tenants/org-1/subscriptions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/users/u1/application-grants").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/tenants/org-1/logins").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.c\"}"))
                .andExpect(status().isUnauthorized());

        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void aWrongServiceTokenIsRejected() throws Exception {
        mvc.perform(get(BASE + "/users/u1").header(SecurityConstants.INTERNAL_SERVICE_TOKEN_HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void anEndUserBearerTokenIsNotACredentialHere() throws Exception {
        mvc.perform(get(BASE + "/users/u1").header("Authorization", "Bearer some.user.jwt")).andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(service);
    }
    @Test
    void theApplicationListNeedsTheServiceTokenAndAnswersTheCodesInTheEnvelope() throws Exception {
        mvc.perform(get(BASE + "/applications")).andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(service);
        when(service.applicationCodes()).thenReturn(java.util.List.of("MATRIMONY", "TRAIN"));
        mvc.perform(authed(get(BASE + "/applications")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responseObject.codes[0]").value("MATRIMONY"))
                .andExpect(jsonPath("$.responseObject.codes[1]").value("TRAIN"));
    }

    @Test
    void applicationExistsAnswersInTheEnvelope() throws Exception {
        when(service.applicationExists("MATRI_APP")).thenReturn(true);

        mvc.perform(authed(get(BASE + "/applications/MATRI_APP/exists")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responseObject.exists").value(true));
    }

    @Test
    void anApplicationIsFoundWithItsLinksAndAnUnknownCodeIsFoundFalse() throws Exception {
        when(service.application("MATRI_APP")).thenReturn(new FranchiseAccessModels.ApplicationResponse(true, "MATRI_APP", "Matrimony", "/home", "https://m.example.com"));
        when(service.application("NOPE")).thenReturn(FranchiseAccessModels.ApplicationResponse.missing());

        mvc.perform(authed(get(BASE + "/applications/MATRI_APP")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.found").value(true))
                .andExpect(jsonPath("$.responseObject.code").value("MATRI_APP"))
                .andExpect(jsonPath("$.responseObject.name").value("Matrimony"))
                .andExpect(jsonPath("$.responseObject.landingPage").value("/home"))
                .andExpect(jsonPath("$.responseObject.baseUrl").value("https://m.example.com"));
        mvc.perform(authed(get(BASE + "/applications/NOPE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.found").value(false))
                .andExpect(jsonPath("$.responseObject.name").doesNotExist());
    }

    @Test
    void theAccountLookupsRequireTheOrganization() throws Exception {
        mvc.perform(authed(get(BASE + "/users/u1"))).andExpect(status().isBadRequest());
        mvc.perform(authed(get(BASE + "/users/by-email").param("email", "a@b.c"))).andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void userByEmailAndByIdAnswerWithTheAccount() throws Exception {
        UserResponse ravi = new UserResponse(true, "u1", "Ravi", "ravi@example.com", List.of("FRANCHISE_OWNER"));
        when(service.userByEmail("org-1", "ravi@example.com")).thenReturn(ravi);
        when(service.userById("org-1", "u1")).thenReturn(ravi);
        when(service.userById("org-1", "none")).thenReturn(UserResponse.missing());

        mvc.perform(authed(get(BASE + "/users/by-email").param("tenantId", "org-1").param("email", "ravi@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.found").value(true))
                .andExpect(jsonPath("$.responseObject.userId").value("u1"))
                .andExpect(jsonPath("$.responseObject.displayName").value("Ravi"))
                .andExpect(jsonPath("$.responseObject.roles[0]").value("FRANCHISE_OWNER"));
        mvc.perform(authed(get(BASE + "/users/u1").param("tenantId", "org-1"))).andExpect(jsonPath("$.responseObject.email").value("ravi@example.com"));
        mvc.perform(authed(get(BASE + "/users/none").param("tenantId", "org-1"))).andExpect(status().isOk()).andExpect(jsonPath("$.responseObject.found").value(false));
    }

    @Test
    void severalAccountsAreLookedUpInOneCall() throws Exception {
        when(service.usersByIds("org-1", List.of("u1", "u2"))).thenReturn(new FranchiseAccessModels.UsersResponse(
                List.of(new UserResponse(true, "u1", "Ravi", "ravi@example.com", List.of()))));

        mvc.perform(authed(get(BASE + "/users").param("tenantId", "org-1").param("ids", "u1", "u2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.users[0].userId").value("u1"))
                .andExpect(jsonPath("$.responseObject.users[0].displayName").value("Ravi"));
    }

    @Test
    void memberChecksAreForTheOrganizationInThePath() throws Exception {
        when(service.isActiveMember("org-1", "u1")).thenReturn(true);

        mvc.perform(authed(get(BASE + "/tenants/org-1/members/u1"))).andExpect(jsonPath("$.responseObject.active").value(true));
        mvc.perform(authed(get(BASE + "/tenants/org-2/members/u1"))).andExpect(jsonPath("$.responseObject.active").value(false));
    }

    @Test
    void addingAMemberPassesTheOrganizationFromThePathAndTheBody() throws Exception {
        mvc.perform(authed(post(BASE + "/tenants/org-1/members")).content("{\"userId\":\"u1\",\"actor\":\"svc\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

        verify(service).addMember("org-1", "u1", "svc");
    }

    @Test
    void supportEmailIsNullWhenTheOrganizationHasNone() throws Exception {
        when(service.supportEmail("org-1")).thenReturn(Optional.of("help@org.com"));
        when(service.supportEmail("org-2")).thenReturn(Optional.empty());

        mvc.perform(authed(get(BASE + "/tenants/org-1/support-email"))).andExpect(jsonPath("$.responseObject.email").value("help@org.com"));
        mvc.perform(authed(get(BASE + "/tenants/org-2/support-email"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.email").doesNotExist());
    }

    @Test
    void roleCodesAreListedSortedForTheOrganization() throws Exception {
        when(service.roleCodes("org-1")).thenReturn(Set.of("B", "A"));

        mvc.perform(authed(get(BASE + "/tenants/org-1/role-codes")))
                .andExpect(jsonPath("$.responseObject.codes[0]").value("A"))
                .andExpect(jsonPath("$.responseObject.codes[1]").value("B"));
    }

    @Test
    void emailInUseAnswersFromTheService() throws Exception {
        when(service.emailInUse("taken@example.com")).thenReturn(true);

        mvc.perform(authed(get(BASE + "/email-in-use").param("email", "taken@example.com"))).andExpect(jsonPath("$.responseObject.inUse").value(true));
        mvc.perform(authed(get(BASE + "/email-in-use").param("email", "free@example.com"))).andExpect(jsonPath("$.responseObject.inUse").value(false));
    }

    @Test
    void ensureRolePassesTheDefinition() throws Exception {
        mvc.perform(authed(post(BASE + "/roles/ensure"))
                        .content("{\"roleCode\":\"FRANCHISE_OWNER\",\"name\":\"Owner\",\"description\":\"d\",\"privileges\":[\"P1\"]}"))
                .andExpect(status().isOk());

        verify(service).ensureRole("FRANCHISE_OWNER", "Owner", "d", Set.of("P1"));
    }

    @Test
    void addRolesSubscribeAndGrantPassTheirBodies() throws Exception {
        mvc.perform(authed(post(BASE + "/users/u1/roles")).content("{\"roleCodes\":[\"A\",\"B\"]}")).andExpect(status().isOk());
        mvc.perform(authed(post(BASE + "/tenants/org-1/subscriptions")).content("{\"applicationCode\":\"MATRI_APP\",\"actor\":\"svc\"}"))
                .andExpect(status().isOk());
        mvc.perform(authed(post(BASE + "/users/u1/application-grants"))
                .content("{\"applicationCode\":\"MATRI_APP\",\"actor\":\"svc\",\"note\":\"n\"}")).andExpect(status().isOk());

        verify(service).addRoles("u1", List.of("A", "B"));
        verify(service).subscribe("org-1", "MATRI_APP", "svc");
        verify(service).grant("u1", "MATRI_APP", "svc", "n");
    }

    @Test
    void createLoginReturnsTheNewUserIdForTheOrganizationInThePath() throws Exception {
        when(service.createLogin("org-1", "ravi@example.com", "Ravi", "+919876543210", List.of("FRANCHISE_OWNER"))).thenReturn("new-1");

        mvc.perform(authed(post(BASE + "/tenants/org-1/logins"))
                        .content("{\"email\":\"ravi@example.com\",\"name\":\"Ravi\",\"phone\":\"+919876543210\",\"roleCodes\":[\"FRANCHISE_OWNER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.userId").value("new-1"));
    }

    @Test
    void aTakenEmailIsAConflictWithTheUamMessage() throws Exception {
        when(service.createLogin(eq("org-1"), any(), any(), any(), any())).thenThrow(UamException.conflict("Email already in use"));

        mvc.perform(authed(post(BASE + "/tenants/org-1/logins")).content("{\"email\":\"a@b.c\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email already in use"));
    }

    @Test
    void ruleFailuresMapToTheStatusTheCallerCanActOn() throws Exception {
        when(service.createLogin(eq("org-1"), any(), any(), any(), any()))
                .thenThrow(new UamException("Phone number is required"))
                .thenThrow(UamException.notFound("Role not found"))
                .thenThrow(new UamException("Only a super admin can do that", HttpStatus.FORBIDDEN))
                .thenThrow(OrgStructureException.conflict("Email already used"))
                .thenThrow(OrgStructureException.invalid("Bad phone"))
                .thenThrow(OrgStructureException.notFound("No such thing"));
        String body = "{\"email\":\"a@b.c\"}";

        mvc.perform(authed(post(BASE + "/tenants/org-1/logins")).content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phone number is required"));
        mvc.perform(authed(post(BASE + "/tenants/org-1/logins")).content(body)).andExpect(status().isNotFound());
        mvc.perform(authed(post(BASE + "/tenants/org-1/logins")).content(body)).andExpect(status().isConflict());
        mvc.perform(authed(post(BASE + "/tenants/org-1/logins")).content(body)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already used"));
        mvc.perform(authed(post(BASE + "/tenants/org-1/logins")).content(body)).andExpect(status().isBadRequest());
        mvc.perform(authed(post(BASE + "/tenants/org-1/logins")).content(body)).andExpect(status().isNotFound());
        verify(service, never()).addMember(any(), any(), any());
    }

    @Test
    void theSubscriptionCheckAnswersSubscribedWithTheNamesAndNeverSubscribes() throws Exception {
        when(service.subscription("org-1", "MATRI_APP")).thenReturn(new FranchiseAccessModels.SubscribedResponse(true, "Acme", "Matrimony"));
        when(service.subscription("org-2", "MATRI_APP")).thenReturn(new FranchiseAccessModels.SubscribedResponse(false, "Beta", "Matrimony"));

        mvc.perform(authed(get(BASE + "/tenants/org-1/subscriptions/MATRI_APP"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.subscribed").value(true))
                .andExpect(jsonPath("$.responseObject.organizationName").value("Acme"));
        mvc.perform(authed(get(BASE + "/tenants/org-2/subscriptions/MATRI_APP"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.subscribed").value(false));
        verify(service, never()).subscribe(any(), any(), any());
    }

    @Test
    void theSubscriptionListAnswersTheOrganizationsApplicationsAndNeedsTheServiceToken() throws Exception {
        when(service.subscribedApplicationCodes("org-1")).thenReturn(List.of("MATRI_APP", "RIDE"));

        mvc.perform(authed(get(BASE + "/tenants/org-1/subscriptions"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.codes[0]").value("MATRI_APP"))
                .andExpect(jsonPath("$.responseObject.codes[1]").value("RIDE"));
        mvc.perform(get(BASE + "/tenants/org-1/subscriptions")).andExpect(status().isUnauthorized());
        verify(service, never()).subscribe(any(), any(), any());
    }

    @Test
    void thePrivilegeCheckAnswersGrantedForTheOrganizationInThePath() throws Exception {
        when(service.holdsPrivilege("org-1", "u1", "COMMISSION_PLAN_CREATE")).thenReturn(true);

        mvc.perform(authed(get(BASE + "/tenants/org-1/users/u1/privileges/COMMISSION_PLAN_CREATE"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.granted").value(true));
        mvc.perform(authed(get(BASE + "/tenants/org-2/users/u1/privileges/COMMISSION_PLAN_CREATE"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.granted").value(false));
    }
}
