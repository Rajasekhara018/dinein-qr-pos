package com.heuristiq.uam.util;

import com.heuristiq.uam.helper.LoginPolicy;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class LoginPolicyAttributeConverter extends AbstractJsonAttributeConverter<LoginPolicy> {
    public LoginPolicyAttributeConverter() {
        super(LoginPolicy.class);
    }
}
