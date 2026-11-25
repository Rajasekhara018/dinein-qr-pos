package com.heuristiq.identity.franchiseaccess;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;

/**
 * Answers the franchise service with the UAM message and a status it can act on: a rule broken by existing data (email taken,
 * role already there, not allowed) is 409 with the message, a missing record 404, bad input 400.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = FranchiseAccessController.class)
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class FranchiseAccessExceptionAdvice {

    @ExceptionHandler(UamException.class)
    ResponseEntity<CommonResponseObject> handle(UamException e) {
        HttpStatus status = e.getStatus() == HttpStatus.NOT_FOUND ? HttpStatus.NOT_FOUND
                : e.getStatus() == HttpStatus.BAD_REQUEST ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(CommonResponseObject.failure(e.getMessage(), status.name()));
    }

    @ExceptionHandler(OrgStructureException.class)
    ResponseEntity<CommonResponseObject> handle(OrgStructureException e) {
        HttpStatus status = switch (e.kind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID -> HttpStatus.BAD_REQUEST;
            case CONFLICT, FORBIDDEN -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(CommonResponseObject.failure(e.getMessage(), status.name()));
    }
}
