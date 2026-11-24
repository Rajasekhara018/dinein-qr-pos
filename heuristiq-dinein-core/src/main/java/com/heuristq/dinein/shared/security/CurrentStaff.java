package com.heuristq.dinein.shared.security;

import com.heuristq.dinein.shared.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class CurrentStaff {

    private CurrentStaff() {
    }

    public static Optional<StaffPrincipal> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof StaffPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static StaffPrincipal require() {
        return find().orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Login required"));
    }
}
