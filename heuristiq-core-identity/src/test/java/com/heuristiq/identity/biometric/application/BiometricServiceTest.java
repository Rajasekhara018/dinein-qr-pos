package com.heuristiq.identity.biometric.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.heuristiq.identity.biometric.application.port.AccountReauthentication;
import com.heuristiq.identity.biometric.application.port.BiometricSignIn;
import com.heuristiq.identity.biometric.domain.BiometricConstants;
import com.heuristiq.identity.biometric.domain.BiometricCredential;
import com.heuristiq.identity.biometric.domain.BiometricCredentialStatus;
import com.heuristiq.identity.biometric.domain.BiometricException;
import com.heuristiq.identity.biometric.domain.BiometricRevocationReason;
import com.heuristiq.identity.biometric.support.BiometricTestKeys;
import com.heuristiq.identity.biometric.support.InMemoryBiometricStores;
import com.heuristiq.identity.biometric.support.MutableClock;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.dto.LoginResponse;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Enrolment, listing, revocation, challenges and sign-in of device-key (fingerprint / Face ID) sign-in. */
class BiometricServiceTest {

    private static final Instant START = Instant.parse("2026-11-22T10:00:00Z");
    private static final String USER = "user-1";
    private static final String OTHER_USER = "user-2";
    private static final String APP = "MATRIMONY_APP";
    private static final String INSTALLATION = "6f1c2b9e-3c41-4b7a-9d55-0f6c1c3c8a11";
    private static final String SESSION = "session-1";
    private static final BiometricSettings SETTINGS = new BiometricSettings(Duration.ofMinutes(5), Duration.ofSeconds(60),
            Duration.ofDays(90), 5, Duration.ofMinutes(15));

