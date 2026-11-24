package com.heuristiq.uam.application.access;

import com.heuristiq.uam.application.Application;
import com.heuristiq.uam.application.access.repository.ApplicationAccessRepository;
import com.heuristiq.uam.application.repository.ApplicationRepository;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationAccessServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"), ZoneOffset.UTC);

    private InMemoryAccessRepository accessRepository;
    private ApplicationRepository applicationRepository;
    private UamUserRepository userRepository;
    private ApplicationAccessService service;

    @BeforeEach
    void setUp() {
        accessRepository = new InMemoryAccessRepository();
        applicationRepository = mock(ApplicationRepository.class);
        userRepository = mock(UamUserRepository.class);
        service = new ApplicationAccessService(accessRepository, applicationRepository, userRepository, clock);
        registerApplication("MATRIMONY", true);
        registerApplication("RIDER", true);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1", "AGENT")));
    }

    @Test
    void roleGrantReachesEveryHolderOfTheRole() {
        grant(AccessSubjectType.ROLE, "agent", "MATRIMONY");

        assertThat(resolve(user("u1", "AGENT"))).containsExactly("MATRIMONY");
        assertThat(resolve(user("u2", "CUSTOMER"))).isEmpty();
    }

    @Test
    void anOrganizationsAdministratorOpensEveryRegisteredApplication() {
        com.heuristiq.uam.role.repository.UamRoleRepository roles = mock(com.heuristiq.uam.role.repository.UamRoleRepository.class);
        when(roles.findByCodeIgnoreCase("ACME_ADMIN")).thenReturn(Optional.of(
                com.heuristiq.uam.role.UamRole.builder().code("ACME_ADMIN").tenantId("t-acme").build()));
        List<Application> registered = List.of(applicationRepository.findByCodeIgnoreCase("MATRIMONY").orElseThrow(),
                applicationRepository.findByCodeIgnoreCase("RIDER").orElseThrow());
        when(applicationRepository.findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Sort.class)))
                .thenReturn(registered);
        service.setRoleRepository(roles);

        assertThat(resolve(user("u9", "ACME_ADMIN"))).containsExactly("MATRIMONY", "RIDER");
        assertThat(resolve(user("u8", "AGENT"))).isEmpty();
    }

    @Test
    void directGrantAndRoleGrantAreCombinedWithoutDuplicates() {
        grant(AccessSubjectType.ROLE, "AGENT", "MATRIMONY");
        grant(AccessSubjectType.USER, "u1", "MATRIMONY");
        grant(AccessSubjectType.USER, "u1", "RIDER");

        assertThat(resolve(user("u1", "AGENT"))).containsExactly("MATRIMONY", "RIDER");
    }

    @Test
    void revokedGrantNoLongerCounts() {
        ApplicationAccess grant = grant(AccessSubjectType.USER, "u1", "MATRIMONY");

        ApplicationAccess revoked = service.revoke(new ApplicationAccessServiceUseCase.RevokeInput(grant.getId(), "admin-1")).access();

        assertThat(revoked.isActive()).isFalse();
        assertThat(revoked.getRevokedBy()).isEqualTo("admin-1");
        assertThat(resolve(user("u1", "CUSTOMER"))).isEmpty();
    }

    @Test
    void superAdminCanOpenEveryActiveApplicationWithoutAGrant() {
        when(applicationRepository.findAll(Sort.by("code"))).thenReturn(List.of(
                Application.builder().code("MATRIMONY").name("MATRIMONY").active(true).build(),
                Application.builder().code("RIDER").name("RIDER").active(true).build(),
                Application.builder().code("OLD").name("OLD").active(false).build()));
        registerApplication("OLD", false);

        assertThat(resolve(user("root", "SUPER_ADMIN"))).containsExactly("MATRIMONY", "RIDER");
        assertThat(resolve(user("u9", "ADMIN"))).isEmpty();
    }

    @Test
    void grantingAgainReactivatesTheSameRow() {
        ApplicationAccess first = grant(AccessSubjectType.USER, "u1", "MATRIMONY");
        service.revoke(new ApplicationAccessServiceUseCase.RevokeInput(first.getId(), "admin-1"));

        ApplicationAccess again = grant(AccessSubjectType.USER, "u1", "MATRIMONY");

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(again.isActive()).isTrue();
        assertThat(again.getRevokedAt()).isNull();
        assertThat(accessRepository.rows).hasSize(1);
    }

    @Test
    void applicationThatIsSwitchedOffOrGoneIsLeftOut() {
        grant(AccessSubjectType.USER, "u1", "MATRIMONY");
        grant(AccessSubjectType.USER, "u1", "RIDER");
        registerApplication("MATRIMONY", false);
        when(applicationRepository.findByCodeIgnoreCase("RIDER")).thenReturn(Optional.empty());

        assertThat(resolve(user("u1", "CUSTOMER"))).isEmpty();
    }

    @Test
    void cannotGrantAnUnknownApplicationOrUser() {
        when(applicationRepository.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> grant(AccessSubjectType.USER, "u1", "NOPE")).isInstanceOf(UamException.class);
        assertThatThrownBy(() -> grant(AccessSubjectType.USER, "ghost", "MATRIMONY")).isInstanceOf(UamException.class);
        assertThat(accessRepository.rows).isEmpty();
    }

    @Test
    void cannotGrantASwitchedOffApplication() {
        registerApplication("MATRIMONY", false);

        assertThatThrownBy(() -> grant(AccessSubjectType.ROLE, "AGENT", "MATRIMONY")).isInstanceOf(UamException.class);
    }

    private ApplicationAccess grant(AccessSubjectType type, String subjectId, String applicationCode) {
        return service.grant(new ApplicationAccessServiceUseCase.GrantInput(
                new GrantApplicationAccessRequest(applicationCode, type, subjectId, null), "admin-1")).access();
    }

    private List<String> resolve(UamUser user) {
        return service.resolveAccessibleApplications(new ApplicationAccessServiceUseCase.ResolveInput(user)).applicationCodes();
    }

    private void registerApplication(String code, boolean active) {
        when(applicationRepository.findByCodeIgnoreCase(code))
                .thenReturn(Optional.of(Application.builder().code(code).name(code).active(active).build()));
    }

    private UamUser user(String id, String role) {
        return UamUser.builder().id(id).roles(List.of(role)).build();
    }

    private static final class InMemoryAccessRepository implements ApplicationAccessRepository {
        final List<ApplicationAccess> rows = new ArrayList<>();

        @Override
        public ApplicationAccess save(ApplicationAccess access) {
            if (access.getId() == null) {
                access.setId(UUID.randomUUID().toString());
                rows.add(access);
            }
            return access;
        }

        @Override
        public Optional<ApplicationAccess> findById(String id) {
            return rows.stream().filter(r -> r.getId().equals(id)).findFirst();
        }

        @Override
        public Optional<ApplicationAccess> findBySubject(String applicationCode, AccessSubjectType type, String subjectId) {
            return rows.stream().filter(r -> r.getApplicationCode().equals(applicationCode)
                    && r.getSubjectType() == type && r.getSubjectId().equals(subjectId)).findFirst();
        }

        @Override
        public List<ApplicationAccess> findByApplicationCode(String applicationCode) {
            return rows.stream().filter(r -> r.getApplicationCode().equals(applicationCode)).toList();
        }

        @Override
        public List<ApplicationAccess> findBySubjectTypeAndSubjectId(AccessSubjectType type, String subjectId) {
            return rows.stream().filter(r -> r.getSubjectType() == type && r.getSubjectId().equals(subjectId)).toList();
        }

        @Override
        public List<ApplicationAccess> findActiveBySubjects(AccessSubjectType type, Collection<String> subjectIds) {
            return rows.stream().filter(r -> r.isActive() && r.getSubjectType() == type
                    && subjectIds.contains(r.getSubjectId())).toList();
        }
    }
}
