package com.heuristiq.uam.util;

import com.heuristiq.uam.helper.SessionPolicy;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class SessionPolicyAttributeConverter extends AbstractJsonAttributeConverter<SessionPolicy> {
    public SessionPolicyAttributeConverter() {
        super(SessionPolicy.class);
    }
}
