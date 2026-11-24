package com.heuristiq.uam.cache;

import com.heuristiq.uam.auth.MyPrivilegesService;
import com.heuristiq.uam.auth.MyPrivilegesServiceUseCase;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.permissions.PermissionRepository;
import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.users.repository.UamUserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The real Spring wiring: property binding and validation, the primary decorators, the repository post-processor and meters. */
class UamCacheContextTest {

    private final TenantRepository rawTenants = mock(TenantRepository.class);

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(UamCacheConfig.class, MyPrivilegesService.class, CachingMyPrivilegesService.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean("users", UamUserRepository.class, () -> mock(UamUserRepository.class))
                .withBean("roles", UamRoleRepository.class, () -> mock(UamRoleRepository.class))
                .withBean("permissions", PermissionRepository.class, () -> mock(PermissionRepository.class))
                .withBean("tenants", TenantRepository.class, () -> rawTenants);
    }

    @Test
    void decoratorIsPrimaryRepositoriesAreWrappedAndMetersAreBound() {
        when(rawTenants.findById("t1")).thenReturn(Optional.of(Tenant.builder().id("t1").build()));
        runner().withPropertyValues("railcab.uam.cache.tenants.ttl-seconds=5").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MyPrivilegesServiceUseCase.class)).isInstanceOf(CachingMyPrivilegesService.class);
            TenantRepository tenants = context.getBean(TenantRepository.class);
            assertThat(tenants).isInstanceOf(CachingTenantRepository.class);
            tenants.findById("t1");
            tenants.findById("t1");
            verify(rawTenants, times(1)).findById("t1");
            assertThat(context.getBean(MeterRegistry.class).find("cache.gets").tag("cache", UamCacheConstants.TENANTS).functionCounter()).isNotNull();
            assertThat(context.getBean(UamCacheProperties.class).getTenants().getTtlSeconds()).isEqualTo(5);
        });
    }

    @Test
    void globalSwitchTurnsTheBoundCachesIntoPassThrough() {
        when(rawTenants.findById("t1")).thenReturn(Optional.of(Tenant.builder().id("t1").build()));
        runner().withPropertyValues("railcab.uam.cache.enabled=false").run(context -> {
            TenantRepository tenants = context.getBean(TenantRepository.class);
            tenants.findById("t1");
            tenants.findById("t1");
            verify(rawTenants, times(2)).findById("t1");
        });
    }

    @Test
    void anOutOfRangeSettingStopsStartup() {
        runner().withPropertyValues("railcab.uam.cache.my-privileges.ttl-seconds=0").run(context -> assertThat(context).hasFailed());
    }
}
