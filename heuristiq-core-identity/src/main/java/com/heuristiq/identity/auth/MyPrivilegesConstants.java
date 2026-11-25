package com.heuristiq.identity.auth;

import com.heuristiq.identity.common.UamCommonConstants;

/** Fixed values used when resolving a caller's privileges. */
final class MyPrivilegesConstants {

    /** Key in a permission's attributes holding the numeric id the gateway matches a request path against. */
    static final String PRIVILEGE_ID_ATTRIBUTE = UamCommonConstants.PERMISSION_PRIVILEGE_ID_ATTRIBUTE;

    private MyPrivilegesConstants() {
    }
}
