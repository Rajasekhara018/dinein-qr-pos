package com.heuristiq.identity.util;

import com.heuristiq.identity.helper.SessionPolicy;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class SessionPolicyAttributeConverter extends AbstractJsonAttributeConverter<SessionPolicy> {
    public SessionPolicyAttributeConverter() {
        super(SessionPolicy.class);
    }
}
