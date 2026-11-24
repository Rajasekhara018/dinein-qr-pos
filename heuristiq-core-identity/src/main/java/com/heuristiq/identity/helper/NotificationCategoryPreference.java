package com.heuristiq.uam.helper;

import java.util.List;

import com.heuristiq.uam.enums.UamEnums.NotificationCategory;
import com.heuristiq.uam.enums.UamEnums.NotificationChannel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCategoryPreference {

    /**
     * SECURITY
     * MARKETING
     * BILLING
     * SYSTEM
     * PROMOTION
     */
    private NotificationCategory category;

    @Builder.Default
    private Boolean enabled = true;

    /**
     * Preferred Channel Order.
     */
    private List<NotificationChannel> preferredChannels;
}
