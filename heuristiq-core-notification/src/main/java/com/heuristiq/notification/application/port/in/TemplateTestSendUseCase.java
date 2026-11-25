package com.heuristiq.notification.application.port.in;

import com.heuristiq.notification.application.dto.TemplateTestSendRequest;
import com.heuristiq.notification.application.dto.TemplateTestSendResponse;

/** Sends one clearly-marked test message so an editor can see a template in a real inbox before users get it. */
public interface TemplateTestSendUseCase {

    /**
     * @param callerId the signed-in user, used for the per-user rate limit
     * @param tenantId the caller's organization, used for the organization's own variables; may be null
     * @throws IllegalArgumentException when the channel cannot be tested or the recipient is not valid for it
     * @throws com.heuristiq.notification.shared.NmsTooManyRequestsException when the caller sent too many tests in the last hour
     */
    TemplateTestSendResponse send(String callerId, String tenantId, TemplateTestSendRequest request);
}
