package com.heuristiq.uam.util;

import com.heuristiq.uam.helper.AccountPolicy;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AccountPolicyAttributeConverter extends AbstractJsonAttributeConverter<AccountPolicy> {
    public AccountPolicyAttributeConverter() {
        super(AccountPolicy.class);
    }
}
