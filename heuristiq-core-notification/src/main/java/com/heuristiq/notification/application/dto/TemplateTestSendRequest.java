package com.railcab.nms.application.dto;

import java.util.Map;

import com.railcab.nms.domain.model.NotificationChannel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One test message from the template editor: the content exactly as typed (not yet saved), the
 * sample values to fill in, and who should receive it.
 *
 * @param channel   EMAIL, SMS or WHATSAPP (the channels that can be tested to a typed recipient)
 * @param recipient email address or phone number the test goes to
 * @param subject   email subject; ignored by the other channels
 * @param body      template body as plain HTML or text (not Base64)
 * @param variables sample values for the {{placeholders}}
 */
public record TemplateTestSendRequest(
        @NotNull NotificationChannel channel,
        @NotBlank @Size(max = 254) String recipient,
        @Size(max = 300) String subject,
        @NotBlank @Size(max = 200_000) String body,
        Map<String, String> variables) {
}
