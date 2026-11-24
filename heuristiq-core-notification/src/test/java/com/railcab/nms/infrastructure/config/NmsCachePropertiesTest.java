package com.railcab.nms.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class NmsCachePropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsAreValidAndMatchTheDocumentedValues() {
        NmsProperties props = new NmsProperties();
        assertThat(validator.validate(props)).isEmpty();
        assertThat(props.getCache().isEnabled()).isTrue();
        assertThat(props.getCache().getOrganizationProfile().getTtlSeconds()).isEqualTo(300);
        assertThat(props.getCache().getOrganizationProfile().getNotFoundTtlSeconds()).isEqualTo(30);
        assertThat(props.getCache().getTemplate().getTtlSeconds()).isEqualTo(60);
        assertThat(props.getCache().getTemplate().getNotFoundTtlSeconds()).isEqualTo(15);
    }

    @Test
    void outOfRangeValuesAreRejected() {
        NmsProperties props = new NmsProperties();
        props.getCache().getTemplate().setTtlSeconds(0);
        props.getCache().getOrganizationProfile().setMaxSize(10_000_000);
        props.getCache().getOrganizationProfile().setNotFoundTtlSeconds(-1);
        assertThat(validator.validate(props)).hasSize(3);
    }
}
