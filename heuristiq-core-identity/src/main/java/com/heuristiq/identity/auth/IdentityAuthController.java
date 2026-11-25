package com.heuristiq.identity.auth;

import com.heuristiq.identity.dto.OtpSentResponse;
import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.OperationResult;
import com.heuristiq.identity.common.RequestObjectType;
import com.heuristiq.identity.dto.IdentityEmailOtpRequest;
import com.heuristiq.identity.dto.IdentityPhoneOtpRequest;
import com.heuristiq.identity.dto.IdentityRegisterRequest;
import com.heuristiq.identity.dto.IdentityResetPasswordRequest;
import com.heuristiq.identity.dto.IdentityVerifyEmailOtpRequest;
import com.heuristiq.identity.dto.IdentityVerifyPhoneOtpRequest;
import com.heuristiq.identity.dto.LoginRequest;
import com.heuristiq.identity.dto.ProviderLoginRequest;
import com.heuristiq.identity.dto.ProviderRefreshRequest;
import com.heuristiq.identity.dto.UnifiedLoginRequest;
import com.heuristiq.identity.dto.UnifiedLogoutRequest;
import com.heuristiq.identity.dto.UnifiedRefreshRequest;
import com.heuristiq.identity.google.UamGoogleAuthServiceUseCase;
import com.heuristiq.identity.identity.IdentityProviderAuthRouterUseCase;
import com.heuristiq.identity.refreshToken.RefreshTokenServiceUseCase;
import com.heuristiq.identity.otp.UamOtpAuthServiceUseCase;
import com.heuristiq.identity.registration.UserRegistrationServiceUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The single identity/auth surface for every UserType (CUSTOMER, DRIVER, VENDOR, and anything
 * added later) -- the userType travels IN the request body, so onboarding a new user type never
 * requires a new copy-pasted controller, only a new UamEnums.UserType value. Replaces the earlier
 * per-type CustomerAuthController/DriverAuthController and the duplicate AuthController/
 * WebAuthController (both removed) now that every client -- CustomerApp, DriverApp, AdminWeb,
 * 2026_RailCab_Web -- calls this single endpoint directly.
 *
 * <p>{@code /login}, {@code /refresh}, and {@code /logout} are likewise each a single entry point
 * covering every MECHANISM (local username/password, Google, external Keycloak/OIDC) -- see
 * {@link UnifiedLoginRequest}/{@link UnifiedRefreshRequest}/{@link UnifiedLogoutRequest}. There is
 * no separate {@code /google}, {@code /provider/login}, {@code /provider/refresh}, or
 * {@code /provider/logout} -- which mechanism runs is decided by which fields the caller
 * populates, and every mechanism returns/accepts the same shapes.
 */
@RestController
@RequestMapping("/api/v1/uam/identity/auth")
@Tag(name = "Identity Auth", description = "The single identity/auth surface for every UserType and login mechanism (local, Google, external IdP): register, login, refresh, logout, and OTP-based verification/reset.")
public class IdentityAuthController {
    private final AuthServiceUseCase authService;
    private final UamOtpAuthServiceUseCase otpAuthService;
    private final UamGoogleAuthServiceUseCase googleAuthService;
    private final IdentityProviderAuthRouterUseCase identityProviderAuthRouter;
    private final RefreshTokenServiceUseCase refreshTokenService;
    private final UserRegistrationServiceUseCase userRegistrationService;
    private final CommonRequestMapper requestMapper;

