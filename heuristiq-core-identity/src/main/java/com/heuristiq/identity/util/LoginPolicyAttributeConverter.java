package com.heuristiq.identity.util;

import com.heuristiq.identity.helper.LoginPolicy;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class LoginPolicyAttributeConverter extends AbstractJsonAttributeConverter<LoginPolicy> {
    public LoginPolicyAttributeConverter() {
        super(LoginPolicy.class);
    }
}
