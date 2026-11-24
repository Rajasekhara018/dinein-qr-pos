package com.heuristiq.notification.adapters.in.web.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.heuristiq.notification.application.usecase.NotificationAttemptQueryService;
import com.heuristiq.notification.domain.model.DeliveryStatus;
import com.heuristiq.notification.domain.model.NotificationAttempt;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.testsupport.InMemoryAttemptRepository;

class NotificationAttemptControllerTest {

    private MockMvc mvc;
    private InMemoryAttemptRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAttemptRepository();
        mvc = MockMvcBuilders.standaloneSetup(new NotificationAttemptController(new NotificationAttemptQueryService(repository)))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
        LocalDateTime t = LocalDateTime.of(2026, 10, 6, 10, 0);
        save("m-1", "c-1", 1, DeliveryStatus.FAILED, t);
        save("m-1", "c-1", 2, DeliveryStatus.SENT, t.plusSeconds(1));
        save("m-2", "c-1", 1, DeliveryStatus.SENT, t.plusSeconds(2));
        save("m-3", "c-9", 1, DeliveryStatus.SENT, t.plusSeconds(3));
    }

    private void save(String messageId, String correlationId, int number, DeliveryStatus status, LocalDateTime at) {
        repository.save(NotificationAttempt.builder().logId("log-" + messageId).messageId(messageId)
                .correlationId(correlationId).channel(NotificationChannel.SMS).attemptNumber(number).status(status)
                .recipient("******3210").sourceService("rider-uam").errorMessage("password=hunter2 ignored")
                .startedAt(at).build());
    }

    @Test
    void attemptsOfOneMessageComeBackOldestFirst() throws Exception {
        mvc.perform(get("/api/v1/nms/logs/attempts/message/m-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responseObject.totalElements").value(2))
                .andExpect(jsonPath("$.responseObject.content[0].attemptNumber").value(1))
                .andExpect(jsonPath("$.responseObject.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.responseObject.content[1].attemptNumber").value(2))
                .andExpect(jsonPath("$.responseObject.content[1].recipient").value("******3210"))
                .andExpect(jsonPath("$.responseObject.content[1].sourceService").value("rider-uam"));
    }

    @Test
    void attemptsOfACorrelationIdSpanMessages() throws Exception {
        mvc.perform(get("/api/v1/nms/logs/attempts/correlation/c-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.totalElements").value(3));
    }

    @Test
    void anUnknownIdGivesAnEmptyPageNotAnError() throws Exception {
        mvc.perform(get("/api/v1/nms/logs/attempts/message/nope"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.totalElements").value(0));
    }

    @Test
    void thePageSizeIsCapped() throws Exception {
        mvc.perform(get("/api/v1/nms/logs/attempts/message/m-1").param("size", "100000"))
                .andExpect(status().isOk());

        // the service clamps what reaches the repository
        assertThat(new NotificationAttemptQueryService(repository)
                .findByMessageId("m-1", new com.heuristiq.notification.domain.repository.PageQuery(0, 100000)).size())
                .isEqualTo(200);
    }
}
