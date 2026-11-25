package com.heuristiq.identity.organization.legalprofile.domain;

import static com.heuristiq.identity.organization.legalprofile.domain.LegalProfileConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.heuristiq.identity.organization.legalprofile.domain.LegalProfileDraft.AddressDraft;
import com.heuristiq.identity.organization.legalprofile.domain.LegalProfileDraft.TaxIdDraft;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Normalisation and every validation rule of the legal profile, with the message key and field each refusal names. */
class LegalProfileValidatorTest {

    private static LegalProfileDraft draft(List<TaxIdDraft> taxIds, List<AddressDraft> addresses) {
        return new LegalProfileDraft("PRIVATE_COMPANY", "Acme Mobility Pvt Ltd", "Acme Rides", "U12345KA2020PTC123456", "IN", true,
                taxIds, addresses);
    }

    private static LegalProfileDraft withTax(String type, String value) {
        return draft(List.of(new TaxIdDraft(type, value)), null);
    }

    private static LegalProfileDraft withAddress(AddressDraft address) {
        return draft(null, List.of(address));
    }

    private static AddressDraft address(String country, String region, String postalCode) {
        return new AddressDraft("REGISTERED", "12 MG Road", null, "City", region, postalCode, country);
    }

    private static void refused(ThrowingCallable call, String key, String field) {
        assertThatThrownBy(call).isInstanceOf(LegalProfileException.class)
                .hasMessage(key)
                .satisfies(e -> {
                    assertThat(((LegalProfileException) e).kind()).isEqualTo(LegalProfileException.Kind.INVALID);
                    assertThat(((LegalProfileException) e).field()).isEqualTo(field);
                });
    }

    @Test
    void aFullProfileIsAcceptedAndNormalised() {
        LegalProfile profile = LegalProfileValidator.validate(new LegalProfileDraft(" private_company ", "  Acme Mobility Pvt Ltd ", "  ",
                " U12345KA2020PTC123456 ", " in ", null,
                List.of(new TaxIdDraft(" gstin ", " 29abcde 1234f1z5 "), new TaxIdDraft("pan", "abcde\t1234 f")),
                List.of(new AddressDraft(" operating ", " 1 Main St ", "", " Austin ", " TX ", " 73301-1234 ", " us "),
                        new AddressDraft("registered", "12 MG Road", " Floor 2 ", "Bengaluru", "Karnataka", "560001", "IN"))));

        assertThat(profile.entityType()).isEqualTo(LegalEntityType.PRIVATE_COMPANY);
        assertThat(profile.legalName()).isEqualTo("Acme Mobility Pvt Ltd");
        assertThat(profile.tradingName()).isNull();
        assertThat(profile.registrationNumber()).isEqualTo("U12345KA2020PTC123456");
        assertThat(profile.registrationCountry()).isEqualTo("IN");
        assertThat(profile.taxRegistered()).isFalse();
        // Sets are kept in a fixed order (type / kind), whatever order they were sent in.
        assertThat(profile.taxIds()).containsExactly(new TaxId(TaxIdType.PAN, "ABCDE1234F"), new TaxId(TaxIdType.GSTIN, "29ABCDE1234F1Z5"));
        assertThat(profile.addresses()).containsExactly(
                new LegalAddress(AddressKind.REGISTERED, "12 MG Road", "Floor 2", "Bengaluru", "Karnataka", "560001", "IN"),
                new LegalAddress(AddressKind.OPERATING, "1 Main St", null, "Austin", "TX", "73301-1234", "US"));
    }

    @Test
    void anEmptyOrMissingDraftIsAnEmptyProfile() {
        assertThat(LegalProfileValidator.validate(null)).isEqualTo(LegalProfile.empty(null));
        assertThat(LegalProfileValidator.validate(new LegalProfileDraft("", " ", null, null, "", false, List.of(), List.of())))
                .isEqualTo(LegalProfile.empty(null));
    }

    @Test
    void taxRegisteredTrueIsKept() {
        assertThat(LegalProfileValidator.validate(draft(null, null)).taxRegistered()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"INDIVIDUAL", "SOLE_PROPRIETOR", "PARTNERSHIP", "LLP", "PRIVATE_COMPANY", "PUBLIC_COMPANY", "NON_PROFIT", "GOVERNMENT", "OTHER"})
    void everyEntityTypeIsAccepted(String type) {
        assertThat(LegalProfileValidator.validate(new LegalProfileDraft(type.toLowerCase(), null, null, null, null, null, null, null))
                .entityType()).isEqualTo(LegalEntityType.valueOf(type));
    }

