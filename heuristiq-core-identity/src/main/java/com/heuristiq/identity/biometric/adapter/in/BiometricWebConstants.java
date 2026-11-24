package com.heuristiq.uam.biometric.adapter.in;

/** Paths, headers and request attributes of the biometric sign-in endpoints. */
final class BiometricWebConstants {

    /** Self-service (signed-in, not in the privilege catalog): the caller's own enrolled devices. */
    static final String MY_DEVICES_PATH = "/api/v1/uam/identity/me/biometric-devices";

    /** Public (like login): challenge and verify. */
    static final String SIGN_IN_PATH = "/api/v1/uam/identity/auth/biometric";

    /** The app's installation id, sent on the device list so the current phone can be marked. */
    static final String INSTALLATION_ID_HEADER = "X-Installation-Id";

    /** Request attribute the JWT filter sets to the token's session id ({@code sid}) for a policy-checked session. */
    static final String SESSION_ID_ATTRIBUTE = "uam.policySessionId";

    /** Operation result names. */
    static final String OPERATION_REVOKE = "REVOKE";
    static final String RESULT_ENTITY = "BIOMETRIC_CREDENTIAL";

    private BiometricWebConstants() {
    }
}
