package com.heuristiq.identity.pos.adapter.in;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.pos.domain.PosConstants;
import com.heuristiq.identity.pos.domain.PosException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;

/**
 * Turns the POS refusals into UAM's error answers: invalid 400, conflict (stale version, duplicate code) 409, unknown or
 * unreachable 404 (never 403, so other tenants' and branches' ids are not confirmed). The message is the translatable key
 * ({@code pos.*}); the offending field's path is sent as {@code errorCode}.
 */
final class PosErrors {

    private PosErrors() {
    }

    static <T> T translated(Supplier<T> call) {
        try {
            return call.get();
        } catch (PosException e) {
            HttpStatus status = switch (e.kind()) {
                case INVALID -> HttpStatus.BAD_REQUEST;
                case CONFLICT -> HttpStatus.CONFLICT;
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
            };
            throw new UamException(e.getMessage(), status, e.field());
        }
    }

    /** An update must say which version it was based on; without one it cannot be told to be current. */
    static long requireVersion(Long version) {
        if (version == null) {
            throw PosException.conflict(PosConstants.ERROR_STALE_VERSION, PosConstants.FIELD_VERSION);
        }
        return version;
    }
}
