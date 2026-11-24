package com.heuristiq.uam.biometric.adapter.in;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.uam.biometric.application.BiometricService;
import com.heuristiq.uam.biometric.application.BiometricSettings;
import com.heuristiq.uam.biometric.application.port.AccountReauthentication;
import com.heuristiq.uam.biometric.application.port.BiometricSignIn;
import com.heuristiq.uam.biometric.domain.BiometricCredentialStatus;
import com.heuristiq.uam.biometric.support.BiometricTestKeys;
import com.heuristiq.uam.biometric.support.InMemoryBiometricStores;
import com.heuristiq.uam.biometric.support.MutableClock;
import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.GlobalExceptionHandler;
import com.heuristiq.uam.dto.LoginResponse;
import com.heuristiq.uam.security.TenantContext;
import jakarta.validation.Validation;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The biometric endpoints end to end over the real use case (in-memory stores): envelope, headers, error codes, and that a
 * person only ever sees and revokes their own devices, whichever organization either session is in.
 */
class BiometricControllersTest {

    private static final String DEVICES = "/api/v1/uam/identity/me/biometric-devices";
    private static final String SIGN_IN = "/api/v1/uam/identity/auth/biometric";
    private static final String APP = "MATRIMONY_APP";
    private static final String INSTALLATION_A = "6f1c2b9e-3c41-4b7a-9d55-0f6c1c3c8a11";
    private static final String INSTALLATION_B = "7f1c2b9e-3c41-4b7a-9d55-0f6c1c3c8a12";

    private final ObjectMapper json = new ObjectMapper();
    private final KeyPair keyA = BiometricTestKeys.p256();
    private InMemoryBiometricStores.Credentials credentials;
    private AccountReauthentication reauth;
    private BiometricSignIn signIn;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        credentials = new InMemoryBiometricStores.Credentials();
        reauth = mock(AccountReauthentication.class);
        signIn = mock(BiometricSignIn.class);
        Instant now = Instant.parse("2026-11-22T10:00:00Z");
        when(reauth.interactiveSignInAt(anyString(), anyString())).thenReturn(Optional.of(now));
        BiometricService service = new BiometricService(credentials, new InMemoryBiometricStores.Challenges(),
                new InMemoryBiometricStores.Attempts(), new InMemoryBiometricStores.RecordingAudit(), signIn, reauth,
                new BiometricSettings(Duration.ofMinutes(5), Duration.ofSeconds(60), Duration.ofDays(90), 5, Duration.ofMinutes(15)),
                new MutableClock(now), new SecureRandom());
        CommonRequestMapper mapper = new CommonRequestMapper(json, Validation.buildDefaultValidatorFactory().getValidator());
        mvc = MockMvcBuilders.standaloneSetup(new MyBiometricDevicesController(service, mapper), new BiometricSignInController(service, mapper))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static UsernamePasswordAuthenticationToken user(String id) {
        return new UsernamePasswordAuthenticationToken(id, null, java.util.List.of());
    }

