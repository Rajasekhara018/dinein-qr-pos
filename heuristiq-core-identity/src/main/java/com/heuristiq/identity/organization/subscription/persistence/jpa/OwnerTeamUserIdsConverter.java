package com.heuristiq.identity.organization.subscription.persistence.jpa;

import java.util.List;

import com.heuristiq.identity.organization.subscription.repository.OwnerTeamCodec;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the owner team's user ids as one comma separated text column and reads them back as a list. */
@Converter
public class OwnerTeamUserIdsConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        return OwnerTeamCodec.encode(attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        return OwnerTeamCodec.decode(dbData);
    }
}
