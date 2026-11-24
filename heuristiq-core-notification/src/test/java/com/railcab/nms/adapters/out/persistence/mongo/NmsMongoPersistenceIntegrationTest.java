package com.railcab.nms.adapters.out.persistence.mongo;

import com.railcab.nms.domain.model.NotificationMessage;
import com.railcab.nms.domain.model.NotificationMessageStatus;
import com.railcab.nms.domain.repository.INotificationMessageRepository;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service Component Test (Testcontainers pattern): the unique-messageId constraint backing
 * {@code NotificationMessageService.reserve()}'s idempotent-receiver behavior is only a real
 * guarantee if a real MongoDB enforces it -- every prior test in this codebase either mocked the
 * repository or never exercised this path against a live database. Also exercises the domain-owned
 * {@link PageQuery}/{@link PageResult} pagination contract (see the Clean Architecture fix that
 * removed Spring Data's Page/Pageable from the domain layer) against real query results.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class NmsMongoPersistenceIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO.getReplicaSetUrl("railcab_nms_test"));
        registry.add("spring.mongodb.database", () -> "railcab_nms_test");
    }

    @Autowired
    private INotificationMessageRepository messageRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    @AfterEach
    void cleanUp() {
        mongoTemplate.getDb().drop();
    }

    @Test
    void uniqueMessageIdConstraintRejectsADuplicateInsert() {
        String messageId = "msg-" + UUID.randomUUID();
        messageRepository.insert(newMessage(messageId));

        assertThatThrownBy(() -> messageRepository.insert(newMessage(messageId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByMessageIdRetrievesTheOriginalMessage() {
        String messageId = "msg-" + UUID.randomUUID();
        NotificationMessage inserted = messageRepository.insert(newMessage(messageId));

        var found = messageRepository.findByMessageId(messageId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(inserted.getId());
        assertThat(found.get().getStatus()).isEqualTo(NotificationMessageStatus.PROCESSING);
    }

    @Test
    void findAllPaginatesUsingTheDomainOwnedPageContract() {
        for (int i = 0; i < 3; i++) {
            messageRepository.insert(newMessage("msg-" + UUID.randomUUID()));
        }

        PageResult<NotificationMessage> page = messageRepository.findAll(new PageQuery(0, 2));

        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    private NotificationMessage newMessage(String messageId) {
        NotificationMessage message = new NotificationMessage();
        message.setMessageId(messageId);
        message.setCorrelationId(UUID.randomUUID().toString());
        message.setEvent("BOOKING_CONFIRMED");
        message.setStatus(NotificationMessageStatus.PROCESSING);
        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        return message;
    }
}
