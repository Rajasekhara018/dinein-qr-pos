package com.heuristiq.identity.orgstructure.adapter.in;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;

/**
 * Maps organization-structure rule failures onto the standard error envelope. Ordered ahead of the application-wide
 * handler: that one has a catch-all {@code Exception} method and, with equal order, was answering these failures with a 500.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {OrgStructureController.class, OrgImportController.class, DepartmentImportController.class,
        LadderImportController.class, RoleRuleImportController.class,
        OrgLoginController.class})
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class OrgStructureExceptionAdvice {

    /** The document store (or another downstream) is down or too slow: an outage, not a bad request. */
    @ExceptionHandler(DownstreamUnavailableException.class)
    ResponseEntity<CommonResponseObject> handleDownstream(DownstreamUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(CommonResponseObject.failure(
                "A required service is temporarily unavailable; try again shortly", "DOWNSTREAM_UNAVAILABLE"));
    }

    @ExceptionHandler(OrgStructureException.class)
    ResponseEntity<CommonResponseObject> handle(OrgStructureException e) {
        HttpStatus status = switch (e.kind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
        };
        return ResponseEntity.status(status).body(CommonResponseObject.failure(e.getMessage(), e.kind().name()));
    }
}
