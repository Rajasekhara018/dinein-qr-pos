package com.heuristiq.uam.selfregister;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.RequestValidationException;

import jakarta.validation.Validation;

class SelfRegisterControllersTest {
    private SelfRegisterUseCase useCase;
    private SelfRegisterController controller;
    private InternalSelfRegisterController internal;

    @BeforeEach
    void setUp() {
        useCase = mock(SelfRegisterUseCase.class);
        CommonRequestMapper mapper = new CommonRequestMapper(new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator());
        controller = new SelfRegisterController(useCase, mapper);
        internal = new InternalSelfRegisterController(useCase, mapper);
    }

    private static CommonRequestObject body(Map<String, Object> fields) {
        CommonRequestObject request = new CommonRequestObject();
        request.setRequestObject(new HashMap<>(fields));
        return request;
    }

    @Test
    void sendOtpPassesTheClientAddressAndAnswersTheSameGenericSentence() {
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.addHeader("X-Forwarded-For", "203.0.113.9");
        when(useCase.sendOtp("jane@example.com", "APP_X", "tok", "203.0.113.9")).thenReturn(new SelfRegisterUseCase.SendResult(true, 30));

        ResponseEntity<CommonResponseObject> out = controller.sendOtp(http,
                body(Map.of("email", "jane@example.com", "applicationCode", "APP_X", "botToken", "tok")));

        assertThat(out.getBody().getResponseObject()).isEqualTo(new SelfRegisterUseCase.SendResult(true, 30));
        assertThat(out.getBody().getMessage()).isEqualTo("If this address can be used, a code is on its way.");
    }

    @Test
    void verifyOtpReturnsTheTokenAndTheLifetime() {
        when(useCase.verifyOtp("jane@example.com", "123456", "APP_X")).thenReturn(new SelfRegisterUseCase.VerifyResult("tok", 900));

        ResponseEntity<CommonResponseObject> out = controller.verifyOtp(
                body(Map.of("email", "jane@example.com", "otp", "123456", "applicationCode", "APP_X")));

        assertThat(out.getBody().getResponseObject()).isEqualTo(new SelfRegisterUseCase.VerifyResult("tok", 900));
    }

    @Test
    void theInternalCallAsksForTheSetPasswordCodeAndAlwaysAnswersOk() {
        ResponseEntity<CommonResponseObject> out = internal.sendPasswordCode(body(Map.of("email", "jane@example.com")));

        verify(useCase).sendSetPasswordCode("jane@example.com");
        assertThat(out.getStatusCode().is2xxSuccessful()).isTrue();
        assertThatThrownBy(() -> internal.sendPasswordCode(body(Map.of("email", "bad")))).isInstanceOf(RequestValidationException.class);
    }

    @Test
    void badInputIsRejectedBeforeTheUseCaseIsTouched() {
        assertThatThrownBy(() -> controller.sendOtp(new MockHttpServletRequest(), body(Map.of("email", "bad", "applicationCode", "APP_X"))))
                .isInstanceOf(RequestValidationException.class);
        assertThatThrownBy(() -> controller.verifyOtp(body(Map.of("email", "jane@example.com", "otp", "", "applicationCode", "APP_X"))))
                .isInstanceOf(RequestValidationException.class);
        assertThatThrownBy(() -> controller.sendOtp(new MockHttpServletRequest(), new CommonRequestObject()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theInternalCheckSpendsTheTokenAndSaysWhetherItWasGood() {
        when(useCase.consumeToken("jane@example.com", "tok", "APP_X")).thenReturn(true);
        when(useCase.consumeToken("jane@example.com", "other", "APP_X")).thenReturn(false);

        ResponseEntity<CommonResponseObject> good = internal.verifyToken(
                body(Map.of("email", "jane@example.com", "token", "tok", "applicationCode", "APP_X")));
        ResponseEntity<CommonResponseObject> bad = internal.verifyToken(
                body(Map.of("email", "jane@example.com", "token", "other", "applicationCode", "APP_X")));

        assertThat(good.getBody().getResponseObject()).isEqualTo(new SelfRegisterRequests.Consumed(true));
        assertThat(bad.getBody().getResponseObject()).isEqualTo(new SelfRegisterRequests.Consumed(false));
        verify(useCase).consumeToken(eq("jane@example.com"), eq("tok"), eq("APP_X"));
        assertThatThrownBy(() -> internal.verifyToken(body(Map.of("email", "jane@example.com", "applicationCode", "APP_X"))))
                .isInstanceOf(RequestValidationException.class);
        verify(useCase, org.mockito.Mockito.never()).sendOtp(any(), any(), any(), any());
    }
}
