package com.railcab.nms.adapters.out.persistence.jpa;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "nms_event_definitions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_nms_event_definition_value", columnNames = "event_value")
})
public class NotificationEventDefinitionEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    // "value" is a reserved word in H2/MySQL, so it can't be used as a bare column name.
    @Column(name = "event_value")
    private String value;

    private String label;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "nms_event_definition_attributes", joinColumns = @JoinColumn(name = "event_definition_id"))
    @Column(name = "attribute")
    @OrderColumn(name = "idx")
    private List<String> attributes = new ArrayList<>();

    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
