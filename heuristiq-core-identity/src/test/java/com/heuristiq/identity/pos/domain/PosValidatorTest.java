package com.heuristiq.identity.pos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.heuristiq.identity.organization.legalprofile.domain.TaxId;
import com.heuristiq.identity.organization.legalprofile.domain.TaxIdType;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** Normalisation and the country-specific validation rules of legal entities and branches. */
class PosValidatorTest {

    private static LegalEntityDraft validDe() {
        return new LegalEntityDraft("DE", "Muster GmbH", null, List.of(new TaxId(TaxIdType.VAT_DE, "DE123456789")),
                new PosAddress("Hauptstr. 1", null, "Berlin", null, "10115", "DE"), "ber", null);
    }

    private static LegalEntity owner(String country, PosStatus status) {
        return new LegalEntity("le1", "t1", country, "Owner", PosCountry.of(country).orElseThrow().currency(), List.of(),
                new PosAddress("L", null, "C", null, null, country), "INV", 1, status, 1L, Instant.EPOCH, "u");
    }

    private static BranchDraft branch(String locale, String timeZone, String phone) {
        return new BranchDraft("le1", "br-1", "Main", timeZone, locale, null, phone, null,
                new PosAddress("Rue 1", null, "Paris", null, "75001", "FR"));
    }

    private static void invalid(ThrowingCallable call, String field) {
        assertThatThrownBy(call).isInstanceOf(PosException.class).satisfies(e -> {
            assertThat(((PosException) e).kind()).isEqualTo(PosException.Kind.INVALID);
            assertThat(((PosException) e).field()).isEqualTo(field);
        });
    }

    @Test
    void fillsCurrencyAndFinancialYearFromCountry() {
        LegalEntityDraft d = PosValidator.legalEntity(validDe());
        assertThat(d.currency()).isEqualTo("EUR");
        assertThat(d.financialYearStartMonth()).isEqualTo(1);
        assertThat(d.invoicePrefix()).isEqualTo("BER");
    }

    @Test
    void currencyMustMatchCountry() {
        LegalEntityDraft v = validDe();
        invalid(() -> PosValidator.legalEntity(new LegalEntityDraft("DE", v.legalName(), "GBP", v.taxIds(), v.address(),
                v.invoicePrefix(), null)), "currency");
    }

    @Test
    void unsupportedCountryIsRefused() {
        LegalEntityDraft v = validDe();
        invalid(() -> PosValidator.legalEntity(new LegalEntityDraft("IT", v.legalName(), null, v.taxIds(), v.address(),
                v.invoicePrefix(), null)), "country");
    }

    @Test
    void taxIdOfAnotherCountryIsRefused() {
        LegalEntityDraft v = validDe();
        invalid(() -> PosValidator.legalEntity(new LegalEntityDraft("DE", v.legalName(), null,
                List.of(new TaxId(TaxIdType.GSTIN, "29ABCDE1234F1Z5")), v.address(), v.invoicePrefix(), null)), "taxIds[0]");
    }

    @Test
    void regionRequiredInIndia() {
        invalid(() -> PosValidator.legalEntity(new LegalEntityDraft("IN", "Acme", null, List.of(),
                new PosAddress("1 MG Road", null, "Bengaluru", null, "560001", "IN"), "ACM", null)), "address.region");
    }

    @Test
    void branchDefaultsLocaleAndLanguages() {
        BranchDraft b = PosValidator.branch(branch(null, "Europe/Paris", "+33123456789"), owner("FR", PosStatus.ACTIVE));
        assertThat(b.locale()).isEqualTo("fr-FR");
        assertThat(b.languages()).containsExactly("fr");
        assertThat(b.code()).isEqualTo("BR-1");
    }

    @Test
    void branchLocaleRegionMustMatchCountry() {
        LegalEntity fr = owner("FR", PosStatus.ACTIVE);
        invalid(() -> PosValidator.branch(branch("en-US", "Europe/Paris", null), fr), "locale");
        assertThat(PosValidator.branch(branch("en-FR", "Europe/Paris", null), fr).locale()).isEqualTo("en-FR");
    }

    @Test
    void offsetTimeZoneIsRefused() {
        LegalEntity fr = owner("FR", PosStatus.ACTIVE);
        invalid(() -> PosValidator.branch(branch(null, "+05:30", null), fr), "timeZone");
        assertThat(PosValidator.branch(branch(null, "Asia/Kolkata", null), fr).timeZone()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void inactiveLegalEntityCannotGetBranches() {
        invalid(() -> PosValidator.branch(branch(null, "Europe/Paris", null), owner("FR", PosStatus.INACTIVE)), "legalEntityId");
    }

    @Test
    void phoneMustBeE164() {
        LegalEntity fr = owner("FR", PosStatus.ACTIVE);
        invalid(() -> PosValidator.branch(branch(null, "Europe/Paris", "98480 12345"), fr), "phone");
        assertThat(PosValidator.branch(branch(null, "Europe/Paris", "+919848012345"), fr).phone()).isEqualTo("+919848012345");
    }
}
