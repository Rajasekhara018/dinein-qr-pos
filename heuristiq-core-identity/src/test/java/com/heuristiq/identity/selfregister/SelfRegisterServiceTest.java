package com.heuristiq.identity.selfregister;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.notification.NmsNotificationService;
import com.heuristiq.identity.otp.OtpChallenge;
import com.heuristiq.identity.otp.OtpChallengeManager;
import com.heuristiq.identity.otp.OtpStore;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;

/** The sign-up email check against the real one-time-code rules, with an in-memory store and a stubbed mailer. */
class SelfRegisterServiceTest {
    private static final String APP = "PASUPU_KUMKUMA_MATRIMONY_APP";

    /** Same optimistic compare-and-save contract as the real stores. */
    private static final class MemoryStore implements OtpStore {
        private final ConcurrentHashMap<String, OtpChallenge> rows = new ConcurrentHashMap<>();

        @Override
        public Optional<OtpChallenge> find(String key) {
            OtpChallenge row = rows.get(key);
            return Optional.ofNullable(row == null ? null : row.copy());
        }

        @Override
        public synchronized boolean compareAndSave(OtpChallenge challenge) {
            OtpChallenge current = rows.get(challenge.getKey());
            if (challenge.getVersion() == null) {
                if (current != null) {
                    return false;
                }
                challenge.setVersion(0L);
            } else {
                if (current == null || !current.getVersion().equals(challenge.getVersion())) {
                    return false;
                }
                challenge.setVersion(challenge.getVersion() + 1);
            }
            rows.put(challenge.getKey(), challenge.copy());
            return true;
        }
    }

    private MemoryStore store;
    private UamUserRepository users;
    private NmsNotificationService notifications;
    private SelfRegistrationBotCheck botCheck;
    private SelfRegisterProperties properties;
    private OtpChallengeManager base;
    private com.heuristiq.identity.otp.UamOtpAuthServiceUseCase otpAuth;

    @BeforeEach
    void setUp() {
        store = new MemoryStore();
        users = mock(UamUserRepository.class);
        notifications = mock(NmsNotificationService.class);
        botCheck = mock(SelfRegistrationBotCheck.class);
        otpAuth = mock(com.heuristiq.identity.otp.UamOtpAuthServiceUseCase.class);
        when(botCheck.passes(any(), any())).thenReturn(true);
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        properties = SelfRegisterProperties.defaults();
        base = new OtpChallengeManager(store, 5, 5, 0, 50);
    }

    private SelfRegisterService service() {
        return service(50);
    }

    private SelfRegisterService service(int maxSendsPerHour) {
        return new SelfRegisterService(users, notifications, properties, botCheck, otpAuth, base, 5, 5, 0, maxSendsPerHour);
    }

    private String sentCode() {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(notifications).sendOtp(eq("jane@example.com"), eq(null), eq("there"), code.capture(), eq(5), eq("Create your account"));
        return code.getValue();
    }

    @Test
    void aNewAddressGetsACodeAndTheRightCodeReturnsASingleUseToken() {
        SelfRegisterService service = service();

        SelfRegisterUseCase.SendResult sent = service.sendOtp(" Jane@Example.com ", APP, null, "203.0.113.5");
        String code = sentCode();
        SelfRegisterUseCase.VerifyResult verified = service.verifyOtp("jane@example.com", code, APP);

        assertThat(sent.sent()).isTrue();
        assertThat(code).matches("\\d{6}");
        assertThat(verified.verificationToken()).hasSizeGreaterThan(30);
        assertThat(verified.expiresInSeconds()).isEqualTo(15 * 60);
        assertThat(service.consumeToken("JANE@example.com", verified.verificationToken(), APP)).isTrue();
        assertThat(service.consumeToken("jane@example.com", verified.verificationToken(), APP)).as("single use").isFalse();
    }

