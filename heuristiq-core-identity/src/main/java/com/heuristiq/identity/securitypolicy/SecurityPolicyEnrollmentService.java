package com.heuristiq.uam.securitypolicy;

import com.heuristiq.uam.auth.AuthConstants;
import com.heuristiq.uam.auth.ClientIpResolver;
import com.heuristiq.uam.auth.LoginSessionService;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.dto.LoginResponse;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.lockout.LoginAttemptServiceUseCase;
import com.heuristiq.uam.mfa.*;
import com.heuristiq.uam.organization.membership.TenancyConstants;
import com.heuristiq.uam.security.JwtService;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.Objects;

@Service
public class SecurityPolicyEnrollmentService {
    public record Challenge(boolean mfaRequired, boolean mfaEnrollmentRequired, String enrollmentToken, String message) {}
    private final JwtService jwt;
    private final UamUserRepository users;
    private final UserMfaServiceUseCase mfa;
    private final LoginAttemptServiceUseCase attempts;
    private final LoginSessionService sessions;
    private final HttpServletRequest request;
    public SecurityPolicyEnrollmentService(JwtService jwt, UamUserRepository users, UserMfaServiceUseCase mfa,
            LoginAttemptServiceUseCase attempts, LoginSessionService sessions, HttpServletRequest request) {
        this.jwt=jwt; this.users=users; this.mfa=mfa; this.attempts=attempts; this.sessions=sessions; this.request=request;
    }
    public Challenge challenge(UamUser user) {
        return new Challenge(true, true, jwt.generateEnrollment(user.getId(), header(TenancyConstants.ORGANIZATION_HEADER),
                header(AuthConstants.APPLICATION_CODE_HEADER)), "Set up your authenticator to finish signing in.");
    }
    public MfaEnrollTotpResponse begin(String token) {
        UamUser user = authenticated(token);
        return mfa.enrollTotp(new UserMfaServiceUseCase.EnrollTotpInput(user.getId())).response();
    }
    public LoginResponse complete(String token, String id, String code) {
        UamUser user = authenticated(token);
        var method = mfa.get(new UserMfaServiceUseCase.GetInput(id)).mfa();
        if (!Objects.equals(user.getId(), method.getUserId()) || method.getMfaType() != UamEnums.MfaType.TOTP
                || Boolean.TRUE.equals(method.getVerified())) throw invalid();
        try {
            mfa.verifyEnrollment(new UserMfaServiceUseCase.VerifyEnrollmentInput(id, code));
        } catch (UamException wrongCode) {
            attempts.recordFailure(new LoginAttemptServiceUseCase.RecordFailureInput(user.getUserName()));
            throw wrongCode;
        }
        LoginResponse response = sessions.issue(user, ClientIpResolver.resolve(request), request.getHeader("User-Agent"), true);
        attempts.recordSuccess(new LoginAttemptServiceUseCase.RecordSuccessInput(user.getUserName()));
        return response;
    }
    private UamUser authenticated(String token) {
        io.jsonwebtoken.Claims claims;
        try { claims = jwt.parse(token); } catch (RuntimeException badProof) { throw invalid(); }
        if (!"MFA_ENROLLMENT".equals(claims.get("purpose"))
                || !header(TenancyConstants.ORGANIZATION_HEADER).equals(claims.get("organization"))
                || !header(AuthConstants.APPLICATION_CODE_HEADER).equals(claims.get("application"))) throw invalid();
        UamUser user = users.findById(claims.getSubject()).orElseThrow(SecurityPolicyEnrollmentService::invalid);
        if (user.getStatus() != UamEnums.UserStatus.ACTIVE) throw invalid();
        if (attempts.isLocked(new LoginAttemptServiceUseCase.IsLockedInput(user.getUserName())).locked())
            throw new UamException(AuthConstants.LOGIN_LOCKED_MESSAGE, HttpStatus.FORBIDDEN);
        return user;
    }
    private String header(String name) { return Objects.requireNonNullElse(request.getHeader(name), "").trim(); }
    private static UamException invalid() { return new UamException("Enrollment proof expired or invalid; sign in again", HttpStatus.UNAUTHORIZED); }
}
