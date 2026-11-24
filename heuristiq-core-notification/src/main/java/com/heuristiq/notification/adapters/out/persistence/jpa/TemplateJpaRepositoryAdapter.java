package com.railcab.nms.adapters.out.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Repository;

import com.railcab.nms.adapters.out.persistence.PaginationMapper;
import com.railcab.nms.domain.model.NotificationTemplate;
import com.railcab.nms.domain.repository.ITemplateRepository;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.adapters.out.persistence.jpa.NotificationTemplateEntity;

@Repository
@ConditionalOnExpression("'${nms.persistence.type:mongo}' == 'mysql' or '${nms.persistence.type:mongo}' == 'postgres'")
class TemplateJpaRepositoryAdapter implements ITemplateRepository {

    private final TemplateJpaSpringRepository springRepository;

    TemplateJpaRepositoryAdapter(TemplateJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public NotificationTemplate save(NotificationTemplate template) {
        return toDomain(springRepository.save(toEntity(template)));
    }

    @Override
    public List<NotificationTemplate> saveAll(List<NotificationTemplate> templates) {
        return springRepository.saveAll(templates.stream().map(this::toEntity).toList())
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
                : springRepository.findActiveScoped(pageQuery.scope().tenantIdOrEmpty(), PaginationMapper.toPageable(pageQuery));
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

    private NotificationTemplate toDomain(NotificationTemplateEntity entity) {
        NotificationTemplate template = new NotificationTemplate();
        template.setId(entity.getId());
        template.setEvent(entity.getEvent());
        template.setTenantId(entity.getTenantId());
        template.setChannel(entity.getChannel());
        template.setName(entity.getName());
        template.setSubject(entity.getSubject());
        template.setBody(entity.getBody());
        template.setExternalTemplateId(entity.getExternalTemplateId());
        template.setActive(entity.isActive());
        template.setCreatedAt(entity.getCreatedAt());
        template.setUpdatedAt(entity.getUpdatedAt());
        return template;
    }

    private NotificationTemplateEntity toEntity(NotificationTemplate template) {
        NotificationTemplateEntity entity = new NotificationTemplateEntity();
        entity.setId(template.getId());
        entity.setEvent(template.getEvent());
        entity.setTenantId(template.getTenantId());
        entity.setChannel(template.getChannel());
        entity.setName(template.getName());
        entity.setSubject(template.getSubject());
        entity.setBody(template.getBody());
        entity.setExternalTemplateId(template.getExternalTemplateId());
        entity.setActive(template.isActive());
        entity.setCreatedAt(template.getCreatedAt());
        entity.setUpdatedAt(template.getUpdatedAt());
        return entity;
    }
}
