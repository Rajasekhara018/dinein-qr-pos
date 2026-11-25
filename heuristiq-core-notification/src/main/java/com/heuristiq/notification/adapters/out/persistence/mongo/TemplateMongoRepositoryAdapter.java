package com.heuristiq.notification.adapters.out.persistence.mongo;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.heuristiq.notification.adapters.out.persistence.PaginationMapper;
import com.heuristiq.notification.domain.model.NotificationTemplate;
import com.heuristiq.notification.domain.repository.ITemplateRepository;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.domain.repository.PageResult;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.adapters.out.persistence.mongo.NotificationTemplateDocument;

@Repository
@ConditionalOnProperty(prefix = "nms.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class TemplateMongoRepositoryAdapter implements ITemplateRepository {

    private final TemplateMongoSpringRepository springRepository;

    TemplateMongoRepositoryAdapter(TemplateMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public NotificationTemplate save(NotificationTemplate template) {
        return toDomain(springRepository.save(toDocument(template)));
    }

    @Override
    public List<NotificationTemplate> saveAll(List<NotificationTemplate> templates) {
        return springRepository.saveAll(templates.stream().map(this::toDocument).toList())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<NotificationTemplate> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<NotificationTemplate> findByEventAndActiveTrue(String event) {
        return springRepository.findByEventAndActiveTrue(event).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<NotificationTemplate> findByEventAndChannelAndActiveTrue(String event, NotificationChannel channel) {
        return springRepository.findByEventAndChannelAndTenantIdIsNullAndActiveTrue(event, channel).map(this::toDomain);
    }

    @Override
    public Optional<NotificationTemplate> findOrganizationTemplate(String event, NotificationChannel channel, String tenantId) {
        return springRepository.findByEventAndChannelAndTenantIdAndActiveTrue(event, channel, tenantId).map(this::toDomain);
    }

    @Override
    public List<NotificationTemplate> findByActiveTrue() {
        return springRepository.findByActiveTrue().stream().map(this::toDomain).toList();
    }

    @Override
    public PageResult<NotificationTemplate> findByActiveTrue(PageQuery pageQuery) {
        var page = pageQuery.scope().unrestricted()
                ? springRepository.findByActiveTrue(PaginationMapper.toPageable(pageQuery))
                : springRepository.findByActiveTrueAndTenantIdIn(pageQuery.scope().visibleTenantIds(), PaginationMapper.toPageable(pageQuery));
        return PaginationMapper.toPageResult(page.map(this::toDomain));
    }

    @Override
    public boolean existsByEventAndChannel(String event, NotificationChannel channel) {
        return springRepository.existsByEventAndChannelAndTenantIdIsNull(event, channel);
    }

    @Override
    public boolean existsOrganizationTemplate(String event, NotificationChannel channel, String tenantId) {
        return springRepository.existsByEventAndChannelAndTenantId(event, channel, tenantId);
    }

    @Override
    public List<NotificationTemplate> findSharedActive() {
        return springRepository.findByTenantIdIsNullAndActiveTrue().stream().map(this::toDomain).toList();
    }

    @Override
    public List<NotificationTemplate> findOrganizationTemplates(String tenantId) {
        return springRepository.findByTenantId(tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public PageResult<NotificationTemplate> findOrganizationActive(String tenantId, PageQuery pageQuery) {
        return PaginationMapper.toPageResult(springRepository.findByTenantIdAndActiveTrue(tenantId, PaginationMapper.toPageable(pageQuery))
                .map(this::toDomain));
    }

    private NotificationTemplate toDomain(NotificationTemplateDocument doc) {
        NotificationTemplate template = new NotificationTemplate();
        template.setId(doc.getId());
        template.setEvent(doc.getEvent());
        template.setTenantId(doc.getTenantId());
        template.setChannel(doc.getChannel());
        template.setName(doc.getName());
        template.setSubject(doc.getSubject());
        template.setBody(doc.getBody());
        template.setExternalTemplateId(doc.getExternalTemplateId());
        template.setActive(doc.isActive());
        template.setCreatedAt(doc.getCreatedAt());
        template.setUpdatedAt(doc.getUpdatedAt());
        return template;
    }

    private NotificationTemplateDocument toDocument(NotificationTemplate template) {
        NotificationTemplateDocument doc = new NotificationTemplateDocument();
        doc.setId(template.getId());
        doc.setEvent(template.getEvent());
        doc.setTenantId(template.getTenantId());
        doc.setChannel(template.getChannel());
        doc.setName(template.getName());
        doc.setSubject(template.getSubject());
        doc.setBody(template.getBody());
        doc.setExternalTemplateId(template.getExternalTemplateId());
        doc.setActive(template.isActive());
        doc.setCreatedAt(template.getCreatedAt());
        doc.setUpdatedAt(template.getUpdatedAt());
        return doc;
    }
}
