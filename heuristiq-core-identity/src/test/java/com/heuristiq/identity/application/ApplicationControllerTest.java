package com.heuristiq.identity.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.FieldValidationError;
import com.heuristiq.identity.common.GlobalExceptionHandler;
import com.heuristiq.identity.common.RequestValidationException;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.enums.UamEnums.ApplicationType;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The HTTP shape of the application endpoints: catalogue, create (with its errors), delete and the self-service lookup. */
class ApplicationControllerTest {
    private static final String CREATE_BODY = "{\"requestObject\":{\"code\":\"BEST_PURCHASE_APP\"}}";

    private ApplicationServiceUseCase service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ApplicationServiceUseCase.class);
        CommonRequestMapper mapper = new CommonRequestMapper(new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator());
        mvc = MockMvcBuilders.standaloneSetup(new ApplicationController(service, mapper), new MyApplicationsController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void theCatalogueIsReturnedWithTheCreatedFlag() throws Exception {
        when(service.catalogue()).thenReturn(new ApplicationServiceUseCase.CatalogueOutput(List.of(
                new ApplicationCatalogueItem("BEST_PURCHASE_APP", "Best Purchase", "d", ApplicationType.WEB, 40, "shopping_cart", true))));

        mvc.perform(get("/api/v1/uam/applications/catalogue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responseObject[0].code").value("BEST_PURCHASE_APP"))
                .andExpect(jsonPath("$.responseObject[0].type").value("WEB"))
                .andExpect(jsonPath("$.responseObject[0].iconHint").value("shopping_cart"))
                .andExpect(jsonPath("$.responseObject[0].created").value(true));
    }

    @Test
    void createPassesTheSignedInActorAndAnswers201() throws Exception {
        when(service.create(any())).thenReturn(new ApplicationServiceUseCase.CreateOutput(
                Application.builder().id("a1").code("BEST_PURCHASE_APP").build()));

        mvc.perform(post("/api/v1/uam/applications").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY)
                        .principal(new UsernamePasswordAuthenticationToken("admin-a", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responseObject.code").value("BEST_PURCHASE_APP"));

        ArgumentCaptor<ApplicationServiceUseCase.CreateInput> input = ArgumentCaptor.forClass(ApplicationServiceUseCase.CreateInput.class);
        verify(service).create(input.capture());
        assertThat(input.getValue().actor()).isEqualTo("admin-a");
        assertThat(input.getValue().request().code()).isEqualTo("BEST_PURCHASE_APP");
    }

    @Test
    void aCodeOutsideTheCatalogueIs400WithAFieldError() throws Exception {
        when(service.create(any())).thenThrow(new RequestValidationException(List.of(
                new FieldValidationError("code", ApplicationConstants.CODE_NOT_IN_CATALOGUE + "BEST_PURCHASE_APP"))));

        mvc.perform(post("/api/v1/uam/applications").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.responseObject[0].field").value("code"));
    }

    @Test
    void aDuplicateIs409() throws Exception {
        when(service.create(any())).thenThrow(UamException.conflict(ApplicationConstants.ALREADY_EXISTS + "BEST_PURCHASE_APP"));

        mvc.perform(post("/api/v1/uam/applications").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Application already exists in this organization: BEST_PURCHASE_APP"));
    }

    @Test
    void deleteAnswers200WithTheRemovedMessageAndPassesTheActor() throws Exception {
        mvc.perform(delete("/api/v1/uam/applications/a1").principal(new UsernamePasswordAuthenticationToken("admin-a", null)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value("Application removed"));

        verify(service).delete(new ApplicationServiceUseCase.DeleteInput("a1", "admin-a"));
    }

    @Test
    void theLookupIsSmallSortedAndBrieflyCacheable() throws Exception {
        when(service.lookup()).thenReturn(new ApplicationServiceUseCase.LookupOutput(List.of(
                new ApplicationLookupItem("CENTRALISED_WALLET_APP", "Centralised Wallet", ApplicationType.WEB, 50, null, "account_balance_wallet"))));

        mvc.perform(get("/api/v1/uam/identity/applications"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=60, private"))
                .andExpect(jsonPath("$.responseObject[0].code").value("CENTRALISED_WALLET_APP"))
                .andExpect(jsonPath("$.responseObject[0].displayOrder").value(50))
                .andExpect(jsonPath("$.responseObject[0].description").doesNotExist());
    }
}
