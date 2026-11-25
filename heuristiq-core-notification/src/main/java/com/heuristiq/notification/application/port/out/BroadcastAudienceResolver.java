package com.heuristiq.notification.application.port.out;

import java.util.List;

import com.heuristiq.notification.application.dto.UamUserSummary;
import com.heuristiq.notification.domain.model.BroadcastSegment;

/** Port BroadcastService depends on instead of the concrete UAM integration client. */
public interface BroadcastAudienceResolver {

    /** All ACTIVE users matching the segment. Never returns null. Throws {@link AudienceResolutionException} if the audience cannot be resolved completely (never a partial list). */
    List<UamUserSummary> resolveSegment(BroadcastSegment segment);
}
