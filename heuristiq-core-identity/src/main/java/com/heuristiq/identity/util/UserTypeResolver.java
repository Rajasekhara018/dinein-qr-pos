package com.heuristiq.uam.util;

import com.heuristiq.uam.enums.UamEnums;

import java.util.List;

public final class UserTypeResolver {

    private UserTypeResolver() {
    }

    public static UamEnums.UserType resolve(List<String> roles) {
        List<String> safe = roles == null ? List.of() : roles;
        if (safe.contains("DRIVER")) {
            return UamEnums.UserType.DRIVER;
        }
        if (safe.contains("VENDOR")) {
            return UamEnums.UserType.VENDOR;
        }
        if (safe.contains("CUSTOMER")) {
            return UamEnums.UserType.CUSTOMER;
        }
        // Every web/back-office user (SUPER_ADMIN or any admin-panel role) is an OPS user -- the
        // role list, not userType, is what distinguishes a super admin from other staff.
        return UamEnums.UserType.OPS;
    }
}
