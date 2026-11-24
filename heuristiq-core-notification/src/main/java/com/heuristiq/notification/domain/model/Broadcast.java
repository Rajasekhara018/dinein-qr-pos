package com.heuristiq.notification.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.heuristiq.notification.domain.model.BroadcastSegment;
import com.heuristiq.notification.domain.model.BroadcastStatus;
import com.heuristiq.notification.domain.model.NotificationChannel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An admin-composed broadcast job -- the scheduling/audience-targeting layer NMS's own
 * {@code /api/v1/nms/notify} lacks (see NotificationRequest javadoc). {@code event}/{@code variables}
 * are handed to NotificationDispatchService.dispatch() as-is per resolved recipient, so a
 * NotificationTemplate must already exist for {@code event} × each channel in {@code channels}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Broadcast {

    private String id;

    /** Organization this record belongs to; null for a record stored before organizations existed. */
    private String tenantId;

    /** Admin-facing label; also passed to the templates as the {{title}} variable. */
    private String title;

    /** Free-form body; passed to the templates as the {{message}} variable (ADMIN_BROADCAST renders it verbatim). */
    private String message;

    /** Informational only -- UAM carries no city, so it is recorded but not used for audience targeting. */
    private String city;

    private String event;

    private Map<String, String> variables;

    private BroadcastSegment segment;

    private List<NotificationChannel> channels;

    /** Null/past = eligible for the next dispatch sweep; future = held until then. */
    private LocalDateTime scheduledAt;

    private BroadcastStatus status;

    /** Recipients that received the broadcast on at least one channel. */
    private Integer recipientCount;

    /** Recipients resolved for the segment (set when dispatch starts). */
    private Integer targetedCount;

    /** Resolved recipients for which every channel failed. */
    private Integer failedCount;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime sentAt;
}
