package com.heuristiq.uam.helper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RAddressTest {

    private static RAddress full() {
        RAddress address = new RAddress();
        address.setLines(" 12 Main Road ");
        address.setCity(" Pune ");
        address.setState("MH");
        address.setStateId("27");
        address.setCountry("India");
        address.setPincode("411001");
        address.setLandmark(" Near Park ");
        return address;
    }

    @Test
    void defaultConstructorStartsWithEmptyRegion() {
        assertThat(new RAddress().getRegion()).isEmpty();
    }

    @Test
    void fullAddressStoreJoinsNonBlankPartsInUpperCase() {
        assertThat(full().fullAddressStore()).isEqualTo("12 MAIN ROAD PUNE MH 27 INDIA 411001 NEAR PARK");
    }

    @Test
    void fullAddressStoreSkipsBlankAndNullParts() {
        RAddress address = new RAddress();
        address.setLines("  ");
        address.setCity("Pune");
        address.setState("");
        address.setPincode("411001");
        assertThat(address.fullAddressStore()).isEqualTo("PUNE 411001 ");
        assertThat(new RAddress().fullAddressStore()).isEmpty();
    }

    @Test
    void hashFormPrefixesEveryPresentPartWithHashEvenWhenBlank() {
        assertThat(full().fullAddressStoreHASH()).isEqualTo("#12 MAIN ROAD#PUNE#MH#27#INDIA#411001#NEAR PARK");
        RAddress partial = new RAddress();
        partial.setCity("pune");
        partial.setState("");
        assertThat(partial.fullAddressStoreHASH()).isEqualTo("#PUNE#");
        assertThat(new RAddress().fullAddressStoreHASH()).isEmpty();
    }

    @Test
    void addressLinesSplitStreetFromLocality() {
        RAddress address = full();
        String[] lines = address.gerAddressLines();
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo(" 12 Main Road");
        assertThat(lines[1]).isEqualTo("PUNE MH INDIA 411001 ");
        assertThat(lines[2]).isNull();
    }

    @Test
    void longStreetLineIsCutAtEightyCharacters() {
        RAddress address = new RAddress();
        address.setLines("x".repeat(120));
        assertThat(address.gerAddressLines()[0]).hasSize(80);
    }

    @Test
    void addressWithoutStreetStillBuildsLocalityLine() {
        String[] lines = new RAddress().gerAddressLines();
        assertThat(lines[0]).isNull();
        assertThat(lines[1]).isEmpty();
    }

    @Test
    void valueSemanticsFollowAllFields() {
        RAddress a = full();
        RAddress b = full();
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        b.setDistrict("Haveli");
        assertThat(a).isNotEqualTo(b);
        assertThat(a.toString()).contains("Pune");
        b.setDistrict(null);
        b.setIsoCountryCode("IN");
        assertThat(a).isNotEqualTo(b);
    }
}
