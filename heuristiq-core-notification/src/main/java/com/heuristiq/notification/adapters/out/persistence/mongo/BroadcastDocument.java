package com.railcab.nms.adapters.out.persistence.mongo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.railcab.nms.domain.model.BroadcastSegment;
import com.railcab.nms.domain.model.BroadcastStatus;
import com.railcab.nms.domain.model.NotificationChannel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "nms_broadcasts")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastDocument {

    @Id
    private String id;

    /** Organization this record belongs to; null for a record stored before organizations existed. */
    @Indexed
    private String tenantId;

    private String title;

    private String message;

    private String city;

    private String event;

    private Map<String, String> variables;

    @Indexed
    private BroadcastSegment segment;

    private List<NotificationChannel> channels;

    @Indexed
    private LocalDateTime scheduledAt;

    @Indexed
    private BroadcastStatus status;

    private Integer recipientCount;

    private Integer targetedCount;

    private Integer failedCount;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime sentAt;
}
