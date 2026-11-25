package com.heuristiq.identity.franchiseaccess;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** The privileges a franchise role may carry exist in privilege.json, belong to the franchise service, and are all "mine" or portal calls. */
class FranchiseAccessCatalogTest {
    private static final Path CATALOG = Path.of("src/main/resources/privilege.json");

    @Test
    void everyAllowedPrivilegeExistsInTheCatalogueAsAFranchiseServiceCall() throws IOException {
        Map<String, String> serviceByName = new HashMap<>();
        for (JsonNode entry : new ObjectMapper().readTree(Files.readString(CATALOG))) {
            serviceByName.put(entry.path("name").asText(), entry.path("service").asText());
        }

        for (String privilege : FranchiseAccessConstants.DEFAULT_ALLOWED_PRIVILEGE_PREFIXES) {
            assertThat(serviceByName).as(privilege).containsKey(privilege);
            // The wallet statement of the person's own wallet is the one thing owners and agents also open outside the franchise service.
            // The own wallet statement and the own in-app notifications are the things owners and agents also open outside the franchise service.
            // an owner onboards their own drivers through the onboarding service (it checks the franchise is theirs)
            String expected = privilege.startsWith("WALLET_") ? "rider-wallet" : privilege.startsWith("NMS_IN_APP_") ? "nms"
                    : "FRANCHISE_DRIVER_ONBOARDING_CREATE".equals(privilege) ? "rider-onboarding"
                    // the person's own account screen (UAM) and their own workflow inbox
                    : privilege.startsWith("USER_SELF_") ? "uam" : "WORKFLOW_TASK_LIST".equals(privilege) ? "rider-workflow"
                    // the matrimony agent desk and franchise management, served by matrimonial-api
                    : privilege.startsWith("MATRIMONY_") ? "matrimony" : "franchise";
            assertThat(serviceByName.get(privilege)).as(privilege + " service").isEqualTo(expected);
        }
    }

    @Test
    void theAllowListHoldsTheOwnWalletStatementAndTheOwnFranchiseAccountingAndNoOtherWalletPrivilege() {
        assertThat(FranchiseAccessConstants.DEFAULT_ALLOWED_PRIVILEGE_PREFIXES).filteredOn(p -> p.startsWith("WALLET_"))
                .containsExactlyInAnyOrder("WALLET_SELF_STATEMENT_READ", "WALLET_SELF_STATEMENT_MONTH_LIST",
                        "WALLET_SELF_STATEMENT_SUMMARY_READ", "WALLET_SELF_STATEMENT_EXPORT_READ", "WALLET_FRANCHISE_ACCOUNTING_MINE_READ");
    }

    @Test
    void theAllowListLetsAgentsAndOwnersApproveTheirCustomersAndSeeTheirPhotos() {
        assertThat(FranchiseAccessConstants.DEFAULT_ALLOWED_PRIVILEGE_PREFIXES).contains("MATRIMONY_AGENT_CUSTOMER_ACTIVATE",
                "MATRIMONY_AGENT_CUSTOMER_PENDING_ACTIVATION_LIST", "MATRIMONY_AGENT_CUSTOMER_PHOTO_LIST", "MATRIMONY_AGENT_CUSTOMER_PHOTO_READ",
                "MATRIMONY_AGENT_CUSTOMER_PHOTO_DELETE", "MATRIMONY_AGENT_CUSTOMER_HOROSCOPE_READ");
    }

    @Test
    void theAllowListHoldsNoPlatformWideRuleApprovalOrLedgerPrivilege() {
        Set<String> forbidden = Set.of("PAYOUT_APPROVE_UPDATE", "PAYOUT_REQUEST_LIST", "PAYOUT_RULE_LIST", "PAYOUT_RULE_CREATE", "PAYOUT_RULE_UPDATE",
                "LEDGER_ENTRY_LIST", "COMMISSION_STATEMENT_READ", "FRANCHISE_LIST", "FRANCHISE_CREATE", "COMMISSION_PLAN_CREATE", "COMMISSION_PLAN_UPDATE",
                // the platform-wide accounting screens stay staff-only: an owner reads only their own franchise
                "WALLET_ACCOUNTING_FRANCHISE_READ", "WALLET_ACCOUNTING_TRANSACTION_LIST", "WALLET_ACCOUNTING_SUMMARY_READ");

        assertThat(FranchiseAccessConstants.DEFAULT_ALLOWED_PRIVILEGE_PREFIXES).doesNotContainAnyElementsOf(forbidden);
    }
}
