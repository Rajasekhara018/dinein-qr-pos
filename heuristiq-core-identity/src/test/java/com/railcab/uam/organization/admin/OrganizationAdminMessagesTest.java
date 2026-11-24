package com.heuristiq.uam.organization.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;


class OrganizationAdminMessagesTest {

    @Test
    void aNewAccountIsToldItsEmailAndOneTimePassword() {
        String text = OrganizationAdminMessages.signInDetails("asha@acme.com", "Pa55word#Temp14", "https://realboom.example/auth/login");

        assertThat(text).contains("https://realboom.example/auth/login", "asha@acme.com", "Pa55word#Temp14", "change it");
    }

    @Test
    void anExistingAccountIsToldToUseItsOwnPasswordAndNeverSeesOne() {
        String text = OrganizationAdminMessages.signInDetails("asha@acme.com", null, "");

        assertThat(text).contains("asha@acme.com", "existing password").doesNotContain("one-time");
    }
}