    @Test
    void anUnknownEntityTypeIsRefused() {
        refused(() -> LegalProfileValidator.validate(new LegalProfileDraft("CORPORATION", null, null, null, null, null, null, null)),
                ERROR_INVALID_ENTITY_TYPE, FIELD_ENTITY_TYPE);
    }

    @Test
    void textsLongerThanTheirLimitAreRefused() {
        refused(() -> LegalProfileValidator.validate(new LegalProfileDraft(null, "x".repeat(201), null, null, null, null, null, null)),
                ERROR_TOO_LONG, FIELD_LEGAL_NAME);
        refused(() -> LegalProfileValidator.validate(new LegalProfileDraft(null, null, "x".repeat(201), null, null, null, null, null)),
                ERROR_TOO_LONG, FIELD_TRADING_NAME);
        refused(() -> LegalProfileValidator.validate(new LegalProfileDraft(null, null, null, "1".repeat(51), null, null, null, null)),
                ERROR_TOO_LONG, FIELD_REGISTRATION_NUMBER);
        // Exactly at the limit is fine (after trimming).
        assertThat(LegalProfileValidator.validate(new LegalProfileDraft(null, " " + "x".repeat(200) + " ", "y".repeat(200), "1".repeat(50),
                null, null, null, null)).legalName()).hasSize(200);
    }

    @Test
    void aRegistrationNumberMustStartWithALetterOrDigitAndUseOnlyAllowedCharacters() {
        assertThat(LegalProfileValidator.validate(new LegalProfileDraft(null, null, null, "ab 12/34.5-6", null, null, null, null))
                .registrationNumber()).isEqualTo("ab 12/34.5-6");
        refused(() -> LegalProfileValidator.validate(new LegalProfileDraft(null, null, null, "-123", null, null, null, null)),
                ERROR_INVALID_REGISTRATION_NUMBER, FIELD_REGISTRATION_NUMBER);
        refused(() -> LegalProfileValidator.validate(new LegalProfileDraft(null, null, null, "12#4", null, null, null, null)),
                ERROR_INVALID_REGISTRATION_NUMBER, FIELD_REGISTRATION_NUMBER);
    }

    @Test
    void theRegistrationCountryMustBeAnIsoCode() {
        refused(() -> LegalProfileValidator.validate(new LegalProfileDraft(null, null, null, null, "XX", null, null, null)),
                ERROR_INVALID_COUNTRY, FIELD_REGISTRATION_COUNTRY);
        refused(() -> LegalProfileValidator.validate(new LegalProfileDraft(null, null, null, null, "IND", null, null, null)),
                ERROR_INVALID_COUNTRY, FIELD_REGISTRATION_COUNTRY);
    }

    @ParameterizedTest(name = "{0} {1} is accepted as {2}")
    @CsvSource({
            "PAN, abcde1234f, ABCDE1234F",
            "GSTIN, 29ABCDE1234F1Z5, 29ABCDE1234F1Z5",
            "TAN, ABCD12345E, ABCD12345E",
            "TRN, 100 123 456 700 003, 100123456700003",
            "VAT_SA, 300000000000003, 300000000000003",
            "VAT_GB, GB123456789, GB123456789",
            "VAT_GB, 123456789012, 123456789012",
            "VAT_GB, gbgd123, GBGD123",
            "VAT_GB, HA123, HA123",
            "CRN_GB, 12345678, 12345678",
            "CRN_GB, sc123456, SC123456",
            "EIN, 12-3456789, 12-3456789",
            "EIN, 123456789, 123456789",
            "TAX_ID, abc-12/3.4, ABC-12/3.4",
    })
    void wellFormedTaxIdsAreAcceptedAfterNormalisation(String type, String value, String stored) {
        assertThat(LegalProfileValidator.validate(withTax(type, value)).taxIds())
                .containsExactly(new TaxId(TaxIdType.valueOf(type), stored));
    }

    @ParameterizedTest(name = "{0} {1} is refused")
    @CsvSource({
            "PAN, ABCD12345F",
            "PAN, ABCDE1234",
            "GSTIN, 29ABCDE1234F0Z5",
            "GSTIN, 29ABCDE1234F1X5",
            "TAN, ABCDE1234F",
            "TRN, 10012345670000",
            "VAT_SA, 400000000000003",
            "VAT_SA, 300000000000004",
            "VAT_GB, GB12345678",
            "VAT_GB, GD1234",
            "CRN_GB, 1234567",
            "CRN_GB, S1234567",
            "EIN, 12--3456789",
            "EIN, 1234567",
            "TAX_ID, AB",
            "TAX_ID, -ABC",
            "TAX_ID, ABC_123",
    })
    void malformedTaxIdsAreRefused(String type, String value) {
        refused(() -> LegalProfileValidator.validate(withTax(type, value)), ERROR_INVALID_TAX_ID, "taxIds[0].value");
    }

