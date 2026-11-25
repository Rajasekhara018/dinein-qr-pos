package com.heuristiq.identity.organization.subscription.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class OwnerTeamCodecTest {

    @Test
    void roundTripsIds() {
        assertThat(OwnerTeamCodec.encode(List.of("a", "b"))).isEqualTo("a,b");
        assertThat(OwnerTeamCodec.decode("a,b")).containsExactly("a", "b");
    }

    @Test
    void nothingStoredMeansNoOwners() {
        assertThat(OwnerTeamCodec.encode(null)).isNull();
        assertThat(OwnerTeamCodec.encode(List.of())).isNull();
        assertThat(OwnerTeamCodec.decode(null)).isEmpty();
        assertThat(OwnerTeamCodec.decode("  ")).isEmpty();
    }

    @Test
    void decodingDropsBlanksAndDuplicates() {
        assertThat(OwnerTeamCodec.decode(" a ,, b,a ")).containsExactly("a", "b");
    }
}
