package com.heuristiq.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Local mirror of Rider_NMS's {@code com.railcab.nms.shared.CommonRequestObject} envelope,
 * trimmed to the two fields {@code POST /api/v1/nms/notify} actually reads: {@code messageID}
 * (idempotency/correlation) and {@code requestObject} (the {@link NotificationRequest} payload).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class CommonNmsRequest {
    private String messageID;
    private Object requestObject;
}
