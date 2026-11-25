package com.heuristiq.identity.users.repository.mongo;

import com.heuristiq.identity.helper.RPhoneNumber;
import com.heuristiq.identity.users.persistence.mongo.UamUserDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The Mongo adapter finds legacy and new-form rows for the same real number, and never a foreign-country row. */
class UamUserMongoPhoneLookupTest {
    private final UamUserMongoSpringRepository spring = mock(UamUserMongoSpringRepository.class);
    private final UamUserMongoRepositoryAdapter adapter = new UamUserMongoRepositoryAdapter(spring, mock(MongoTemplate.class));

    private static UamUserDocument row(String id, String code, String number) {
        return UamUserDocument.builder().id(id).phoneNumber(new RPhoneNumber(code, number)).build();
    }

    @Test
    void aLegacyRowIsFoundByTheInternationalInput() {
        when(spring.findAllByPhoneNumber_Number("9876543210")).thenReturn(List.of(row("legacy", null, "9876543210")));

        assertThat(adapter.findByPhone("+919876543210")).get().extracting("id").isEqualTo("legacy");
        assertThat(adapter.findByPhone("9876543210")).isPresent();
    }

    @Test
    void aNewFormRowIsFoundByTheBareInput() {
        when(spring.findAllByPhoneNumber_Number("9876543210")).thenReturn(List.of(row("new", "91", "9876543210")));

        assertThat(adapter.findByPhone("9876543210")).get().extracting("id").isEqualTo("new");
    }

    @Test
    void anotherCountrySharingTheNationalDigitsIsNotTheSamePerson() {
        when(spring.findAllByPhoneNumber_Number("4155552671")).thenReturn(List.of(row("in", "91", "4155552671")));

        assertThat(adapter.findByPhone("+14155552671")).isEmpty();
    }

    @Test
    void blankInputFindsNothing() {
        assertThat(adapter.findByPhone(" ")).isEmpty();
        assertThat(adapter.findByPhone(null)).isEmpty();
    }

    @Test
    void theCanonicalRowIsPreferredAndFoundForAnySpelling() {
        when(spring.findByPhoneE164("+919876543210")).thenReturn(java.util.Optional.of(row("canon", "91", "9876543210")));

        assertThat(adapter.findByPhone("9876543210")).get().extracting("id").isEqualTo("canon");
        assertThat(adapter.findByPhone("+91 98765 43210")).get().extracting("id").isEqualTo("canon");
    }

    @Test
    void sameNationalDigitsInAnotherCountryAreNotFoundSoBothCanBeCreated() {
        when(spring.findByPhoneE164("+919876543210")).thenReturn(java.util.Optional.of(row("in", "91", "4155552671")));
        when(spring.findAllByPhoneNumber_Number("4155552671")).thenReturn(List.of(row("in", "91", "4155552671")));

        assertThat(adapter.findByPhone("+14155552671")).isEmpty();
    }

    @Test
    void aCanonicalPhoneViolationBecomesAConflict() {
        when(spring.save(org.mockito.ArgumentMatchers.any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                "violates unique constraint", new RuntimeException("duplicate key value violates unique constraint uk_uam_user_phone_e164")));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.save(com.heuristiq.identity.users.UamUser.builder().id("u").build()))
                .isInstanceOfSatisfying(com.heuristiq.identity.common.UamException.class,
                        e -> assertThat(e.getStatus().value()).isEqualTo(409));
    }

    @Test
    void anotherIntegrityViolationIsLeftAlone() {
        when(spring.save(org.mockito.ArgumentMatchers.any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                "x", new RuntimeException("uk_uam_user_email")));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.save(com.heuristiq.identity.users.UamUser.builder().id("u").build()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
