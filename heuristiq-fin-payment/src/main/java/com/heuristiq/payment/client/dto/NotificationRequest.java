package com.heuristiq.paymentservice.client.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Local mirror of Rider_NMS's {@code com.railcab.nms.application.dto.NotificationRequest} —
 * kept in this service (no Gradle dependency on the NMS module, per NMS's own
 * NMS_DTO_CONTRACTS.md / NMS_INTEGRATION.md convention) and trimmed to the fields this
 * service actually populates.
 *
 * <p>Recipient identification: this service only has {@code customerId} on hand for a
 * payment (no phone number/email), so notifications are addressed via {@code userIds} — NMS
 * falls back to an internal device-token lookup by user id for the FCM_PUSH channel, and
 * IN_APP delivery is keyed by user id directly. SMS/EMAIL channels would need
 * {@code toPhone}/{@code toEmail}, which is a known gap (see PaymentService javadoc at the
 * notify call sites) rather than something faked here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequest {

    /** Event key whose active NMS templates drive this dispatch, e.g. "PAYMENT_SUCCESSFUL". */
    private String event;

    /** Template variable substitutions, matching {{key}} placeholders in the NMS template body. */
    private Map<String, String> variables;

    /** Target user ids (IN_APP channel; also the FCM_PUSH device-token lookup fallback). */
    private List<String> userIds;
}