    private String body(Object... kv) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            payload.put((String) kv[i], kv[i + 1]);
        }
        return json.writeValueAsString(Map.of("requestObject", payload));
    }

    private MockHttpServletRequestBuilder enrolRequest(String userId, String installation, KeyPair key, String organization) throws Exception {
        TenantContext.set(organization);
        return post(DEVICES).principal(user(userId)).header("X-Application-Code", APP)
                .requestAttr("uam.policySessionId", "session-" + userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("installationId", installation, "publicKey", BiometricTestKeys.spki(key), "keyAlgorithm", "EC_P256_SHA256",
                        "platform", "ANDROID", "deviceName", "Android phone"));
    }

    private String enrol(String userId, String installation, KeyPair key, String organization) throws Exception {
        MvcResult result = mvc.perform(enrolRequest(userId, installation, key, organization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.credentialId").isNotEmpty())
                .andExpect(jsonPath("$.responseObject.expiresAt").exists())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).at("/responseObject/credentialId").asText();
    }

    @Test
    void enrolWithoutARecentSignInOrPasswordAnswersReauthRequired() throws Exception {
        when(reauth.interactiveSignInAt(anyString(), anyString())).thenReturn(Optional.empty());

        mvc.perform(enrolRequest("user-a", INSTALLATION_A, keyA, "org-a"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("REAUTH_REQUIRED"));
    }

    @Test
    void enrolWithoutTheApplicationHeaderIsABadRequest() throws Exception {
        mvc.perform(post(DEVICES).principal(user("user-a")).requestAttr("uam.policySessionId", "s")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("installationId", INSTALLATION_A, "publicKey", BiometricTestKeys.spki(keyA),
                                "keyAlgorithm", "EC_P256_SHA256", "platform", "IOS")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPersonListsAndRevokesOnlyTheirOwnDevicesWhateverTheOrganization() throws Exception {
        String a = enrol("user-a", INSTALLATION_A, keyA, "org-a");
        String b = enrol("user-b", INSTALLATION_B, BiometricTestKeys.p256(), "org-b");

        TenantContext.set("org-a");
        mvc.perform(get(DEVICES).principal(user("user-a")).header("X-Installation-Id", INSTALLATION_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.length()").value(1))
                .andExpect(jsonPath("$.responseObject[0].credentialId").value(a))
                .andExpect(jsonPath("$.responseObject[0].current").value(true))
                .andExpect(jsonPath("$.responseObject[0].deviceName").value("Android phone"));

        // A member of organization A cannot revoke the device of a member of organization B, even by its id.
        mvc.perform(delete(DEVICES + "/" + b).principal(user("user-a")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BIOMETRIC_DEVICE_NOT_FOUND"));
        mvc.perform(delete(DEVICES + "/installation/" + INSTALLATION_B).principal(user("user-a"))).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(credentials.findById(b).orElseThrow().status()).isEqualTo(BiometricCredentialStatus.ACTIVE);

        mvc.perform(delete(DEVICES + "/" + a).principal(user("user-a"))).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(credentials.findById(a).orElseThrow().status()).isEqualTo(BiometricCredentialStatus.REVOKED);

        TenantContext.set("org-b");
        mvc.perform(get(DEVICES).principal(user("user-b")))
                .andExpect(jsonPath("$.responseObject.length()").value(1))
                .andExpect(jsonPath("$.responseObject[0].credentialId").value(b))
                .andExpect(jsonPath("$.responseObject[0].current").value(false));
        mvc.perform(delete(DEVICES + "/installation/" + INSTALLATION_B).principal(user("user-b"))).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(credentials.findById(b).orElseThrow().status()).isEqualTo(BiometricCredentialStatus.REVOKED);
    }

    @Test
    void challengeThenVerifyAnswersTheLoginResponse() throws Exception {
        enrol("user-a", INSTALLATION_A, keyA, "org-a");
        LoginResponse response = new LoginResponse("t", "t", "t", "r", "user-a", null, null, "jane", null, null, null, null,
                java.util.List.of(), null, null, "org-a", "ORG_A", "Org A", false);
        when(signIn.signIn("user-a")).thenReturn(response);

        MvcResult challenge = mvc.perform(post(SIGN_IN + "/challenge").contentType(MediaType.APPLICATION_JSON)
                        .content(body("installationId", INSTALLATION_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.responseObject.challenge").isNotEmpty())
                .andReturn();
        var node = json.readTree(challenge.getResponse().getContentAsString()).at("/responseObject");
        String challengeId = node.get("challengeId").asText();
        String text = node.get("challenge").asText();

        mvc.perform(post(SIGN_IN + "/verify").header("X-Application-Code", APP).contentType(MediaType.APPLICATION_JSON)
                        .content(body("challengeId", challengeId, "installationId", INSTALLATION_A, "signature", BiometricTestKeys.sign(keyA, text))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.refreshToken").value("r"))
                .andExpect(jsonPath("$.responseObject.organizationCode").value("ORG_A"));

        // The same challenge a second time: one undifferentiated refusal.
        mvc.perform(post(SIGN_IN + "/verify").header("X-Application-Code", APP).contentType(MediaType.APPLICATION_JSON)
                        .content(body("challengeId", challengeId, "installationId", INSTALLATION_A, "signature", BiometricTestKeys.sign(keyA, text))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("BIOMETRIC_SIGNIN_INVALID"));
    }

    @Test
    void aRevokedDeviceAnswersCredentialRevokedAndRepeatedFailuresAnswer429() throws Exception {
        String id = enrol("user-a", INSTALLATION_A, keyA, "org-a");
        mvc.perform(delete(DEVICES + "/" + id).principal(user("user-a"))).andExpect(status().isOk());

        mvc.perform(verifyRequest(keyA)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("BIOMETRIC_CREDENTIAL_REVOKED"));
        for (int i = 0; i < 4; i++) {
            mvc.perform(verifyRequest(BiometricTestKeys.p256())).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("BIOMETRIC_SIGNIN_INVALID"));
        }
        mvc.perform(verifyRequest(keyA)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_ATTEMPTS"));
    }

    private MockHttpServletRequestBuilder verifyRequest(KeyPair signer) throws Exception {
        MvcResult challenge = mvc.perform(post(SIGN_IN + "/challenge").contentType(MediaType.APPLICATION_JSON)
                .content(body("installationId", INSTALLATION_A))).andReturn();
        var node = json.readTree(challenge.getResponse().getContentAsString()).at("/responseObject");
        return post(SIGN_IN + "/verify").header("X-Application-Code", APP).contentType(MediaType.APPLICATION_JSON)
                .content(body("challengeId", node.get("challengeId").asText(), "installationId", INSTALLATION_A,
                        "signature", BiometricTestKeys.sign(signer, node.get("challenge").asText())));
    }
}
