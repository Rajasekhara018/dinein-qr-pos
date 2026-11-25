package com.heuristiq.identity.helper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A typed full name is stored as first and last name, split at the last space; one word stays a first name. */
class PersonNamesTest {

    @Test
    void theLastWordIsTheLastNameAndEverythingBeforeItTheFirstName() {
        RName name = PersonNames.split("QA Retest UI Owner");

        assertThat(name.getFirstName()).isEqualTo("QA Retest UI");
        assertThat(name.getMiddleName()).isEmpty();
        assertThat(name.getLastName()).isEqualTo("Owner");
        assertThat(name.getFullName()).isEqualTo("QA Retest UI Owner");
    }

    @Test
    void twoWordsGiveOneFirstAndOneLastName() {
        RName name = PersonNames.split("Ravi Kumar");

        assertThat(name.getFirstName()).isEqualTo("Ravi");
        assertThat(name.getLastName()).isEqualTo("Kumar");
    }

    @Test
    void aSingleWordStaysAFirstNameWithABlankLastNameAndDoesNotFail() {
        RName name = PersonNames.split("Ravi");

        assertThat(name.getFirstName()).isEqualTo("Ravi");
        assertThat(name.getLastName()).isEmpty();
        assertThat(PersonNames.display(name)).isEqualTo("Ravi");
    }

    @Test
    void extraSpacesAreTrimmedAndCollapsed() {
        RName name = PersonNames.split("   Anil    Kumar   Reddy  ");

        assertThat(name.getFirstName()).isEqualTo("Anil Kumar");
        assertThat(name.getLastName()).isEqualTo("Reddy");
    }

    @Test
    void aNullOrBlankNameIsNoName() {
        assertThat(PersonNames.split(null)).isNull();
        assertThat(PersonNames.split("")).isNull();
        assertThat(PersonNames.split("   ")).isNull();
        assertThat(PersonNames.display(null)).isNull();
        assertThat(PersonNames.display(new RName(" "))).isNull();
    }

    @Test
    void theDisplayNameIsThePartsJoinedByOneSpace() {
        assertThat(PersonNames.display(new RName("Anil Kumar", "", "Reddy"))).isEqualTo("Anil Kumar Reddy");
        assertThat(PersonNames.display(new RName("Anil", "K", "Reddy"))).isEqualTo("Anil K Reddy");
        assertThat(PersonNames.display(PersonNames.split("Ravi"))).isEqualTo("Ravi");
    }

    @Test
    void namesInOtherScriptsSplitTheSameWay() {
        RName name = PersonNames.split("రవి కుమార్ రెడ్డి");

        assertThat(name.getFirstName()).isEqualTo("రవి కుమార్");
        assertThat(name.getLastName()).isEqualTo("రెడ్డి");
    }
}
