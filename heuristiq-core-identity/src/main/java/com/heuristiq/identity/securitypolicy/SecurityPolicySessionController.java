package com.heuristiq.uam.securitypolicy;

import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.security.TenantGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** The gateway calls UAM directly with the original signed token; no client-supplied user id is accepted. */
@RestController
public class SecurityPolicySessionController {
    @GetMapping("/api/v1/uam/auth/session-status")
    public CommonResponseObject status(HttpServletRequest request) {
        if (!TenantGuard.isSignedInUser() || request.getAttribute("uam.policySessionId") == null)
            throw new UamException("Session is not active", HttpStatus.UNAUTHORIZED);
        return CommonResponseObject.success(Map.of("active", true));
    }
}