    @Test
    void theCodeIsSingleUseAndAWrongCodeIsRefusedWithAMachineReadableCode() {
        SelfRegisterService service = service();
        service.sendOtp("jane@example.com", APP, null, "203.0.113.5");
        String code = sentCode();

        assertThatThrownBy(() -> service.verifyOtp("jane@example.com", "000000".equals(code) ? "111111" : "000000", APP))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getErrorCode()).isEqualTo("OTP_INVALID");
                });
        service.verifyOtp("jane@example.com", code, APP);
        assertThatThrownBy(() -> service.verifyOtp("jane@example.com", code, APP)).isInstanceOf(UamException.class);
    }

    @Test
    void anAddressThatAlreadyHasAnAccountGetsTheSameAnswerAndNoCodeAndCannotVerify() {
        when(users.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(mock(UamUser.class)));
        SelfRegisterService service = service();

        SelfRegisterUseCase.SendResult existing = service.sendOtp("jane@example.com", APP, null, "203.0.113.5");

        verify(notifications, never()).sendOtp(any(), any(), any(), any(), anyInt(), any());
        verify(notifications).sendSelfRegisterExistingAccount(eq("jane@example.com"), eq(APP), anyString());
        assertThat(existing).isEqualTo(new SelfRegisterUseCase.SendResult(true, 0));
        assertThatThrownBy(() -> service.verifyOtp("jane@example.com", "123456", APP))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getErrorCode()).isEqualTo("OTP_INVALID"));
    }

    @Test
    void knownAndUnknownAddressesAreLimitedIdentically() {
        when(users.findByEmailIgnoreCase("known@example.com")).thenReturn(Optional.of(mock(UamUser.class)));
        SelfRegisterService service = service(2);

        for (String email : new String[] {"known@example.com", "unknown@example.com"}) {
            service.sendOtp(email, APP, null, "203.0.113." + email.length());
            service.sendOtp(email, APP, null, "203.0.113." + email.length());
            assertThatThrownBy(() -> service.sendOtp(email, APP, null, "203.0.113." + email.length()))
                    .as(email).isInstanceOfSatisfying(UamException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                        assertThat(e.getErrorCode()).isEqualTo("OTP_RATE_LIMITED");
                    });
        }
    }

    @Test
    void oneClientAddressCannotDriveMailToManyPeople() {
        properties = new SelfRegisterProperties(true, 3, 15, List.of(), "none");
        SelfRegisterService service = service();

        service.sendOtp("a@example.com", APP, null, "198.51.100.7");
        service.sendOtp("b@example.com", APP, null, "198.51.100.7");
        service.sendOtp("c@example.com", APP, null, "198.51.100.7");

        assertThatThrownBy(() -> service.sendOtp("d@example.com", APP, null, "198.51.100.7"))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        service.sendOtp("d@example.com", APP, null, "198.51.100.8");
    }

    @Test
    void theKillSwitchRefusesEveryCall() {
        properties = new SelfRegisterProperties(false, 20, 15, List.of(), "none");
        SelfRegisterService service = service();

        assertThatThrownBy(() -> service.sendOtp("jane@example.com", APP, null, "203.0.113.5"))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getErrorCode()).isEqualTo("SELF_REGISTRATION_DISABLED");
                });
        assertThatThrownBy(() -> service.verifyOtp("jane@example.com", "123456", APP)).isInstanceOf(UamException.class);
        verify(notifications, never()).sendOtp(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void blockedDomainsABadApplicationAndAFailedBotCheckAreRefusedBeforeAnythingIsSent() {
        properties = new SelfRegisterProperties(true, 20, 15, List.of("Mailinator.com"), "none");
        SelfRegisterService service = service();

        assertThatThrownBy(() -> service.sendOtp("x@mailinator.com", APP, null, "203.0.113.5"))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getErrorCode()).isEqualTo("EMAIL_NOT_ALLOWED"));
        assertThatThrownBy(() -> service.sendOtp("no-at-sign", APP, null, "203.0.113.5"))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getErrorCode()).isEqualTo("EMAIL_NOT_ALLOWED"));
        assertThatThrownBy(() -> service.sendOtp("jane@example.com", "bad code!", null, "203.0.113.5"))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getErrorCode()).isEqualTo("APPLICATION_CODE_INVALID"));
        when(botCheck.passes("bad", "203.0.113.5")).thenReturn(false);
        assertThatThrownBy(() -> service.sendOtp("jane@example.com", APP, "bad", "203.0.113.5"))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getErrorCode()).isEqualTo("BOT_CHECK_FAILED"));

        verify(notifications, never()).sendOtp(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void theTokenIsBoundToTheEmailAndTheApplication() {
        SelfRegisterService service = service();
        service.sendOtp("jane@example.com", APP, null, "203.0.113.5");
        String token = service.verifyOtp("jane@example.com", sentCode(), APP).verificationToken();

        assertThat(service.consumeToken("someone-else@example.com", token, APP)).isFalse();
        assertThat(service.consumeToken("jane@example.com", token, "OTHER_APP")).isFalse();
        assertThat(service.consumeToken("jane@example.com", "not-the-token", APP)).isFalse();
        assertThat(service.consumeToken("jane@example.com", token, APP)).as("the failed tries did not spend it").isTrue();
    }

    @Test
    void blankInputsNeverConsumeAnything() {
        SelfRegisterService service = service();

        assertThat(service.consumeToken(null, "t", APP)).isFalse();
        assertThat(service.consumeToken("jane@example.com", " ", APP)).isFalse();
        assertThat(service.consumeToken("jane@example.com", "t", "bad code")).isFalse();
        assertThatThrownBy(() -> service.verifyOtp("jane@example.com", " ", APP)).isInstanceOf(UamException.class);
    }

    @Test
    void theSetPasswordCodeIsTheSameCustomerEmailCodeAsForgotPasswordAndNeverFailsTheCaller() {
        SelfRegisterService service = service();

        service.sendSetPasswordCode(" Jane@Example.com ");
        verify(otpAuth).sendEmailOtp(new com.heuristiq.identity.otp.UamOtpAuthServiceUseCase.SendEmailOtpInput("jane@example.com",
                com.heuristiq.identity.enums.UamEnums.UserType.CUSTOMER));

        org.mockito.Mockito.doThrow(new UamException("No registered account found for this email.")).when(otpAuth)
                .sendEmailOtp(any());
        service.sendSetPasswordCode("ghost@example.com");
        org.mockito.Mockito.doThrow(new UamException("Too many requests.", HttpStatus.TOO_MANY_REQUESTS)).when(otpAuth).sendEmailOtp(any());
        service.sendSetPasswordCode("jane@example.com");

        org.mockito.Mockito.clearInvocations(otpAuth);
        service.sendSetPasswordCode(" ");
        service.sendSetPasswordCode(null);
        verify(otpAuth, never()).sendEmailOtp(any());
    }

    @Test
    void nothingSecretIsStoredInThePlain() {
        SelfRegisterService service = service();
        service.sendOtp("jane@example.com", APP, null, "203.0.113.5");
        String code = sentCode();
        String token = service.verifyOtp("jane@example.com", code, APP).verificationToken();

        OtpChallenge tokenRow = store.rows.get(SelfRegisterKeys.tokenKey("jane@example.com", APP));
        OtpChallenge addressRow = store.rows.get(SelfRegisterKeys.addressKey("203.0.113.5"));
        assertThat(tokenRow.getOtpHash()).doesNotContain(token);
        assertThat(store.rows.keySet()).noneMatch(key -> key.contains("203.0.113.5"));
        assertThat(addressRow).isNotNull();
    }
}
