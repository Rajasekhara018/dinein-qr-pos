package com.heuristiq.identity.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.heuristiq.identity.security.JwtService;
import com.heuristiq.identity.security.SessionAccess;
import com.heuristiq.identity.users.UamUser;

/** Stubs of a mocked {@link JwtService} for the sign-in session tests. */
final class JwtServiceStubs {

    private JwtServiceStubs() {
    }

    /** Nobody is in a franchise: every account keeps its roles, and every token issued is {@code token}. */
    static void noFranchises(JwtService jwtService, String token) {
        when(jwtService.sessionAccess(any(), any()))
                .thenAnswer(call -> SessionAccess.unchanged(((UamUser) call.getArgument(0)).getRoles()));
        when(jwtService.generate(any(), anyList(), any(), anyBoolean(), any())).thenReturn(token);
    }
}
