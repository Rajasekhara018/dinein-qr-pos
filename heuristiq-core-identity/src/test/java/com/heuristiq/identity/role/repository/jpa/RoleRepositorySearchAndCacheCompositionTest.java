package com.heuristiq.identity.role.repository.jpa;

import com.heuristiq.identity.cache.SearchAspectCacheHarness;
import com.heuristiq.identity.cache.UamCaches;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import com.heuristiq.identity.search.SearchEventPublisher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Same proof for roles: the real JPA role adapter, the real search aspect and the cache post-processor together. */
class RoleRepositorySearchAndCacheCompositionTest {

    @Test
    void saveThroughTheInjectedPortIndexesTheRoleAndEvictsAllPrivileges() {
        UamRoleJpaSpringRepository spring = mock(UamRoleJpaSpringRepository.class);
        when(spring.save(any())).thenAnswer(call -> call.getArgument(0));

        SearchAspectCacheHarness.runner()
                .withBean("roleRepository", UamRoleRepository.class, () -> new UamRoleJpaRepositoryAdapter(spring))
                .run(context -> {
                    UamRoleRepository port = context.getBean(UamRoleRepository.class);
                    UamCaches caches = context.getBean(UamCaches.class);
                    SearchEventPublisher publisher = context.getBean(SearchEventPublisher.class);

                    UamRole role = new UamRole();
                    role.setId("r1");
                    role.setCode("MANAGER");
                    role.setName("Manager");
                    role.setPermissions(List.of("A_READ"));
                    SearchAspectCacheHarness.primePrivileges(caches, "u1");

                    port.save(role);

                    verify(publisher, timeout(2000)).publishIndex(eq("ROLE"), eq("r1"), any());
                    assertThat(caches.myPrivileges().estimatedSize()).isZero();
                });
    }
}