    @Test
    void aTaxIdWithoutValueIsRefused() {
        refused(() -> LegalProfileValidator.validate(withTax("PAN", null)), ERROR_INVALID_TAX_ID, "taxIds[0].value");
        refused(() -> LegalProfileValidator.validate(withTax("PAN", "   ")), ERROR_INVALID_TAX_ID, "taxIds[0].value");
    }

    @Test
    void theGenericTaxIdIsLimitedToFortyCharacters() {
        assertThat(LegalProfileValidator.validate(withTax("TAX_ID", "A".repeat(40))).taxIds()).hasSize(1);
        refused(() -> LegalProfileValidator.validate(withTax("TAX_ID", "A".repeat(41))), ERROR_INVALID_TAX_ID, "taxIds[0].value");
    }

    @Test
    void anUnknownOrMissingTaxTypeIsRefused() {
        refused(() -> LegalProfileValidator.validate(withTax("VAT", "123")), ERROR_INVALID_TAX_TYPE, "taxIds[0].type");
        refused(() -> LegalProfileValidator.validate(withTax(" ", "123")), ERROR_INVALID_TAX_TYPE, "taxIds[0].type");
        refused(() -> LegalProfileValidator.validate(draft(Arrays.asList((TaxIdDraft) null), null)), ERROR_INVALID_TAX_TYPE, "taxIds[0].type");
    }

    @Test
    void aTaxTypeAtMostOnce() {
        refused(() -> LegalProfileValidator.validate(draft(List.of(new TaxIdDraft("PAN", "ABCDE1234F"), new TaxIdDraft("pan", "BCDEF2345G")),
                null)), ERROR_DUPLICATE_TAX_TYPE, "taxIds[1].type");
    }

