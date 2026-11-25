package com.heuristiq.identity.util;

import com.heuristiq.identity.helper.PasswordPolicy;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class PasswordPolicyAttributeConverter extends AbstractJsonAttributeConverter<PasswordPolicy> {
    public PasswordPolicyAttributeConverter() {
        super(PasswordPolicy.class);
    }
}
