package com.heuristiq.identity.securitypolicy;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.securitypolicy.SecurityPolicyServiceUseCase.CreateInput;
import com.heuristiq.identity.securitypolicy.SecurityPolicyServiceUseCase.DeleteInput;
import com.heuristiq.identity.securitypolicy.SecurityPolicyServiceUseCase.GetInput;
import com.heuristiq.identity.securitypolicy.SecurityPolicyServiceUseCase.ListInput;
import com.heuristiq.identity.securitypolicy.SecurityPolicyServiceUseCase.ListPageInput;
import com.heuristiq.identity.securitypolicy.SecurityPolicyServiceUseCase.UpdateInput;
import com.heuristiq.identity.securitypolicy.repository.SecurityPolicyRepository;
import com.heuristiq.identity.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityPolicyServiceTest {
    private SecurityPolicyRepository repository;
    private SecurityPolicyService service;

    @BeforeEach
    void setUp() {
        repository = mock(SecurityPolicyRepository.class);
        when(repository.save(any(SecurityPolicy.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new SecurityPolicyService(repository);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static UpsertSecurityPolicyRequest request(String tenant, String app, String name) {
        return new UpsertSecurityPolicyRequest(tenant, app, name, " desc ", null, null, null, null, null, null);
    }

    @Test
    void createNormalizesFieldsAndActivates() {
        when(repository.findByTenantIdAndApplicationId("t1", null)).thenReturn(Optional.empty());

        SecurityPolicy policy = service.create(new CreateInput(request(" t1 ", " ", " Default "))).policy();

        assertThat(policy.getTenantId()).isEqualTo("t1");
        assertThat(policy.getApplicationId()).isNull();
        assertThat(policy.getName()).isEqualTo("Default");
        assertThat(policy.getDescription()).isEqualTo("desc");
        assertThat(policy.getActive()).isTrue();
        assertThat(policy.getAudit().getCreatedAt()).isNotNull();
    }

    @Test
    void createRejectsDuplicateScopeAndBlankName() {
        when(repository.findByTenantIdAndApplicationId("t1", "a")).thenReturn(Optional.of(new SecurityPolicy()));
        assertThatThrownBy(() -> service.create(new CreateInput(request("t1", "a", "n"))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        when(repository.findByTenantIdAndApplicationId("t2", null)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(new CreateInput(request("t2", null, "  "))))
                .isInstanceOf(UamException.class).hasMessageContaining("name is required");
        verify(repository, never()).save(any());
    }

    @Test
    void updateChangesFieldsAndAllowsSameRecordScope() {
        SecurityPolicy existing = SecurityPolicy.builder().id("p1").tenantId("t1").name("old")
                .audit(AuditInfo.builder().build()).build();
        when(repository.findById("p1")).thenReturn(Optional.of(existing));
        when(repository.findByTenantIdAndApplicationId("t1", "a")).thenReturn(Optional.of(existing));

        SecurityPolicy out = service.update(new UpdateInput("p1", request("t1", "a", "new"))).policy();

        assertThat(out.getName()).isEqualTo("new");
        assertThat(out.getApplicationId()).isEqualTo("a");
        assertThat(out.getAudit().getUpdatedAt()).isNotNull();
    }

    @Test
    void updateWithoutAuditAndWithConflictingOtherPolicy() {
        SecurityPolicy existing = SecurityPolicy.builder().id("p1").build();
        SecurityPolicy other = SecurityPolicy.builder().id("p2").build();
        when(repository.findById("p1")).thenReturn(Optional.of(existing));
        when(repository.findByTenantIdAndApplicationId(null, null)).thenReturn(Optional.empty());
        assertThat(service.update(new UpdateInput("p1", request(null, null, "n"))).policy().getName()).isEqualTo("n");

        when(repository.findByTenantIdAndApplicationId("t", null)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.update(new UpdateInput("p1", request("t", null, "n"))))
                .isInstanceOf(UamException.class).hasMessageContaining("already exists");
    }

    @Test
    void getEnforcesTenantIsolation() {
        SecurityPolicy foreign = SecurityPolicy.builder().id("p9").tenantId("orgB").build();
        SecurityPolicy shared = SecurityPolicy.builder().id("p0").tenantId(null).build();
        when(repository.findById("p9")).thenReturn(Optional.of(foreign));
        when(repository.findById("p0")).thenReturn(Optional.of(shared));
        when(repository.findById("none")).thenReturn(Optional.empty());
        TenantContext.set("orgA");

        assertThatThrownBy(() -> service.get(new GetInput("p9")))
                .isInstanceOf(UamException.class).hasMessageContaining("not found");
        assertThat(service.get(new GetInput("p0")).policy()).isSameAs(shared);
        assertThatThrownBy(() -> service.get(new GetInput("none")))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        TenantContext.set("orgB");
        assertThat(service.get(new GetInput("p9")).policy()).isSameAs(foreign);
    }

    @Test
    void listReturnsAllAndPagedVariants() {
        SecurityPolicy p = SecurityPolicy.builder().id("p").build();
        when(repository.findAll()).thenReturn(List.of(p));
        assertThat(service.list(new ListInput()).policies()).containsExactly(p);

        Page<SecurityPolicy> page = new PageImpl<>(List.of(p));
        when(repository.findAll(any(Pageable.class))).thenReturn(page);
        when(repository.search(eq("pol"), eq(null), any(Pageable.class))).thenReturn(page);
        when(repository.search(eq(null), eq("t"), any(Pageable.class))).thenReturn(page);

        assertThat(service.list(new ListPageInput(0, 10)).policies().content()).containsExactly(p);
        assertThat(service.list(new ListPageInput(-3, 10_000, "pol", null)).policies().totalElements()).isEqualTo(1);
        assertThat(service.list(new ListPageInput(0, 0, null, "t")).policies().content()).hasSize(1);
        verify(repository).findAll(any(Pageable.class));
    }

    @Test
    void deleteRemovesAccessiblePolicy() {
        SecurityPolicy p = SecurityPolicy.builder().id("p").tenantId("orgA").build();
        when(repository.findById("p")).thenReturn(Optional.of(p));
        TenantContext.set("orgA");
        service.delete(new DeleteInput("p"));
        verify(repository).delete(p);

        TenantContext.set("orgB");
        assertThatThrownBy(() -> service.delete(new DeleteInput("p"))).isInstanceOf(UamException.class);
    }
}
