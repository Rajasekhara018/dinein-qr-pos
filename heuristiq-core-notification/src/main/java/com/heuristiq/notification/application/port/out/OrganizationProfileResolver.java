package com.railcab.nms.application.port.out;

import java.util.Optional;

import com.railcab.nms.application.dto.OrganizationProfile;

/** Looks up the name and support details of an organization, for the variables every message gets. */
public interface OrganizationProfileResolver {

    /** The organization's profile, or empty when it is unknown or cannot be reached (messages then use the platform's name). */
    Optional<OrganizationProfile> resolve(String tenantId);
}