    private final KeyPair key = BiometricTestKeys.p256();
    private MutableClock clock;
    private InMemoryBiometricStores.Credentials credentials;
    private InMemoryBiometricStores.Challenges challenges;
    private InMemoryBiometricStores.Attempts attempts;
    private InMemoryBiometricStores.RecordingAudit audit;
    private BiometricSignIn signIn;
    private AccountReauthentication reauth;
    private LoginResponse loginResponse;
    private BiometricService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        credentials = new InMemoryBiometricStores.Credentials();
        challenges = new InMemoryBiometricStores.Challenges();
        attempts = new InMemoryBiometricStores.Attempts();
        audit = new InMemoryBiometricStores.RecordingAudit();
        signIn = mock(BiometricSignIn.class);
        reauth = mock(AccountReauthentication.class);
        loginResponse = mock(LoginResponse.class);
        when(signIn.signIn(anyString())).thenReturn(loginResponse);
        when(reauth.interactiveSignInAt(SESSION, USER)).thenReturn(Optional.of(START.minusSeconds(60)));
        service = new BiometricService(credentials, challenges, attempts, audit, signIn, reauth, SETTINGS, clock, new SecureRandom());
    }

    private BiometricUseCase.EnrolCommand enrolCommand(String userId, String sessionId, String password, String installation, KeyPair pair) {
        return new BiometricUseCase.EnrolCommand(userId, "org-a", APP, sessionId, installation, BiometricTestKeys.spki(pair),
                "EC_P256_SHA256", "ANDROID", "Pixel 8", password);
    }

    private BiometricUseCase.EnrolResult enrol() {
        return service.enrol(enrolCommand(USER, SESSION, null, INSTALLATION, key));
    }

    private BiometricUseCase.VerifyCommand signed(BiometricUseCase.ChallengeResult challenge, KeyPair pair) {
        return new BiometricUseCase.VerifyCommand(challenge.challengeId(), INSTALLATION, BiometricTestKeys.sign(pair, challenge.challenge()), APP);
    }

    private static BiometricException.Kind kindOf(Throwable e) {
        return ((BiometricException) e).kind();
    }

    // ---- enrolment ------------------------------------------------------------------------------------------------

    @Test
    void aRecentInteractiveSignInEnrolsWithoutThePassword() {
        BiometricUseCase.EnrolResult result = enrol();

        assertThat(result.createdAt()).isEqualTo(START);
        assertThat(result.expiresAt()).isEqualTo(START.plus(Duration.ofDays(90)));
        BiometricCredential stored = credentials.findById(result.credentialId()).orElseThrow();
        assertThat(stored.status()).isEqualTo(BiometricCredentialStatus.ACTIVE);
        assertThat(stored.userId()).isEqualTo(USER);
        assertThat(stored.tenantId()).isEqualTo("org-a");
        assertThat(stored.applicationCode()).isEqualTo(APP);
        assertThat(stored.publicKey()).isEqualTo(BiometricTestKeys.spki(key));
        assertThat(audit.events).containsExactly("ENABLED:" + result.credentialId());
        verify(reauth, never()).passwordMatches(anyString(), anyString());
    }

    @Test
    void aSignInOlderThanTheWindowNeedsThePassword() {
        when(reauth.interactiveSignInAt(SESSION, USER)).thenReturn(Optional.of(START.minus(Duration.ofMinutes(5)).minusSeconds(1)));

        assertThatThrownBy(this::enrol).isInstanceOf(BiometricException.class)
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.REAUTH_REQUIRED));
        assertThat(credentials.all()).isEmpty();
    }

    @Test
    void aRefreshedOrBiometricSessionDoesNotCountAsARecentSignIn() {
        when(reauth.interactiveSignInAt(SESSION, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(this::enrol).satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.REAUTH_REQUIRED));
    }

    @Test
    void theCorrectCurrentPasswordEnrolsEvenWithoutASession() {
        when(reauth.passwordMatches(USER, "Secret#1")).thenReturn(true);

        BiometricUseCase.EnrolResult result = service.enrol(enrolCommand(USER, null, "Secret#1", INSTALLATION, key));

        assertThat(credentials.findById(result.credentialId())).isPresent();
    }

    @Test
    void aWrongCurrentPasswordIsRefusedEvenWithARecentSession() {
        when(reauth.passwordMatches(USER, "wrong")).thenReturn(false);

        assertThatThrownBy(() -> service.enrol(enrolCommand(USER, SESSION, "wrong", INSTALLATION, key)))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.REAUTH_REQUIRED));
        assertThat(credentials.all()).isEmpty();
    }

    @Test
    void noSessionAndNoPasswordIsRefused() {
        assertThatThrownBy(() -> service.enrol(enrolCommand(USER, null, null, INSTALLATION, key)))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.REAUTH_REQUIRED));
    }

    @Test
    void reEnrollingTheInstallationReplacesThePreviousKey() {
        BiometricUseCase.EnrolResult first = enrol();
        KeyPair newKey = BiometricTestKeys.p256();

        BiometricUseCase.EnrolResult second = service.enrol(enrolCommand(USER, SESSION, null, INSTALLATION, newKey));

        assertThat(credentials.findById(first.credentialId()).orElseThrow().status()).isEqualTo(BiometricCredentialStatus.REVOKED);
        assertThat(credentials.findById(first.credentialId()).orElseThrow().revokedReason()).isEqualTo(BiometricRevocationReason.REPLACED);
        assertThat(credentials.findActiveByInstallation(APP, INSTALLATION)).extracting(BiometricCredential::credentialId)
                .containsExactly(second.credentialId());
        assertThat(audit.events).contains("REVOKED:" + first.credentialId() + ":REPLACED", "ENABLED:" + second.credentialId());
    }

    @Test
    void anotherPersonEnrollingTheSameInstallationReplacesTheFirstOnesKey() {
        BiometricUseCase.EnrolResult first = enrol();
        when(reauth.interactiveSignInAt("session-2", OTHER_USER)).thenReturn(Optional.of(START));

        service.enrol(enrolCommand(OTHER_USER, "session-2", null, INSTALLATION, BiometricTestKeys.p256()));

        assertThat(credentials.findById(first.credentialId()).orElseThrow().status()).isEqualTo(BiometricCredentialStatus.REVOKED);
        assertThat(credentials.findActiveByInstallation(APP, INSTALLATION)).singleElement()
                .extracting(BiometricCredential::userId).isEqualTo(OTHER_USER);
    }

    @Test
    void unusableEnrolmentsAreInvalidRequests() {
        List<BiometricUseCase.EnrolCommand> bad = List.of(
                new BiometricUseCase.EnrolCommand(USER, "org-a", null, SESSION, INSTALLATION, BiometricTestKeys.spki(key), "EC_P256_SHA256", "ANDROID", null, null),
                new BiometricUseCase.EnrolCommand(USER, "org-a", APP, SESSION, "bad id!", BiometricTestKeys.spki(key), "EC_P256_SHA256", "ANDROID", null, null),
                new BiometricUseCase.EnrolCommand(USER, "org-a", APP, SESSION, INSTALLATION, BiometricTestKeys.spki(key), "EC_P384", "ANDROID", null, null),
                new BiometricUseCase.EnrolCommand(USER, "org-a", APP, SESSION, INSTALLATION, BiometricTestKeys.spki(key), "EC_P256_SHA256", "WEB", null, null),
                new BiometricUseCase.EnrolCommand(USER, "org-a", APP, SESSION, INSTALLATION, "not-a-key", "EC_P256_SHA256", "IOS", null, null),
                new BiometricUseCase.EnrolCommand(USER, "org-a", APP, SESSION, INSTALLATION, BiometricTestKeys.spki(key), "EC_P256_SHA256", "IOS", "x".repeat(101), null));
        for (BiometricUseCase.EnrolCommand command : bad) {
            assertThatThrownBy(() -> service.enrol(command))
                    .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.INVALID_REQUEST));
        }
        assertThat(credentials.all()).isEmpty();
    }

    @Test
    void theEnrolCommandNeverPrintsThePassword() {
        assertThat(enrolCommand(USER, SESSION, "Secret#1", INSTALLATION, key).toString()).doesNotContain("Secret#1");
    }

    // ---- listing and revoking own devices ---------------------------------------------------------------------------

    @Test
    void myDevicesAreOnlyMyActiveUnexpiredOnesWithTheCurrentInstallationMarked() {
        BiometricUseCase.EnrolResult mine = enrol();
        when(reauth.interactiveSignInAt("session-2", OTHER_USER)).thenReturn(Optional.of(START));
        service.enrol(enrolCommand(OTHER_USER, "session-2", null, "7f1c2b9e-3c41-4b7a-9d55-0f6c1c3c8a12", BiometricTestKeys.p256()));

        List<BiometricUseCase.DeviceView> devices = service.listMine(USER, INSTALLATION);

        assertThat(devices).singleElement().satisfies(d -> {
            assertThat(d.credentialId()).isEqualTo(mine.credentialId());
            assertThat(d.current()).isTrue();
            assertThat(d.platform()).isEqualTo("ANDROID");
            assertThat(d.deviceName()).isEqualTo("Pixel 8");
        });
        assertThat(service.listMine(USER, "other-installation")).singleElement().extracting(BiometricUseCase.DeviceView::current).isEqualTo(false);
        assertThat(service.listMine(USER, null)).singleElement().extracting(BiometricUseCase.DeviceView::current).isEqualTo(false);

        clock.advance(Duration.ofDays(91));
        assertThat(service.listMine(USER, INSTALLATION)).isEmpty();
    }

    @Test
    void aPersonCanRevokeTheirOwnDeviceButNotSomeoneElses() {
        BiometricUseCase.EnrolResult mine = enrol();

        assertThatThrownBy(() -> service.revokeMine(OTHER_USER, mine.credentialId()))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.NOT_FOUND));
        assertThat(credentials.findById(mine.credentialId()).orElseThrow().status()).isEqualTo(BiometricCredentialStatus.ACTIVE);
        assertThatThrownBy(() -> service.revokeMine(USER, "missing"))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.NOT_FOUND));

        service.revokeMine(USER, mine.credentialId());
        service.revokeMine(USER, mine.credentialId());

        assertThat(credentials.findById(mine.credentialId()).orElseThrow().revokedReason()).isEqualTo(BiometricRevocationReason.USER_REVOKED);
        assertThat(audit.events).containsOnlyOnce("REVOKED:" + mine.credentialId() + ":USER_REVOKED");
    }

    @Test
    void turningOffOnThisPhoneRevokesOnlyTheCallersCredentialOfThatInstallation() {
        BiometricUseCase.EnrolResult mine = enrol();

        service.revokeMineByInstallation(OTHER_USER, INSTALLATION);
        assertThat(credentials.findById(mine.credentialId()).orElseThrow().status()).isEqualTo(BiometricCredentialStatus.ACTIVE);

        service.revokeMineByInstallation(USER, INSTALLATION);
        assertThat(credentials.findById(mine.credentialId()).orElseThrow().status()).isEqualTo(BiometricCredentialStatus.REVOKED);
        assertThatThrownBy(() -> service.revokeMineByInstallation(USER, "!"))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.INVALID_REQUEST));
    }

    @Test
    void revokeAllForUserEndsEveryActiveCredentialWithTheReason() {
        BiometricUseCase.EnrolResult first = enrol();
        BiometricUseCase.EnrolResult second = service.enrol(enrolCommand(USER, SESSION, null, "8f1c2b9e-3c41-4b7a-9d55-0f6c1c3c8a13", BiometricTestKeys.p256()));

        assertThat(service.revokeAllForUser(USER, BiometricRevocationReason.PASSWORD_CHANGED)).isEqualTo(2);
        assertThat(service.revokeAllForUser(USER, BiometricRevocationReason.PASSWORD_CHANGED)).isZero();
        assertThat(service.revokeAllForUser(" ", BiometricRevocationReason.PASSWORD_CHANGED)).isZero();

        assertThat(audit.events).contains("REVOKED:" + first.credentialId() + ":PASSWORD_CHANGED",
                "REVOKED:" + second.credentialId() + ":PASSWORD_CHANGED");
        assertThat(credentials.findActiveByUser(USER)).isEmpty();
    }

    // ---- challenge ----------------------------------------------------------------------------------------------

    @Test
    void anUnknownInstallationStillGetsAChallengeOf32RandomBytes() {
        BiometricUseCase.ChallengeResult challenge = service.challenge("9f1c2b9e-3c41-4b7a-9d55-0f6c1c3c8a14");

        assertThat(Base64.getDecoder().decode(challenge.challenge())).hasSize(BiometricConstants.CHALLENGE_BYTES);
        assertThat(challenge.expiresAt()).isEqualTo(START.plusSeconds(60));
        assertThat(challenges.find(challenge.challengeId())).isPresent();
        assertThat(service.challenge(INSTALLATION).challenge()).isNotEqualTo(challenge.challenge());
    }

    @Test
    void aMalformedInstallationIdGetsNoChallenge() {
        assertThatThrownBy(() -> service.challenge("x"))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.INVALID_REQUEST));
    }

    // ---- verify -------------------------------------------------------------------------------------------------

    @Test
    void aValidSignatureSignsInLikeAPasswordLoginAndSlidesTheExpiry() {
        BiometricUseCase.EnrolResult enrolled = enrol();
        clock.advance(Duration.ofDays(10));
        BiometricUseCase.ChallengeResult challenge = service.challenge(INSTALLATION);

        LoginResponse response = service.verify(signed(challenge, key));

        assertThat(response).isSameAs(loginResponse);
        verify(signIn).signIn(USER);
        BiometricCredential stored = credentials.findById(enrolled.credentialId()).orElseThrow();
        assertThat(stored.lastUsedAt()).isEqualTo(START.plus(Duration.ofDays(10)));
        assertThat(stored.expiresAt()).isEqualTo(START.plus(Duration.ofDays(100)));
        assertThat(audit.events).endsWith("SIGNIN:" + enrolled.credentialId());
    }

    @Test
    void anRsaDeviceKeySignsInToo() {
        KeyPair rsa = BiometricTestKeys.rsa(2048);
        service.enrol(new BiometricUseCase.EnrolCommand(USER, "org-a", APP, SESSION, INSTALLATION, BiometricTestKeys.spki(rsa),
                "RSA_2048_SHA256", "IOS", null, null));
        BiometricUseCase.ChallengeResult challenge = service.challenge(INSTALLATION);

        assertThat(service.verify(signed(challenge, rsa))).isSameAs(loginResponse);
    }

    @Test
    void aChallengeIsAcceptedOnlyOnce() {
        enrol();
        BiometricUseCase.ChallengeResult challenge = service.challenge(INSTALLATION);
        BiometricUseCase.VerifyCommand command = signed(challenge, key);
        service.verify(command);

        assertThatThrownBy(() -> service.verify(command))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.SIGNIN_INVALID));
    }

    @Test
    void anExpiredChallengeIsRefused() {
        enrol();
        BiometricUseCase.ChallengeResult challenge = service.challenge(INSTALLATION);
        clock.advance(Duration.ofSeconds(61));

        assertThatThrownBy(() -> service.verify(signed(challenge, key)))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.SIGNIN_INVALID));
        verify(signIn, never()).signIn(anyString());
    }

    @Test
    void aChallengeIssuedToAnotherInstallationIsRefused() {
        enrol();
        BiometricUseCase.ChallengeResult challenge = service.challenge("af1c2b9e-3c41-4b7a-9d55-0f6c1c3c8a15");

        assertThatThrownBy(() -> service.verify(signed(challenge, key)))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.SIGNIN_INVALID));
    }

    @Test
    void aWrongSignatureIsInvalidAndAudited() {
        BiometricUseCase.EnrolResult enrolled = enrol();
        BiometricUseCase.ChallengeResult challenge = service.challenge(INSTALLATION);

        assertThatThrownBy(() -> service.verify(signed(challenge, BiometricTestKeys.p256())))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.SIGNIN_INVALID));
        assertThat(audit.events).contains("FAILED:" + enrolled.credentialId() + ":" + BiometricConstants.FAILURE_SIGNATURE);
        verify(signIn, never()).signIn(anyString());
    }

    @Test
    void anUnknownInstallationOrAnotherApplicationIsInvalid() {
        enrol();
        BiometricUseCase.ChallengeResult unknown = service.challenge("bf1c2b9e-3c41-4b7a-9d55-0f6c1c3c8a16");
        assertThatThrownBy(() -> service.verify(new BiometricUseCase.VerifyCommand(unknown.challengeId(),
                "bf1c2b9e-3c41-4b7a-9d55-0f6c1c3c8a16", BiometricTestKeys.sign(key, unknown.challenge()), APP)))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.SIGNIN_INVALID));

        BiometricUseCase.ChallengeResult other = service.challenge(INSTALLATION);
        assertThatThrownBy(() -> service.verify(new BiometricUseCase.VerifyCommand(other.challengeId(), INSTALLATION,
                BiometricTestKeys.sign(key, other.challenge()), "OTHER_APP")))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.SIGNIN_INVALID));

        BiometricUseCase.ChallengeResult noApp = service.challenge(INSTALLATION);
        assertThatThrownBy(() -> service.verify(new BiometricUseCase.VerifyCommand(noApp.challengeId(), INSTALLATION,
                BiometricTestKeys.sign(key, noApp.challenge()), null)))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.SIGNIN_INVALID));
        assertThatThrownBy(() -> service.verify(new BiometricUseCase.VerifyCommand("c", "bad!", "sig", APP)))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.SIGNIN_INVALID));
    }

    @Test
    void aRevokedCredentialAnswersRevokedOnlyToItsOwnKey() {
        BiometricUseCase.EnrolResult enrolled = enrol();
        service.revokeAllForUser(USER, BiometricRevocationReason.PASSWORD_RESET);

        BiometricUseCase.ChallengeResult forged = service.challenge(INSTALLATION);
        assertThatThrownBy(() -> service.verify(signed(forged, BiometricTestKeys.p256())))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.SIGNIN_INVALID));

        BiometricUseCase.ChallengeResult genuine = service.challenge(INSTALLATION);
        assertThatThrownBy(() -> service.verify(signed(genuine, key)))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.CREDENTIAL_REVOKED));
        assertThat(audit.events).contains("FAILED:" + enrolled.credentialId() + ":" + BiometricConstants.FAILURE_CREDENTIAL_REVOKED);
        verify(signIn, never()).signIn(anyString());
    }

    @Test
    void aDeviceUnusedForTheInactivityWindowExpires() {
        BiometricUseCase.EnrolResult enrolled = enrol();
        clock.advance(Duration.ofDays(90));
        BiometricUseCase.ChallengeResult challenge = service.challenge(INSTALLATION);

        assertThatThrownBy(() -> service.verify(signed(challenge, key)))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.CREDENTIAL_REVOKED));
        BiometricCredential stored = credentials.findById(enrolled.credentialId()).orElseThrow();
        assertThat(stored.status()).isEqualTo(BiometricCredentialStatus.EXPIRED);
        assertThat(stored.revokedReason()).isEqualTo(BiometricRevocationReason.INACTIVITY);
        assertThat(audit.events).contains("REVOKED:" + enrolled.credentialId() + ":INACTIVITY");
    }

    @Test
    void fiveFailuresInTheWindowBlockTheInstallationUntilTheWindowEnds() {
        enrol();
        KeyPair wrong = BiometricTestKeys.p256();
        for (int i = 0; i < 5; i++) {
            BiometricUseCase.ChallengeResult challenge = service.challenge(INSTALLATION);
            assertThatThrownBy(() -> service.verify(signed(challenge, wrong)))
                    .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.SIGNIN_INVALID));
        }
        assertThat(audit.events).contains("FAILED:" + credentials.all().get(0).credentialId() + ":" + BiometricConstants.FAILURE_RATE_LIMITED);

        BiometricUseCase.ChallengeResult blocked = service.challenge(INSTALLATION);
        assertThatThrownBy(() -> service.verify(signed(blocked, key)))
                .satisfies(e -> assertThat(kindOf(e)).isEqualTo(BiometricException.Kind.TOO_MANY_ATTEMPTS));
        verify(signIn, never()).signIn(anyString());

        clock.advance(Duration.ofMinutes(15));
        BiometricUseCase.ChallengeResult later = service.challenge(INSTALLATION);
        assertThat(service.verify(signed(later, key))).isSameAs(loginResponse);
    }

    @Test
    void aSuccessfulSignInClearsEarlierFailures() {
        enrol();
        for (int i = 0; i < 4; i++) {
            BiometricUseCase.ChallengeResult challenge = service.challenge(INSTALLATION);
            assertThatThrownBy(() -> service.verify(signed(challenge, BiometricTestKeys.p256()))).isInstanceOf(BiometricException.class);
        }
        service.verify(signed(service.challenge(INSTALLATION), key));

        assertThat(attempts.failures(INSTALLATION, clock.instant())).isZero();
    }

    @Test
    void refusalsOfTheSharedSignInPassThroughAndDoNotCountAsDeviceFailures() {
        enrol();
        when(signIn.signIn(USER)).thenThrow(new UamException("User is not active"));
        BiometricUseCase.ChallengeResult challenge = service.challenge(INSTALLATION);

        assertThatThrownBy(() -> service.verify(signed(challenge, key))).isInstanceOf(UamException.class).hasMessage("User is not active");
        assertThat(attempts.failures(INSTALLATION, clock.instant())).isZero();
    }

    @Test
    void theRevocationHelperIsANoOpWithoutTheUseCase() {
        BiometricRevocations.revokeAll(null, USER, BiometricRevocationReason.LOGOUT_ALL);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<BiometricRevocationUseCase> empty = mock(org.springframework.beans.factory.ObjectProvider.class);
        BiometricRevocations.revokeAll(empty, USER, BiometricRevocationReason.LOGOUT_ALL);

        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<BiometricRevocationUseCase> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
        BiometricRevocationUseCase useCase = mock(BiometricRevocationUseCase.class);
        when(provider.getIfAvailable()).thenReturn(useCase);
        BiometricRevocations.revokeAll(provider, "", BiometricRevocationReason.LOGOUT_ALL);
        verify(useCase, never()).revokeAllForUser(any(), any());
        BiometricRevocations.revokeAll(provider, USER, BiometricRevocationReason.LOGOUT_ALL);
        verify(useCase).revokeAllForUser(USER, BiometricRevocationReason.LOGOUT_ALL);
    }
}
