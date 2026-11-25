package com.heuristiq.identity.organization.legalprofile.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An organization's validated legal profile. Tax ids and addresses are sets: they are kept in a fixed order (by type / kind),
 * so two profiles with the same content are equal whatever order they were sent or stored in.
 */
public record LegalProfile(LegalEntityType entityType, String legalName, String tradingName, String registrationNumber,
                           String registrationCountry, boolean taxRegistered, List<TaxId> taxIds, List<LegalAddress> addresses) {

    public LegalProfile {
        taxIds = sorted(taxIds, Comparator.comparing(TaxId::type));
        addresses = sorted(addresses, Comparator.comparing(LegalAddress::kind));
    }

    /** The profile of an organization that has saved none yet (its legal name may still be known). */
    public static LegalProfile empty(String legalName) {
        return new LegalProfile(null, legalName, null, null, null, false, List.of(), List.of());
    }

    /** The same profile with another legal name (the legal name is kept on the organization record itself). */
    public LegalProfile withLegalName(String name) {
        return new LegalProfile(entityType, name, tradingName, registrationNumber, registrationCountry, taxRegistered, taxIds, addresses);
    }

    /** The profile as plain values, for the audit trail's before / after. */
    public Map<String, Object> toAuditMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(LegalProfileConstants.FIELD_ENTITY_TYPE, entityType == null ? null : entityType.name());
        map.put(LegalProfileConstants.FIELD_LEGAL_NAME, legalName);
        map.put(LegalProfileConstants.FIELD_TRADING_NAME, tradingName);
        map.put(LegalProfileConstants.FIELD_REGISTRATION_NUMBER, registrationNumber);
        map.put(LegalProfileConstants.FIELD_REGISTRATION_COUNTRY, registrationCountry);
        map.put(LegalProfileConstants.FIELD_TAX_REGISTERED, taxRegistered);
        List<Map<String, Object>> taxes = new ArrayList<>();
        for (TaxId taxId : taxIds) {
            Map<String, Object> tax = new LinkedHashMap<>();
            tax.put(LegalProfileConstants.FIELD_TYPE, taxId.type().name());
            tax.put(LegalProfileConstants.FIELD_VALUE, taxId.value());
            taxes.add(tax);
        }
        map.put(LegalProfileConstants.FIELD_TAX_IDS, taxes);
        List<Map<String, Object>> places = new ArrayList<>();
        for (LegalAddress address : addresses) {
            Map<String, Object> place = new LinkedHashMap<>();
            place.put(LegalProfileConstants.FIELD_KIND, address.kind().name());
            place.put(LegalProfileConstants.FIELD_LINE1, address.line1());
            place.put(LegalProfileConstants.FIELD_LINE2, address.line2());
            place.put(LegalProfileConstants.FIELD_CITY, address.city());
            place.put(LegalProfileConstants.FIELD_REGION, address.region());
            place.put(LegalProfileConstants.FIELD_POSTAL_CODE, address.postalCode());
            place.put(LegalProfileConstants.FIELD_COUNTRY, address.country());
            places.add(place);
        }
        map.put(LegalProfileConstants.FIELD_ADDRESSES, places);
        return map;
    }

    private static <T> List<T> sorted(List<T> values, Comparator<T> order) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<T> copy = new ArrayList<>(values);
        copy.sort(order);
        return List.copyOf(copy);
    }
}
