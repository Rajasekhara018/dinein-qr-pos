package com.heuristq.dinein.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Stateless bearer authentication for {@code /api/**}. Not a bean, so it is only registered in the security chain. */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Lets a {@code platformAdmin} account (see {@code StaffUserEntity}) act on a restaurant that isn't their own
     * -- e.g. to manage a merchant's tables/menu directly, or debug an issue -- without a second login. Ignored
     * for everyone else, so an ordinary owner/manager can never widen their own access by sending this header.
     */
    private static final String RESTAURANT_OVERRIDE_HEADER = "X-Restaurant-Id";

    private final TokenAuthenticator tokenAuthenticator;

    public TokenAuthenticationFilter(TokenAuthenticator tokenAuthenticator) {
        this.tokenAuthenticator = tokenAuthenticator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            tokenAuthenticator.authenticate(request.getHeader(HttpHeaders.AUTHORIZATION)).ifPresent(auth -> {
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(withRestaurantOverride(auth, request));
            });
        }
        chain.doFilter(request, response);
    }

    private static Authentication withRestaurantOverride(UsernamePasswordAuthenticationToken auth,
                                                          HttpServletRequest request) {
        if (!(auth.getPrincipal() instanceof StaffPrincipal principal) || !principal.platformAdmin()) {
            return auth;
        }
        String header = request.getHeader(RESTAURANT_OVERRIDE_HEADER);
        if (header == null || header.isBlank()) {
            return auth;
        }
        long restaurantId;
        try {
            restaurantId = Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return auth;
        }
        StaffPrincipal overridden = new StaffPrincipal(principal.userId(), principal.username(), principal.role(),
                restaurantId, principal.deviceId(), principal.passwordChangeRequired(), principal.platformAdmin());
        UsernamePasswordAuthenticationToken result =
                new UsernamePasswordAuthenticationToken(overridden, auth.getCredentials(), auth.getAuthorities());
        result.setDetails(auth.getDetails());
        return result;
    }
}
