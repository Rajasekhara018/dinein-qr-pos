package com.heuristiq.gateway.identity.repository.jpa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UamUserStatusJpaRepositoryAdapterTest {

    private final GatewayUamUserJpaSpringRepository repo = mock(GatewayUamUserJpaSpringRepository.class);
    private final UamUserStatusJpaRepositoryAdapter adapter = new UamUserStatusJpaRepositoryAdapter(repo);

    @Test
    void delegatesWithTheActiveStatus() {
        when(repo.existsByIdAndStatus("u1", "ACTIVE")).thenReturn(true);

        assertThat(adapter.isActive("u1")).isTrue();
        assertThat(adapter.isActive("u2")).isFalse();
    }
}
