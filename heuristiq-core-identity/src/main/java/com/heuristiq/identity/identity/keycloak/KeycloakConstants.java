package com.heuristiq.identity.identity.keycloak;

/** Keycloak wire contract used by the Keycloak adapters: endpoint paths, admin client, attributes and naming. */
final class KeycloakConstants {

    /** Path segment before the realm name. */
    static final String REALMS_PATH = "/realms/";

    /** OpenID Connect endpoints, relative to the realm. */
    static final String TOKEN_PATH = "/protocol/openid-connect/token";
    static final String LOGOUT_PATH = "/protocol/openid-connect/logout";
    static final String USERINFO_PATH = "/protocol/openid-connect/userinfo";

    /** Realm and client used to sign in to the Keycloak admin API with admin credentials. */
    static final String ADMIN_REALM = "master";
    static final String ADMIN_CLIENT_ID = "admin-cli";

    /** Keycloak client authenticator that checks a client id and secret. */
    static final String CLIENT_SECRET_AUTHENTICATOR = "client-secret";

    /** Prefix of the Keycloak role that mirrors a UAM permission. */
    static final String PERMISSION_PREFIX = "PERMISSION_";

    /** Keycloak required action that makes the user set up an authenticator app. */
    static final String CONFIGURE_TOTP_ACTION = "CONFIGURE_TOTP";

    /** Keycloak user attributes written by UAM. */
    static final String USER_ID_ATTRIBUTE = "railcab_user_id";
    static final String UPDATED_AT_ATTRIBUTE = "updated_at";

    /** Prefix of the Keycloak realm of an organization (followed by its lower-case code). */
    static final String TENANT_REALM_PREFIX = "railcab-";

    private KeycloakConstants() {
    }
}