    public IdentityAuthController(AuthServiceUseCase authService,
                                  UamOtpAuthServiceUseCase otpAuthService,
                                  UamGoogleAuthServiceUseCase googleAuthService,
                                  IdentityProviderAuthRouterUseCase identityProviderAuthRouter,
                                  RefreshTokenServiceUseCase refreshTokenService,
                                  UserRegistrationServiceUseCase userRegistrationService,
                                  CommonRequestMapper requestMapper) {
        this.authService = authService;
        this.otpAuthService = otpAuthService;
        this.googleAuthService = googleAuthService;
        this.identityProviderAuthRouter = identityProviderAuthRouter;
        this.refreshTokenService = refreshTokenService;
        this.userRegistrationService = userRegistrationService;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Log in (local, Google, or external IdP)",
            description = "Single login entry point for every mechanism this service supports. Which mechanism runs is decided by which fields are populated on the UnifiedLoginRequest carried in commonRequest.requestObject: "
                    + "idToken present -> Google sign-in; providerType present -> external IdP login; otherwise -> local username/password (with optional totpCode for the MFA resubmit).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login succeeded, tokens/session returned"),
            @ApiResponse(responseCode = "400", description = "Validation failed or invalid credentials"),
            @ApiResponse(responseCode = "401", description = "Google/external IdP token invalid or expired"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UnifiedLoginRequest.class)
    @PostMapping("/login")
    public ResponseEntity<CommonResponseObject> login(@Valid @RequestBody CommonRequestObject commonRequest) {
        UnifiedLoginRequest request = requestMapper.requireRequestObject(commonRequest, UnifiedLoginRequest.class);

        if (StringUtils.hasText(request.idToken())) {
            return ResponseEntity.ok(CommonResponseObject.success(googleAuthService.login(
                    new UamGoogleAuthServiceUseCase.LoginWithTypeInput(request.idToken(), request.userType(), request.totpCode())).response()));
        }
        if (StringUtils.hasText(request.providerType())) {
            ProviderLoginRequest providerRequest = new ProviderLoginRequest(
                    request.providerType(), request.grantType(), request.username(), request.password(),
                    request.totp(), request.authorizationCode(), request.redirectUri(), request.userType());
            return ResponseEntity.ok(CommonResponseObject.success(
                    identityProviderAuthRouter.login(new IdentityProviderAuthRouterUseCase.LoginInput(providerRequest)).response()));
        }
        LoginRequest localRequest = new LoginRequest(request.username(), request.password(), request.totpCode(), request.userType());
        return ResponseEntity.ok(CommonResponseObject.success(authService.login(new AuthServiceUseCase.LoginInput(localRequest)).result()));
    }

    @Operation(summary = "Register a new identity",
            description = "Creates a new local account for any UserType beyond CUSTOMER/DRIVER (which have their own dedicated registration DTOs elsewhere). Request payload is an IdentityRegisterRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registration succeeded"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "409", description = "Account already exists"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(IdentityRegisterRequest.class)
    @PostMapping("/register")
    public ResponseEntity<CommonResponseObject> register(@Valid @RequestBody CommonRequestObject commonRequest) {
        IdentityRegisterRequest request = requestMapper.requireRequestObject(commonRequest, IdentityRegisterRequest.class);
        // This path is public: nobody may create a staff account (and so a staff role) by naming it in the request.
        PublicAccountTypes.requireSelfRegistrable(request.userType());
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(
                userRegistrationService.register(new UserRegistrationServiceUseCase.RegisterInput(request)).response()));
    }

