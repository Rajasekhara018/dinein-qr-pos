package com.heuristiq.identity.organization.operatingmodel.adapter.in;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.organization.operatingmodel.application.OrganizationNotFoundException;
import com.heuristiq.identity.organization.operatingmodel.domain.OperatingModelException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;

/** Turns the operating model's refusals into UAM's error answers: invalid 400, conflict 409, unknown organization 404. */
final class OperatingModelErrors {

    private OperatingModelErrors() {
    }

    static <T> T translated(Supplier<T> call) {
        try {
            return call.get();
        } catch (OperatingModelException e) {
            throw new UamException(e.getMessage(),
                    e.kind() == OperatingModelException.Kind.CONFLICT ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST);
        } catch (OrganizationNotFoundException e) {
            throw UamException.notFound(e.getMessage());
        }
    }
}
