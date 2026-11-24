package com.heuristiq.paymentservice.wallet.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.paymentservice.wallet.port.WalletCreditRejectedException;

import java.io.IOException;

/** Reads the wallet's {@code CommonResponseObject} envelope: the journal id of a success, the error code of a refusal. */
final class WalletResponses {

    private WalletResponses() {
    }

    /** @return the journal id of a successful posting, or null when the reply carries none */
    static String journalIdOf(JsonNode response) {
        if (response == null) {
            return null;
        }
        String journalId = response.path(WalletClientConstants.RESPONSE_OBJECT_FIELD)
                .path(WalletClientConstants.JOURNAL_ID_FIELD).asText(null);
        return StringUtils.hasText(journalId) ? journalId : null;
    }

    /** @return the wallet's contract error code of a refusal, or null when the body is not the wallet's JSON */
    static String errorCodeOf(ObjectMapper objectMapper, WebClientResponseException e) {
        try {
            String code = objectMapper.readTree(e.getResponseBodyAsString())
                    .path(WalletClientConstants.ERROR_CODE_FIELD).asText();
            return StringUtils.hasText(code) ? code : null;
        } catch (IOException notJson) {
            return null;
        }
    }

    /**
     * Classifies a non-2xx wallet answer to a credit. Only a definite business refusal (400 VALIDATION_ERROR, or 409
     * with a code in {@link WalletClientConstants#DEFINITE_REFUSAL_CODES_409}) is a {@link WalletCreditRejectedException}.
     * Anything else (401, 403, 404, 408, 429, any 5xx, an unreadable body, another 409 such as BUSY or a duplicate)
     * leaves the outcome unknown: the wallet may have booked it, so it surfaces as unavailable and the caller retries
     * with the SAME key.
     */
    static RuntimeException creditFailureOf(ObjectMapper objectMapper, WebClientResponseException e) {
        int status = e.getStatusCode().value();
        String code = errorCodeOf(objectMapper, e);
        boolean definite = code != null && ((status == 400 && WalletClientConstants.CODE_VALIDATION_ERROR.equals(code))
                || (status == 409 && WalletClientConstants.DEFINITE_REFUSAL_CODES_409.contains(code)));
        if (definite) {
            return new WalletCreditRejectedException(status, code);
        }
        return new DownstreamUnavailableException(WalletClientConstants.WALLET_SERVICE_NAME,
                DownstreamUnavailableException.Reason.FAILED, e);
    }
}
