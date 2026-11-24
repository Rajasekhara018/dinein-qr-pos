package com.heuristiq.uam.mfa;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.enums.UamEnums.MfaType;
import com.heuristiq.uam.mfa.UserMfaServiceUseCase.DeleteInput;
import com.heuristiq.uam.mfa.UserMfaServiceUseCase.DisableInput;
import com.heuristiq.uam.mfa.UserMfaServiceUseCase.EnrollTotpInput;
import com.heuristiq.uam.mfa.UserMfaServiceUseCase.GetInput;
import com.heuristiq.uam.mfa.UserMfaServiceUseCase.ListByUserInput;
import com.heuristiq.uam.mfa.UserMfaServiceUseCase.ListEnabledByUserInput;
import com.heuristiq.uam.mfa.UserMfaServiceUseCase.VerifyCodeInput;
import com.heuristiq.uam.mfa.UserMfaServiceUseCase.VerifyEnrollmentInput;
import com.heuristiq.uam.mfa.repository.UserMfaRepository;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserMfaServiceTest {
    private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
    private UserMfaRepository mfaRepository;
    private UamUserRepository userRepository;
    private UserMfaService service;

    @BeforeEach
    void setUp() {
        mfaRepository = mock(UserMfaRepository.class);
        userRepository = mock(UamUserRepository.class);
        when(mfaRepository.save(any(UserMfa.class))).thenAnswer(inv -> {
            UserMfa m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId("generated");
            }
            return m;
        });
        service = new UserMfaService(mfaRepository, userRepository);
    }

    private static String currentCode() throws Exception {
        return new DefaultCodeGenerator().generate(SECRET, System.currentTimeMillis() / 1000 / 30);
    }

    private UserMfa pending(String id) {
        UserMfa mfa = UserMfa.builder().id(id).userId("u1").mfaType(MfaType.TOTP).secret(SECRET)
                .enabled(false).verified(false).build();
        when(mfaRepository.findById(id)).thenReturn(Optional.of(mfa));
        return mfa;
    }

    @Test
    void listByUserAndEnabledFilter() {
        UserMfa on = UserMfa.builder().id("a").enabled(true).build();
        UserMfa off = UserMfa.builder().id("b").enabled(false).build();
        UserMfa unset = UserMfa.builder().id("c").enabled(null).build();
        when(mfaRepository.findByUserId("u1")).thenReturn(List.of(on, off, unset));

        assertThat(service.listByUser(new ListByUserInput("u1")).mfas()).hasSize(3);
        assertThat(service.listEnabledByUser(new ListEnabledByUserInput("u1")).mfas()).containsExactly(on);
    }

    @Test
    void getDeleteAndDisable() {
        UserMfa mfa = UserMfa.builder().id("a").enabled(true).build();
        when(mfaRepository.findById("a")).thenReturn(Optional.of(mfa));
        when(mfaRepository.findById("zz")).thenReturn(Optional.empty());

        assertThat(service.get(new GetInput("a")).mfa()).isSameAs(mfa);
        assertThatThrownBy(() -> service.get(new GetInput("zz"))).isInstanceOf(UamException.class);

        assertThat(service.disable(new DisableInput("a")).mfa().getEnabled()).isFalse();
        service.delete(new DeleteInput("a"));
        verify(mfaRepository).delete(mfa);
        assertThatThrownBy(() -> service.delete(new DeleteInput("zz"))).isInstanceOf(UamException.class);
    }

    @Test
    void verifyCodeAcceptsCurrentCodeAndRejectsBlankOrWrong() throws Exception {
        UserMfa mfa = UserMfa.builder().secret(SECRET).build();
        assertThat(service.verifyCode(new VerifyCodeInput(mfa, currentCode())).verified()).isTrue();
        assertThat(service.verifyCode(new VerifyCodeInput(mfa, "abcdef")).verified()).isFalse();
        assertThat(service.verifyCode(new VerifyCodeInput(mfa, " ")).verified()).isFalse();
        assertThat(service.verifyCode(new VerifyCodeInput(mfa, null)).verified()).isFalse();
        assertThat(service.verifyCode(new VerifyCodeInput(UserMfa.builder().build(), "123456")).verified()).isFalse();
    }

    @Test
    void enrollCreatesDisabledDraftWithQrCode() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(UamUser.builder().id("u1").userName("jane").email("j@x.com").build()));
        when(mfaRepository.findByUserIdAndMfaType("u1", MfaType.TOTP)).thenReturn(Optional.empty());

        MfaEnrollTotpResponse response = service.enrollTotp(new EnrollTotpInput("u1")).response();

        assertThat(response.id()).isEqualTo("generated");
        assertThat(response.secret()).isNotBlank();
        assertThat(response.otpAuthUrl()).startsWith("otpauth://totp/").contains("RailCab");
        assertThat(Base64.getDecoder().decode(response.qrCodeImageBase64())).isNotEmpty();
        org.mockito.ArgumentCaptor<UserMfa> captor = org.mockito.ArgumentCaptor.forClass(UserMfa.class);
        verify(mfaRepository).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
        assertThat(captor.getValue().getVerified()).isFalse();
        assertThat(captor.getValue().getMfaType()).isEqualTo(MfaType.TOTP);
    }

    @Test
    void enrollUsesUsernameLabelWhenNoEmailAndReusesAbandonedDraft() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(UamUser.builder().id("u1").userName("jane").build()));
        UserMfa abandoned = UserMfa.builder().id("old").userId("u1").mfaType(MfaType.TOTP).enabled(false).secret("stale").build();
        when(mfaRepository.findByUserIdAndMfaType("u1", MfaType.TOTP)).thenReturn(Optional.of(abandoned));

        MfaEnrollTotpResponse response = service.enrollTotp(new EnrollTotpInput("u1")).response();

        assertThat(response.id()).isEqualTo("old");
        assertThat(abandoned.getSecret()).isNotEqualTo("stale").isEqualTo(response.secret());
        assertThat(response.otpAuthUrl()).contains("jane");
    }

    @Test
    void enrollRejectsUnknownUserAndAlreadyEnabledMethod() {
        when(userRepository.findById("none")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.enrollTotp(new EnrollTotpInput("none"))).hasMessageContaining("User not found");

        when(userRepository.findById("u1")).thenReturn(Optional.of(UamUser.builder().id("u1").userName("jane").build()));
        when(mfaRepository.findByUserIdAndMfaType("u1", MfaType.TOTP))
                .thenReturn(Optional.of(UserMfa.builder().enabled(true).build()));
        assertThatThrownBy(() -> service.enrollTotp(new EnrollTotpInput("u1"))).hasMessageContaining("already enabled");
        verify(mfaRepository, never()).save(any());
    }

    @Test
    void verifyEnrollmentActivatesAndMakesFirstMethodPrimary() throws Exception {
        UserMfa mfa = pending("m1");
        when(mfaRepository.findByUserId("u1")).thenReturn(List.of(mfa));

        UserMfa out = service.verifyEnrollment(new VerifyEnrollmentInput("m1", currentCode())).mfa();

        assertThat(out.getEnabled()).isTrue();
        assertThat(out.getVerified()).isTrue();
        assertThat(out.getPrimary()).isTrue();
        assertThat(out.getLastVerifiedAt()).isNotNull();
    }

    @Test
    void verifyEnrollmentIsNotPrimaryWhenAnotherMethodIsEnabled() throws Exception {
        UserMfa mfa = pending("m1");
        UserMfa other = UserMfa.builder().id("m2").enabled(true).build();
        when(mfaRepository.findByUserId("u1")).thenReturn(List.of(mfa, other));

        assertThat(service.verifyEnrollment(new VerifyEnrollmentInput("m1", currentCode())).mfa().getPrimary()).isFalse();
    }

    @Test
    void verifyEnrollmentRejectsWrongCodeAndCountsFailure() {
        UserMfa mfa = pending("m1");
        mfa.setFailedAttempts(null);

        assertThatThrownBy(() -> service.verifyEnrollment(new VerifyEnrollmentInput("m1", "000000")))
                .hasMessageContaining("Invalid verification code");

        assertThat(mfa.getFailedAttempts()).isEqualTo(1);
        assertThat(mfa.getEnabled()).isFalse();
    }

    @Test
    void verifyEnrollmentRejectsWrongTypeAndAlreadyVerified() {
        UserMfa sms = pending("s");
        sms.setMfaType(MfaType.SMS_OTP);
        assertThatThrownBy(() -> service.verifyEnrollment(new VerifyEnrollmentInput("s", "1")))
                .hasMessageContaining("Unsupported MFA type");

        UserMfa done = pending("d");
        done.setVerified(true);
        assertThatThrownBy(() -> service.verifyEnrollment(new VerifyEnrollmentInput("d", "1")))
                .hasMessageContaining("already verified");
    }
}