    @Test
    void atMostTenTaxIds() {
        List<TaxIdDraft> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add(new TaxIdDraft("TAX_ID", "ID" + i + "00"));
        }
        refused(() -> LegalProfileValidator.validate(draft(eleven, null)), ERROR_TOO_MANY_TAX_IDS, FIELD_TAX_IDS);
        // One of every type (nine) is fine.
        List<TaxIdDraft> ten = List.of(new TaxIdDraft("PAN", "ABCDE1234F"), new TaxIdDraft("GSTIN", "29ABCDE1234F1Z5"),
                new TaxIdDraft("TAN", "ABCD12345E"), new TaxIdDraft("TRN", "100123456700003"), new TaxIdDraft("VAT_SA", "300000000000003"),
                new TaxIdDraft("VAT_GB", "GB123456789"), new TaxIdDraft("CRN_GB", "12345678"), new TaxIdDraft("EIN", "12-3456789"),
                new TaxIdDraft("TAX_ID", "ABC123"));
        assertThat(LegalProfileValidator.validate(draft(ten, null)).taxIds()).hasSize(9);
    }

    @Test
    void aTaxTypesCountryNeedNotBeTheRegistrationCountry() {
        // Registered in IN, with a UK VAT number: accepted (the country of a tax type is informational).
        assertThat(LegalProfileValidator.validate(withTax("VAT_GB", "GB123456789")).registrationCountry()).isEqualTo("IN");
        assertThat(TaxIdType.VAT_GB.country()).isEqualTo("GB");
        assertThat(TaxIdType.TAX_ID.country()).isNull();
    }

    @ParameterizedTest(name = "{0} postal code ''{2}'' is accepted as ''{3}''")
    @CsvSource(value = {
            "IN | Karnataka | 560001 | 560001",
            "US | TX | 73301 | 73301",
            "US | TX | 73301-1234 | 73301-1234",
            "SA | | 12345 | 12345",
            "SA | | 12345-6789 | 12345-6789",
            "GB | | sw1a 1aa | SW1A 1AA",
            "GB | | M11AE | M11AE",
            "AE | | | ",
            "QA | | | ",
            "HK | | | ",
            "DE | | 10115 | 10115",
            "CA | | k1a 0b1 | K1A 0B1",
            "FR | | | ",
    }, delimiter = '|')
    void postalCodesFollowTheirCountrysRule(String country, String region, String postalCode, String stored) {
        LegalAddress address = LegalProfileValidator.validate(withAddress(address(country, region, postalCode))).addresses().get(0);
        assertThat(address.postalCode()).isEqualTo(stored);
        assertThat(address.country()).isEqualTo(country);
    }

    @ParameterizedTest(name = "{0} postal code ''{2}'' is refused")
    @CsvSource(value = {
            "IN | Karnataka | 060001",
            "IN | Karnataka | 56000",
            "IN | Karnataka | ",
            "US | TX | 7330",
            "US | TX | ",
            "SA | | 1234",
            "SA | | ",
            "GB | | 12345",
            "GB | | ",
            "AE | | 00000",
            "QA | | 12345",
            "HK | | 999077",
            "DE | | -10115",
            "DE | | 1",
    }, delimiter = '|')
    void postalCodesBreakingTheirCountrysRuleAreRefused(String country, String region, String postalCode) {
        refused(() -> LegalProfileValidator.validate(withAddress(address(country, region, postalCode))),
                ERROR_INVALID_POSTAL_CODE, "addresses[0].postalCode");
    }

    @Test
    void aPostalCodeLongerThanTwelveIsTooLong() {
        refused(() -> LegalProfileValidator.validate(withAddress(address("DE", null, "1234567890123"))), ERROR_TOO_LONG,
                "addresses[0].postalCode");
    }

    @Test
    void anAddressNeedsLine1CityAndCountryAndARegionInIndiaAndTheUs() {
        refused(() -> LegalProfileValidator.validate(withAddress(new AddressDraft("REGISTERED", " ", null, "City", "R", "560001", "IN"))),
                ERROR_ADDRESS_REQUIRED, "addresses[0].line1");
        refused(() -> LegalProfileValidator.validate(withAddress(new AddressDraft("REGISTERED", "L1", null, null, "R", "560001", "IN"))),
                ERROR_ADDRESS_REQUIRED, "addresses[0].city");
        refused(() -> LegalProfileValidator.validate(withAddress(new AddressDraft("REGISTERED", "L1", null, "City", "R", "560001", ""))),
                ERROR_ADDRESS_REQUIRED, "addresses[0].country");
        refused(() -> LegalProfileValidator.validate(withAddress(address("IN", " ", "560001"))), ERROR_ADDRESS_REQUIRED, "addresses[0].region");
        refused(() -> LegalProfileValidator.validate(withAddress(address("US", null, "73301"))), ERROR_ADDRESS_REQUIRED, "addresses[0].region");
        // Elsewhere the region is optional.
        assertThat(LegalProfileValidator.validate(withAddress(address("AE", null, null))).addresses().get(0).region()).isNull();
    }

    @Test
    void addressTextsHaveLimits() {
        refused(() -> LegalProfileValidator.validate(withAddress(new AddressDraft("REGISTERED", "x".repeat(201), null, "C", null, null, "FR"))),
                ERROR_TOO_LONG, "addresses[0].line1");
        refused(() -> LegalProfileValidator.validate(withAddress(new AddressDraft("REGISTERED", "L", "x".repeat(201), "C", null, null, "FR"))),
                ERROR_TOO_LONG, "addresses[0].line2");
        refused(() -> LegalProfileValidator.validate(withAddress(new AddressDraft("REGISTERED", "L", null, "x".repeat(101), null, null, "FR"))),
                ERROR_TOO_LONG, "addresses[0].city");
        refused(() -> LegalProfileValidator.validate(withAddress(new AddressDraft("REGISTERED", "L", null, "C", "x".repeat(101), null, "FR"))),
                ERROR_TOO_LONG, "addresses[0].region");
    }

    @Test
    void anAddressCountryMustBeAnIsoCode() {
        refused(() -> LegalProfileValidator.validate(withAddress(address("ZZ", null, null))), ERROR_INVALID_COUNTRY, "addresses[0].country");
    }

    @Test
    void anAddressKindMustBeKnownAndAppearAtMostOnce() {
        refused(() -> LegalProfileValidator.validate(withAddress(new AddressDraft("BILLING", "L", null, "C", null, null, "FR"))),
                ERROR_INVALID_ADDRESS_KIND, "addresses[0].kind");
        refused(() -> LegalProfileValidator.validate(withAddress(new AddressDraft(null, "L", null, "C", null, null, "FR"))),
                ERROR_INVALID_ADDRESS_KIND, "addresses[0].kind");
        refused(() -> LegalProfileValidator.validate(draft(null, Arrays.asList((AddressDraft) null))),
                ERROR_INVALID_ADDRESS_KIND, "addresses[0].kind");
        refused(() -> LegalProfileValidator.validate(draft(null, List.of(address("FR", null, null),
                new AddressDraft("registered", "L", null, "C", null, null, "FR")))), ERROR_DUPLICATE_ADDRESS_KIND, "addresses[1].kind");
    }

    @Test
    void aThirdAddressIsAlwaysARepeatedKind() {
        AddressDraft operating = new AddressDraft("OPERATING", "L", null, "C", null, null, "FR");
        refused(() -> LegalProfileValidator.validate(draft(null, List.of(address("FR", null, null), operating, operating))),
                ERROR_DUPLICATE_ADDRESS_KIND, "addresses[2].kind");
    }
}