    @Operation(summary = "Send an SMS OTP", description = "Sends a one-time-passcode via SMS to the given phone number. Request payload is an IdentityPhoneOtpRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP sent"),
            @ApiResponse(responseCode = "400", description = "Validation failed (e.g. malformed phone number)"),
            @ApiResponse(responseCode = "429", description = "Resend cooldown or hourly send limit reached for this phone number"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error, or SMS provider failure")
    })
    @RequestObjectType(IdentityPhoneOtpRequest.class)
    @PostMapping("/phone/send-otp")
    public ResponseEntity<CommonResponseObject> sendPhoneOtp(@Valid @RequestBody CommonRequestObject commonRequest) {
        IdentityPhoneOtpRequest request = requestMapper.requireRequestObject(commonRequest, IdentityPhoneOtpRequest.class);
        OtpSentResponse sent = otpAuthService.sendPhoneOtp(
                new UamOtpAuthServiceUseCase.SendPhoneOtpInput(request.phone(), request.userType())).response();
        return ResponseEntity.ok(CommonResponseObject.success(otpSentMessage(sent.maskedPhone(), sent), sent));
    }

    @Operation(summary = "Verify an SMS OTP", description = "Verifies the OTP previously sent to a phone number via /identity/auth/phone/send-otp. Request payload is an IdentityVerifyPhoneOtpRequest. If the account has MFA enrolled and totpCode is absent, the response is an MfaChallengeRequiredResponse and the client resubmits with otp and totpCode.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP verified"),
            @ApiResponse(responseCode = "400", description = "Validation failed, OTP incorrect, or OTP expired"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(IdentityVerifyPhoneOtpRequest.class)
    @PostMapping("/phone/verify-otp")
    public ResponseEntity<CommonResponseObject> verifyPhoneOtp(@Valid @RequestBody CommonRequestObject commonRequest) {
        IdentityVerifyPhoneOtpRequest request = requestMapper.requireRequestObject(commonRequest, IdentityVerifyPhoneOtpRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                otpAuthService.verifyPhoneOtp(new UamOtpAuthServiceUseCase.VerifyPhoneOtpInput(
                        request.phone(), request.otp(), request.userType(), request.totpCode())).response()));
    }

    @Operation(summary = "Send an email OTP", description = "Sends a one-time-passcode via email to the given address. Request payload is an IdentityEmailOtpRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP sent"),
            @ApiResponse(responseCode = "400", description = "Validation failed (e.g. malformed email address)"),
            @ApiResponse(responseCode = "429", description = "Resend cooldown or hourly send limit reached for this email address"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error, or email provider failure")
    })
    @RequestObjectType(IdentityEmailOtpRequest.class)
    @PostMapping("/email/send-otp")
    public ResponseEntity<CommonResponseObject> sendEmailOtp(@Valid @RequestBody CommonRequestObject commonRequest) {
        IdentityEmailOtpRequest request = requestMapper.requireRequestObject(commonRequest, IdentityEmailOtpRequest.class);
        OtpSentResponse sent = otpAuthService.sendEmailOtp(
                new UamOtpAuthServiceUseCase.SendEmailOtpInput(request.email(), request.userType())).response();
        return ResponseEntity.ok(CommonResponseObject.success(otpSentMessage(sent.maskedEmail(), sent), sent));
    }

    @Operation(summary = "Verify an email OTP", description = "Verifies the OTP previously sent to an email address via /identity/auth/email/send-otp. Request payload is an IdentityVerifyEmailOtpRequest. If the account has MFA enrolled and totpCode is absent, the response is an MfaChallengeRequiredResponse and the client resubmits with otp and totpCode.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP verified"),
            @ApiResponse(responseCode = "400", description = "Validation failed, OTP incorrect, or OTP expired"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(IdentityVerifyEmailOtpRequest.class)
    @PostMapping("/email/verify-otp")
    public ResponseEntity<CommonResponseObject> verifyEmailOtp(@Valid @RequestBody CommonRequestObject commonRequest) {
        IdentityVerifyEmailOtpRequest request = requestMapper.requireRequestObject(commonRequest, IdentityVerifyEmailOtpRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                otpAuthService.verifyEmailOtp(new UamOtpAuthServiceUseCase.VerifyEmailOtpInput(
                        request.email(), request.otp(), request.userType(), request.totpCode())).response()));
    }

    @Operation(summary = "Complete a forgot-password reset",
            description = "Verifies the emailed OTP and sets a new password in one step. Request payload is an IdentityResetPasswordRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed, OTP incorrect, or OTP expired"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(IdentityResetPasswordRequest.class)
    @PostMapping("/reset-password")
    public ResponseEntity<CommonResponseObject> resetPassword(@Valid @RequestBody CommonRequestObject commonRequest) {
        IdentityResetPasswordRequest request = requestMapper.requireRequestObject(commonRequest, IdentityResetPasswordRequest.class);
        otpAuthService.resetPassword(new UamOtpAuthServiceUseCase.ResetPasswordInput(
                request.email(), request.otp(), request.newPassword(), request.userType()));
        return ResponseEntity.ok(CommonResponseObject.success("Password reset successful.", null));
    }

    @Operation(summary = "Refresh a session (local or external IdP)",
            description = "Redeems a refresh token for a new session. If providerType is present on the UnifiedRefreshRequest, the token is redeemed against that external IdP and re-bridged onto a local session; otherwise it is UAM's own rotating local refresh token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refresh succeeded, new tokens/session returned"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Refresh token invalid, expired, or revoked"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UnifiedRefreshRequest.class)
    @PostMapping("/refresh")
    public ResponseEntity<CommonResponseObject> refresh(@Valid @RequestBody CommonRequestObject commonRequest) {
        UnifiedRefreshRequest request = requestMapper.requireRequestObject(commonRequest, UnifiedRefreshRequest.class);
        if (StringUtils.hasText(request.providerType())) {
            ProviderRefreshRequest providerRequest = new ProviderRefreshRequest(request.providerType(), request.refreshToken());
            return ResponseEntity.ok(CommonResponseObject.success(
                    identityProviderAuthRouter.refresh(new IdentityProviderAuthRouterUseCase.RefreshInput(providerRequest)).response()));
        }
        return ResponseEntity.ok(CommonResponseObject.success(authService.refresh(new AuthServiceUseCase.RefreshInput(request.refreshToken())).response()));
    }

    // Self-service logout: always revokes every active LOCAL refresh token for the caller
    // (resolved from their own JWT, never a client-supplied id) so a stolen refresh token can't
    // outlive logout. If the caller ALSO supplies providerType + the external IdP's own refresh
    // token (optional body -- no request body at all is fine for a purely local session), the
    // external provider's session is revoked in the same call. "/identity/auth/**" is permitAll
    // (SecurityConfig) so a missing/expired bearer leaves authentication null here -- treat that
    // as an already-logged-out no-op rather than a 401/500.
    @Operation(summary = "Log out the current session",
            description = "Revokes every active LOCAL refresh token for the caller (resolved from their own JWT, never client-supplied). If the caller also supplies providerType + the external IdP's own refresh token in an optional UnifiedLogoutRequest body, that external session is revoked too. "
                    + "This endpoint is permitAll (no authentication required) -- a missing/expired bearer is treated as an already-logged-out no-op rather than a 401.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logout completed (idempotent, always succeeds)"),
            @ApiResponse(responseCode = "400", description = "Validation failed on the optional logout body"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UnifiedLogoutRequest.class)
    @PostMapping("/logout")
    public ResponseEntity<CommonResponseObject> logout(Authentication authentication,
                                       @RequestBody(required = false) CommonRequestObject commonRequest) {
        if (authentication != null) {
            refreshTokenService.revokeAllForPrincipal(
                    new RefreshTokenServiceUseCase.RevokeAllForPrincipalInput(authentication.getName()));
        }
        if (commonRequest != null && commonRequest.getRequestObject() != null) {
            UnifiedLogoutRequest request = requestMapper.requireRequestObject(commonRequest, UnifiedLogoutRequest.class);
            if (StringUtils.hasText(request.providerType()) && StringUtils.hasText(request.refreshToken())) {
                identityProviderAuthRouter.logout(new IdentityProviderAuthRouterUseCase.LogoutInput(
                        new ProviderRefreshRequest(request.providerType(), request.refreshToken())));
            }
        }
        return ResponseEntity.ok(CommonResponseObject.success(OperationResult.completed("LOGOUT", AuthConstants.AUTH_SESSION_ENTITY, null)));
    }

    /** e.g. "A verification code has been sent to ra***@gmail.com. It expires in 5 minutes." */
    private static String otpSentMessage(String destination, OtpSentResponse sent) {
        StringBuilder message = new StringBuilder("A verification code has been sent to ")
                .append(destination != null ? destination : "your registered contact").append('.');
        if (sent.expiresInMinutes() != null) {
            int minutes = sent.expiresInMinutes();
            message.append(" It expires in ").append(minutes).append(minutes == 1 ? " minute." : " minutes.");
        }
        return message.toString();
    }
}
