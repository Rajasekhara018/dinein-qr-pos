package com.heuristiq.identity.util;

import com.heuristiq.identity.helper.AccountPolicy;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AccountPolicyAttributeConverter extends AbstractJsonAttributeConverter<AccountPolicy> {
    public AccountPolicyAttributeConverter() {
        super(AccountPolicy.class);
    }
}
