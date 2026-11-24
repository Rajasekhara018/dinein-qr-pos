package com.heuristiq.uam.application.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The original catalogue codes remain stable; additional product groups have explicit privilege prefixes. */
class ApplicationCatalogueTest {

    @Test
    void theCatalogueIsExactlyTheFiveAgreedCodesInDisplayOrder() {
        assertThat(ApplicationCatalogue.codes().subList(0, 5)).containsExactly("PASUPU_KUMKUMA_MATRIMONY_APP", "BESTRIDE_RIDER_APP",
                "FOOD_DELIVER_APP", "BEST_PURCHASE_APP", "CENTRALISED_WALLET_APP");
    }

    @Test
    void additionalBusinessProductsHaveTheirOwnPrivilegeGroups() {
        assertThat(ApplicationCatalogue.codes()).hasSize(7);
        assertThat(ApplicationCatalogue.find("TRAIN_BOOKING_APP")).get().extracting(ApplicationCatalogueEntry::privilegePrefix).isEqualTo("TRAIN");
        assertThat(ApplicationCatalogue.find("FRANCHISE_MANAGEMENT_APP")).get().extracting(ApplicationCatalogueEntry::privilegePrefix).isEqualTo("FRANCHISE");
        assertThat(ApplicationCatalogue.find("BEST_PURCHASE_APP")).get().extracting(ApplicationCatalogueEntry::privilegePrefix).isEqualTo("IMS");
        assertThat(ApplicationCatalogue.contains("INVENTORY_APP")).isFalse();
        assertThat(ApplicationCatalogue.contains("ECOMMERCE_APP")).isFalse();
    }

    @Test
    void everyEntryHasANameDescriptionTypeIconAndAUniqueOrder() {
        Set<Integer> orders = new HashSet<>();
        for (ApplicationCatalogueEntry entry : ApplicationCatalogue.ENTRIES) {
            assertThat(entry.name()).isNotBlank();
            assertThat(entry.description()).isNotBlank();
            assertThat(entry.type()).isNotNull();
            assertThat(entry.iconHint()).isNotBlank();
            assertThat(orders.add(entry.displayOrder())).as("unique display order for %s", entry.code()).isTrue();
        }
    }

    @Test
    void findIgnoresCaseAndSpacesAndKnowsNoOtherCode() {
        assertThat(ApplicationCatalogue.find(" best_purchase_app ")).map(ApplicationCatalogueEntry::code).hasValue("BEST_PURCHASE_APP");
        assertThat(ApplicationCatalogue.contains("Centralised_Wallet_App")).isTrue();
        for (String retired : List.of("MATRIMONY", "PASUPU_KUNKUMA_MATRIMONY", "PASUPU_KUNKUMA_MATRIMONY_APP", "RIDER", "")) {
            assertThat(ApplicationCatalogue.contains(retired)).as(retired).isFalse();
        }
        assertThat(ApplicationCatalogue.find(null)).isEmpty();
    }
}
