package com.heuristiq.uam.helper;

import java.time.LocalTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {

    private Boolean emailEnabled;

    private Boolean smsEnabled;

    private Boolean pushEnabled;

    private Boolean whatsappEnabled;

    private Boolean inAppEnabled;

    private Boolean doNotDisturb;

        /**
     * DND Start Time.
     */
    private LocalTime dndStartTime;

    /**
     * DND End Time.
     */
    private LocalTime dndEndTime;

    private List<NotificationCategoryPreference> categories;

}
