package com.heuristiq.uam.audit.authevent;

import com.heuristiq.uam.users.UamUser;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sign-in outcome logging and metrics. UAM keeps no audit store: the audit trail of sign-ins is the domain event that
 * {@code AuthService} reports to the central audit service for resolved accounts, plus the gateway's access audit of
 * the login endpoint (which also covers attempts for an unknown account). This class only logs and counts.
 */
@Service
public class AuthAuditService {
    private static final Logger log = LoggerFactory.getLogger(AuthAuditService.class);
    private final MeterRegistry meterRegistry;

    public AuthAuditService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void loginSucceeded(UamUser user) {
        List<String> roles = user.getRoles() == null ? List.of() : user.getRoles();
        log.info("UAM auth event=login_success userId={} username={} roles={}",
                user.getId(), user.getUserName(), roles);
        count("login_success", null);
    }

    public void loginFailed(String username, String reason) {
        log.warn("UAM auth event=login_failed username={} reason={}", sanitize(username), reason);
        count("login_failed", reason);
    }

    public void loginBlocked(String username) {
        log.warn("UAM auth event=login_blocked username={} reason=lockout", sanitize(username));
        count("login_blocked", "lockout");
    }

    private String sanitize(String username) {
        return username == null ? "" : username.trim();
    }

    private void count(String eventType, String reason) {
        meterRegistry.counter("uam.auth.audit.events",
                "event", eventType,
                "reason", reason == null ? "none" : reason).increment();
    }
}
