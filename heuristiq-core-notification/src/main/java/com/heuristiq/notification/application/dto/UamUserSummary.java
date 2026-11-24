package com.railcab.nms.application.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Just the fields broadcast audience-resolution needs from UAM's UamUser. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UamUserSummary(
        String id,
        String userName,
        String email,
        PhoneNumber phoneNumber,
        List<String> roles,
        String status,
        Boolean active,
        String tenantId
) {

    /** The same user without an organization (for callers that do not know it). */
    public UamUserSummary(String id, String userName, String email, PhoneNumber phoneNumber, List<String> roles, String status, Boolean active) {
        this(id, userName, email, phoneNumber, roles, status, active, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhoneNumber(String countryCode, String number) {
        public String concatenated() {
            String cc = countryCode != null ? countryCode : "";
            String num = number != null ? number : "";
            return (cc + num).replaceAll("[^0-9]", "");
        }

        /**
         * The number in E.164 form ({@code +<country code><number>}), taken from the country code UAM holds with it.
         * A missing country code is read in the default region, so a legacy bare number still works. Empty when there
         * is no usable number.
         */
        public String e164() {
            String cc = countryCode != null ? countryCode.replaceAll("[^0-9]", "") : "";
            String num = number != null ? number.replaceAll("[^0-9]", "") : "";
            if (num.isEmpty()) {
                return "";
            }
            String candidate = cc.isEmpty() ? num : "+" + cc + num;
            return com.heuristiq.phone.PhoneNumbers.toE164(candidate).orElse(concatenated());
        }
    }
}
