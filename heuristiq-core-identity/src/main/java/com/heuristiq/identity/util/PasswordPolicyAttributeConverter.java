package com.heuristiq.uam.util;

import com.heuristiq.uam.helper.PasswordPolicy;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class PasswordPolicyAttributeConverter extends AbstractJsonAttributeConverter<PasswordPolicy> {
    public PasswordPolicyAttributeConverter() {
        super(PasswordPolicy.class);
    }
}
