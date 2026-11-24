package com.heuristiq.uam.util;

import com.heuristiq.uam.helper.ApiSecurityPolicy;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ApiSecurityPolicyAttributeConverter extends AbstractJsonAttributeConverter<ApiSecurityPolicy> {
    public ApiSecurityPolicyAttributeConverter() {
        super(ApiSecurityPolicy.class);
    }
}
