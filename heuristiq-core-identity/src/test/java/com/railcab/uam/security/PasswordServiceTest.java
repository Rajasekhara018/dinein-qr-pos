package com.heuristiq.uam.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordServiceTest {

    private final PasswordService passwordService = new PasswordService();

    @Test
    void hashProducesValueDistinctFromRawPassword() {
        String hash = passwordService.hash("Sup3rSecret!");
        assertThat(hash).isNotEqualTo("Sup3rSecret!");
    }

    @Test
    void matchesReturnsTrueForCorrectPassword() {
        String hash = passwordService.hash("Sup3rSecret!");
        assertThat(passwordService.matches("Sup3rSecret!", hash)).isTrue();
    }

    @Test
    void matchesReturnsFalseForIncorrectPassword() {
        String hash = passwordService.hash("Sup3rSecret!");
        assertThat(passwordService.matches("WrongPassword", hash)).isFalse();
    }

    @Test
    void theAutomaticPoolUsesEveryCoreSoProductionKeepsTwoHashThreads() {
        assertThat(PasswordService.poolSize(0, 8)).isEqualTo(8);
        assertThat(PasswordService.poolSize(0, 2)).isEqualTo(2);
        assertThat(PasswordService.poolSize(0, 1)).isEqualTo(1);
        assertThat(PasswordService.poolSize(-3, 4)).isEqualTo(4);
    }

    @Test
    void aConfiguredPoolSizeIsUsedAsIs() {
        assertThat(PasswordService.poolSize(4, 2)).isEqualTo(4);
        assertThat(PasswordService.poolSize(1, 16)).isEqualTo(1);
    }

    @Test
    void theConfiguredConstructorStillHashesAtTheConfiguredStrength() {
        PasswordService configured = new PasswordService(11, 4, 5000, 2);
        String hash = configured.hash("Sup3rSecret!");
        assertThat(hash).startsWith("$2a$11$");
        assertThat(configured.matches("Sup3rSecret!", hash)).isTrue();
    }

    @Test
    void hashIsSaltedSoRepeatedHashesDiffer() {
        String first = passwordService.hash("Sup3rSecret!");
        String second = passwordService.hash("Sup3rSecret!");
        assertThat(first).isNotEqualTo(second);
    }
}
