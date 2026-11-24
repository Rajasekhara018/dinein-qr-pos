package com.heuristiq.notification.application.port.in;

import java.util.List;
import java.util.Optional;

import com.heuristiq.notification.domain.model.NotificationTemplate;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.domain.repository.PageResult;

/** Port the web adapter depends on instead of the concrete template service. */
public interface TemplateUseCase {

    NotificationTemplate create(NotificationTemplate template);

    Optional<NotificationTemplate> findById(String id);

    List<NotificationTemplate> findByEvent(String event);

    Optional<NotificationTemplate> findByEventAndChannel(String event, NotificationChannel channel);

    /**
     * The template to render for an organization: its own template for the event and channel when it has one, else the
     * shared one. A blank organization gets the shared template.
     */
    Optional<NotificationTemplate> findForTenant(String event, NotificationChannel channel, String tenantId);

    List<NotificationTemplate> findAll();

    PageResult<NotificationTemplate> findAll(PageQuery pageQuery);

    NotificationTemplate update(String id, NotificationTemplate updated);

    void delete(String id);
}
