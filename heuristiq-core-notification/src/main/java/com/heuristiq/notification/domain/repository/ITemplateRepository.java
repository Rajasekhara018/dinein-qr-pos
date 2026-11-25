package com.heuristiq.notification.domain.repository;

import java.util.List;
import java.util.Optional;

import com.heuristiq.notification.domain.model.NotificationTemplate;
import com.heuristiq.notification.domain.model.NotificationChannel;

public interface ITemplateRepository {

    NotificationTemplate save(NotificationTemplate template);

    List<NotificationTemplate> saveAll(List<NotificationTemplate> templates);

    Optional<NotificationTemplate> findById(String id);

    List<NotificationTemplate> findByEventAndActiveTrue(String event);

    /** The shared (no organization) active template for an event and channel. */
    Optional<NotificationTemplate> findByEventAndChannelAndActiveTrue(String event, NotificationChannel channel);

    /** The active template an organization owns for an event and channel. */
    Optional<NotificationTemplate> findOrganizationTemplate(String event, NotificationChannel channel, String tenantId);

    List<NotificationTemplate> findByActiveTrue();

    PageResult<NotificationTemplate> findByActiveTrue(PageQuery pageQuery);

    /** True when a shared (no organization) template exists for the event and channel, active or not. */
    boolean existsByEventAndChannel(String event, NotificationChannel channel);

    /** True when the organization already owns a template for the event and channel, active or not. */
    boolean existsOrganizationTemplate(String event, NotificationChannel channel, String tenantId);

    /** The shared (no organization) active templates: what a new organization's own copies are made from. */
    List<NotificationTemplate> findSharedActive();

    /** Every template the organization owns, active or not (a switched-off one is not copied again). */
    List<NotificationTemplate> findOrganizationTemplates(String tenantId);

    /** A page of the organization's own active templates, nothing shared and nothing of another organization. */
    PageResult<NotificationTemplate> findOrganizationActive(String tenantId, PageQuery pageQuery);
}
