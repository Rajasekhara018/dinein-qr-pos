package com.heuristiq.identity.util;

import com.heuristiq.identity.helper.ApiSecurityPolicy;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ApiSecurityPolicyAttributeConverter extends AbstractJsonAttributeConverter<ApiSecurityPolicy> {
    public ApiSecurityPolicyAttributeConverter() {
        super(ApiSecurityPolicy.class);
    }
}
