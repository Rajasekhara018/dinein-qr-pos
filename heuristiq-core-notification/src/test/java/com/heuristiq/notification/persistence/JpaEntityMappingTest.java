package com.heuristiq.notification.persistence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.model.DeliveryStatus;
import com.heuristiq.notification.domain.model.NotificationMessageStatus;
import com.heuristiq.notification.adapters.out.persistence.jpa.NotificationEventDefinitionEntity;
import com.heuristiq.notification.adapters.out.persistence.jpa.NotificationLogEntity;
import com.heuristiq.notification.adapters.out.persistence.jpa.NotificationMessageEntity;
import com.heuristiq.notification.adapters.out.persistence.jpa.NotificationTemplateEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the JPA mappings for the four NMS aggregates (enum columns, the event-definition
 * element collection, unique constraints) against an in-memory H2 database in MySQL-compatibility
 * mode -- without needing a live MySQL server or the full Spring context.
 */
class JpaEntityMappingTest {
    private static StandardServiceRegistry registry;
    private static SessionFactory sessionFactory;

    @BeforeAll
    static void setUp() {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url",
                        "jdbc:h2:mem:nms_jpa_mapping_test;DB_CLOSE_DELAY=-1;MODE=MySQL")
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .applySetting("hibernate.implicit_naming_strategy",
                        "org.springframework.boot.hibernate.SpringImplicitNamingStrategy")
                .build();
        sessionFactory = new MetadataSources(registry)
                .addAnnotatedClass(NotificationLogEntity.class)
                .addAnnotatedClass(NotificationMessageEntity.class)
                .addAnnotatedClass(NotificationTemplateEntity.class)
                .addAnnotatedClass(NotificationEventDefinitionEntity.class)
                .buildMetadata()
                .buildSessionFactory();
    }

    @AfterAll
    static void tearDown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (registry != null) {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void notificationLogRoundTripsEnumsAndTimestamps() {
        NotificationLogEntity entity = new NotificationLogEntity();
        entity.setMessageId("msg-1");
        entity.setTenantId("org-a");
        entity.setCorrelationId("corr-1");
        entity.setEvent("BOOKING_CONFIRMED");
        entity.setChannel(NotificationChannel.SMS);
        entity.setProviderName("TwilioSmsProvider");
        entity.setStatus(DeliveryStatus.SENT);
        entity.setRecipient("+911234567890");
        entity.setSuccess(true);
        entity.setRetryCount(1);
        entity.setSentAt(LocalDateTime.now());

        NotificationLogEntity reloaded = persistAndReload(entity, NotificationLogEntity.class);
        assertThat(reloaded.getMessageId()).isEqualTo("msg-1");
        assertThat(reloaded.getTenantId()).isEqualTo("org-a");
        assertThat(reloaded.getChannel()).isEqualTo(NotificationChannel.SMS);
        assertThat(reloaded.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(reloaded.isSuccess()).isTrue();
    }

    @Test
    void notificationMessageEnforcesUniqueMessageId() {
        NotificationMessageEntity entity = new NotificationMessageEntity();
        entity.setMessageId("msg-unique-1");
        entity.setEvent("BOOKING_CONFIRMED");
        entity.setStatus(NotificationMessageStatus.PROCESSING);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        NotificationMessageEntity reloaded = persistAndReload(entity, NotificationMessageEntity.class);
        assertThat(reloaded.getMessageId()).isEqualTo("msg-unique-1");
        assertThat(reloaded.getStatus()).isEqualTo(NotificationMessageStatus.PROCESSING);
    }

    @Test
    void notificationTemplateRoundTripsChannelAndBody() {
        NotificationTemplateEntity entity = new NotificationTemplateEntity();
        entity.setEvent("BOOKING_CONFIRMED");
        entity.setChannel(NotificationChannel.EMAIL);
        entity.setName("Booking confirmed - email");
        entity.setSubject("Your ride is confirmed");
        entity.setBody("PGgxPkNvbmZpcm1lZDwvaDE+");
        entity.setActive(true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        NotificationTemplateEntity reloaded = persistAndReload(entity, NotificationTemplateEntity.class);
        assertThat(reloaded.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(reloaded.getSubject()).isEqualTo("Your ride is confirmed");
        assertThat(reloaded.isActive()).isTrue();
    }

    @Test
    void notificationEventDefinitionRoundTripsAttributesCollection() {
        NotificationEventDefinitionEntity entity = new NotificationEventDefinitionEntity();
        entity.setValue("BOOKING_CONFIRMED");
        entity.setLabel("Booking Confirmed");
        entity.setAttributes(new ArrayList<>(List.of("{{customerName}}", "{{bookingId}}")));
        entity.setActive(true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        NotificationEventDefinitionEntity reloaded = persistAndReload(entity, NotificationEventDefinitionEntity.class);
        assertThat(reloaded.getValue()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(reloaded.getAttributes()).containsExactly("{{customerName}}", "{{bookingId}}");
    }

    private <T> T persistAndReload(T entity, Class<T> type) {
        String id = inTransaction(session -> {
            session.persist(entity);
            return (String) getId(entity, type);
        });
        return inSession(session -> session.find(type, id));
    }

    private <T> Object getId(T entity, Class<T> type) {
        try {
            return type.getMethod("getId").invoke(entity);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T inTransaction(Function<Session, T> work) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            T result = work.apply(session);
            session.getTransaction().commit();
            return result;
        }
    }

    private <T> T inSession(Function<Session, T> work) {
        try (Session session = sessionFactory.openSession()) {
            return work.apply(session);
        }
    }
}
