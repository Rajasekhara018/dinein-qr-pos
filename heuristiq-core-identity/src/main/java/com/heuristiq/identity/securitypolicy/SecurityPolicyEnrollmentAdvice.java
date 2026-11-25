package com.heuristiq.identity.securitypolicy;

import com.heuristiq.identity.common.CommonResponseObject;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityPolicyEnrollmentAdvice {
    @ExceptionHandler(SecurityPolicyEnrollmentRequired.class)
    public CommonResponseObject challenge(SecurityPolicyEnrollmentRequired required) {
        return CommonResponseObject.success(required.challenge());
    }
}
