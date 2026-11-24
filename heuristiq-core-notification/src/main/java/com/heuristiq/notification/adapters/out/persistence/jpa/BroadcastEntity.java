package com.railcab.nms.adapters.out.persistence.jpa;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import com.railcab.nms.domain.model.BroadcastSegment;
import com.railcab.nms.domain.model.BroadcastStatus;
import com.railcab.nms.domain.model.NotificationChannel;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "nms_broadcasts", indexes = {
                @Index(name = "idx_nms_broadcast_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_nms_broadcast_scheduled_at", columnList = "scheduled_at"),
        @Index(name = "idx_nms_broadcast_status", columnList = "status")
})
public class BroadcastEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    /** Organization this record belongs to; null for a record stored before organizations existed. */
    @Column(name = "tenant_id")
    private String tenantId;

    private String title;

    @Column(length = 4000)
    private String message;

    private String city;

    private String event;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "nms_broadcast_variables", joinColumns = @JoinColumn(name = "broadcast_id"))
    @MapKeyColumn(name = "variable_key")
    @Column(name = "variable_value")
    @Builder.Default
    private Map<String, String> variables = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    private BroadcastSegment segment;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "nms_broadcast_channels", joinColumns = @JoinColumn(name = "broadcast_id"))
    @Column(name = "channel")
    @Enumerated(EnumType.STRING)
    @OrderColumn(name = "idx")
    @Builder.Default
    private List<NotificationChannel> channels = new ArrayList<>();

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    private BroadcastStatus status;

    private Integer recipientCount;

    private Integer targetedCount;

    private Integer failedCount;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime sentAt;
}
