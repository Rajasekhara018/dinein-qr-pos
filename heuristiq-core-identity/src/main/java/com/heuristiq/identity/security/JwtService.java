package com.heuristiq.uam.security;

import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.organization.operatingmodel.application.OperatingModelUseCase;
import com.heuristiq.uam.organization.operatingmodel.application.OrganizationNotFoundException;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModel;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.util.UserTypeResolver;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {
    /** This proof cannot open an application: it has no roles or session id and expires in five minutes. */
    public String generateEnrollment(String userId, String organization, String application) {
        Instant now = Instant.now();
        return Jwts.builder().issuer(properties.getAuth().getIssuer()).subject(userId)
                .claim("purpose", "MFA_ENROLLMENT").claim("organization", organization).claim("application", application)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300))).signWith(key).compact();
    }
    private final UamProperties properties;
    private final SecretKey key;

    private final ObjectProvider<FranchiseClaims> franchiseClaims;

    /** The organization's operating model, read in-process (no HTTP call); used only when claims-by-model is switched on. */
    private final ObjectProvider<OperatingModelUseCase> operatingModels;

    /** A service for an organization that has no franchises. */
    public JwtService(UamProperties properties) {
        this(properties, null, null);
    }

    /** A service that decides the franchise lookup by role prefixes only (the operating model is not available). */
    public JwtService(UamProperties properties, ObjectProvider<FranchiseClaims> franchiseClaims) {
        this(properties, franchiseClaims, null);
    }

    @Autowired
    public JwtService(UamProperties properties, ObjectProvider<FranchiseClaims> franchiseClaims,
                      ObjectProvider<OperatingModelUseCase> operatingModels) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getAuth().getJwtSecret().getBytes(StandardCharsets.UTF_8));
        this.franchiseClaims = franchiseClaims;
        this.operatingModels = operatingModels;
    }

    /** True when the user holds a role that can place a person in a franchise: a configured prefix itself or prefix + "_" + suffix (case-insensitive). */
    private boolean mayBeInFranchise(List<String> roles) {
        return RolePrefixMatcher.matchesAny(roles, properties.getFranchise().getService().getLookupRolePrefixes());
    }

    /**
     * Whether the session's franchises must be looked up. With {@code railcab.uam.franchise.claims-by-model} off (the default)
     * only holders of a franchise role prefix are. With it on, everyone signing in to an organization whose operating model
     * today (in the organization's time zone) is FRANCHISE or HYBRID is looked up whatever their roles; a franchise role holder
     * is still looked up in a DIRECT organization, because a franchise stays active after a switch to DIRECT until an admin
     * closes it, and a token without its franchises would read as "no franchise, see everything".
     */
    private boolean lookupNeeded(String tenantId, List<String> roles) {
        if (mayBeInFranchise(roles)) {
            return true;
        }
        return properties.getFranchise().isClaimsByModel() && organizationUsesFranchises(tenantId);
    }

    /** True when the organization's model today is FRANCHISE or HYBRID; an unknown organization counts as DIRECT (the default model). */
    private boolean organizationUsesFranchises(String tenantId) {
        OperatingModelUseCase models = operatingModels == null ? null : operatingModels.getIfAvailable();
        if (models == null) {
            return false;
        }
        try {
            OperatingModel model = models.modelOn(tenantId, null).model();
            return model == OperatingModel.FRANCHISE || model == OperatingModel.HYBRID;
        } catch (OrganizationNotFoundException e) {
            return false;
        }
    }

    /**
     * Writes which of the token's franchises the account owns and which it works in as an agent, so a screen or service can tell an owner
     * from an agent. Both claims are left out when empty, and a failed lookup leaves them out too: they only describe the person and
     * restrict nothing (the {@code franchiseIds} claim does that and is never left out on a failure).
     */
    private void addFranchiseRoleClaims(io.jsonwebtoken.JwtBuilder builder, UamUser user, String tenantId) {
        FranchiseClaims claims = franchiseClaims == null ? null : franchiseClaims.getIfAvailable();
        if (claims == null || user.getId() == null || tenantId == null) {
            return;
        }
        FranchiseClaims.RoleIds roles = claims.roleIdsOf(tenantId, user.getId());
        if (roles == null) {
            return;
        }
        if (!roles.owned().isEmpty()) {
            builder.claim(SecurityConstants.OWNED_FRANCHISE_IDS_CLAIM, roles.owned());
        }
        if (!roles.agent().isEmpty()) {
            builder.claim(SecurityConstants.AGENT_FRANCHISE_IDS_CLAIM, roles.agent());
        }
    }

    /**
     * What the session may carry once the person's franchises are known. Anyone who must be looked up (see
     * {@link #lookupNeeded}) is looked up, and a failure of the lookup is thrown, never read as "no franchise". The lookup
     * answers only ACTIVE franchises; when it answers none, the franchise roles (found by the configured role prefixes)
     * give nothing and are left out, so neither they nor their privileges reach the token. Other roles stay, and so does
     * any privilege one of them grants. Nobody is looked up when the lookup is not available or switched off, and then
     * no role is left out.
     *
     * @param user     the account signing in or refreshing
     * @param tenantId the session's organization
     */
    public SessionAccess sessionAccess(UamUser user, String tenantId) {
        List<String> roles = user.getRoles() == null ? List.of() : user.getRoles();
        FranchiseClaims claims = franchiseClaims == null ? null : franchiseClaims.getIfAvailable();
        if (claims == null || user.getId() == null || tenantId == null || !lookupNeeded(tenantId, roles)) {
            return SessionAccess.unchanged(roles);
        }
        List<String> franchises = claims.franchiseIdsOf(tenantId, user.getId());
        if (!franchises.isEmpty() || !claims.lookupEnabled()) {
            return new SessionAccess(roles, franchises, List.of());
        }
        List<String> prefixes = properties.getFranchise().getService().getLookupRolePrefixes();
        List<String> kept = roles.stream().filter(role -> !RolePrefixMatcher.matches(role, prefixes)).toList();
        List<String> dropped = roles.stream().filter(role -> RolePrefixMatcher.matches(role, prefixes)).toList();
        return new SessionAccess(kept, List.of(), dropped);
    }

    public String generate(UamUser user) {
        return generate(user, List.of());
    }

    /**
     * @param applications codes of the registered applications the user may sign in to, written to the
     *                     {@code apps} claim. An application's own backend must require its code here; the
     *                     {@code X-Application-Code} header checked at login is only a fail-fast convenience.
     */
    public String generate(UamUser user, List<String> applications) {
        return generate(user, applications, user.getTenantId());
    }

    /**
     * @param tenantId the organization this session works in (the {@code tenantId} claim), chosen from the account's
     *                 memberships at sign-in; services scope everything they read and write to it
     */
    public String generate(UamUser user, List<String> applications, String tenantId) {
        return generate(user, applications, tenantId, false);
    }

    /**
     * @param orgWide true writes the {@code orgWide} claim: the caller may look across organizations (it is left out
     *                otherwise, so an ordinary token is unchanged)
     */
    public String generate(UamUser user, List<String> applications, String tenantId, boolean orgWide) {
        return generate(user, applications, tenantId, orgWide, sessionAccess(user, tenantId));
    }

    /**
     * Same as {@link #generate(UamUser, List, String, boolean)} with the franchise lookup already done (see
     * {@link #sessionAccess}), so a sign-in that checked it first does not ask the franchise service twice. The token's
     * roles and franchises are the ones in {@code access}.
     */
    public String generate(UamUser user, List<String> applications, String tenantId, boolean orgWide, SessionAccess access) {
        return generateSession(user, applications, tenantId, orgWide, access, null);
    }

    public String generateSession(UamUser user, List<String> applications, String tenantId, boolean orgWide, SessionAccess access, String sessionId) {
        return generateSession(user, applications, tenantId, orgWide, access, sessionId, null);
    }

    /**
     * Same as {@link #generateSession(UamUser, List, String, boolean, SessionAccess, String)}, also writing the
     * {@value SecurityConstants#AMR_CLAIM} claim when {@code authenticationMethods} is not empty (a biometric sign-in);
     * without it the token is exactly what it was before.
     */
    public String generateSession(UamUser user, List<String> applications, String tenantId, boolean orgWide, SessionAccess access,
                                  String sessionId, List<String> authenticationMethods) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getAuth().getExpiryMinutes() * 60);
        List<String> roles = access.roles();
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder();
        if (sessionId != null) builder.claim("sid", sessionId);
        if (authenticationMethods != null && !authenticationMethods.isEmpty()) {
            builder.claim(SecurityConstants.AMR_CLAIM, authenticationMethods);
        }
        if (orgWide) {
            builder.claim(com.heuristiq.uam.organization.OrgWideConstants.CLAIM, true);
        }
        // A franchise owner's or agent's ACTIVE franchises travel with the session; it is left out for everyone else, so an ordinary
        // token is unchanged. Holders of a role with a lookup prefix (railcab.uam.franchise.service.lookup-role-prefixes) are looked up, and with
        // railcab.uam.franchise.claims-by-model on so is everyone in a FRANCHISE or HYBRID organization; nobody else, so the franchise service
        // being down cannot block anyone else. For those, the lookup is not caught: a token without the claim would read as "no franchise, see everything".
        // A franchise role holder with no active franchise has already lost those roles in sessionAccess.
        List<String> franchises = access.franchiseIds();
        if (!franchises.isEmpty()) {
            builder.claim(SecurityConstants.FRANCHISE_IDS_CLAIM, franchises);
            addFranchiseRoleClaims(builder, user, tenantId);
        }
        return builder
                .issuer(properties.getAuth().getIssuer())
                .subject(user.getId())
                .claim(SecurityConstants.TENANT_ID_CLAIM, tenantId)
                .claim(SecurityConstants.USERNAME_CLAIM, user.getUserName())
                .claim(SecurityConstants.EMAIL_CLAIM, user.getEmail())
                .claim(SecurityConstants.FULL_NAME_CLAIM, user.getFullName() == null ? null : user.getFullName().getFullName())
                .claim(SecurityConstants.USER_TYPE_CLAIM, UserTypeResolver.resolve(roles).name())
                .claim(SecurityConstants.ROLE_CLAIM, roles.stream().findFirst().orElse(SecurityConstants.DEFAULT_ROLE))
                .claim(SecurityConstants.ROLES_CLAIM, roles)
                .claim(SecurityConstants.APPS_CLAIM, applications == null ? List.of() : applications)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
