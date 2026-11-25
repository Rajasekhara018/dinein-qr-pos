package com.heuristiq.identity.organization.legalprofile.adapter.in;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.organization.legalprofile.domain.LegalProfileException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;

/**
 * Turns the legal profile's refusals into UAM's error answers: invalid 400, stale version 409, unknown organization 404. The
 * message is the translatable key ({@code legalProfile.*}); the offending field's path is sent as {@code errorCode}.
 */
final class LegalProfileErrors {

    private LegalProfileErrors() {
    }

    static <T> T translated(Supplier<T> call) {
        try {
            return call.get();
        } catch (LegalProfileException e) {
            HttpStatus status = switch (e.kind()) {
                case INVALID -> HttpStatus.BAD_REQUEST;
                case CONFLICT -> HttpStatus.CONFLICT;
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
            };
            throw new UamException(e.getMessage(), status, e.field());
        }
    }
}
