package com.railcab.nms.application.port.out;

import java.util.Optional;

import com.railcab.nms.application.dto.OrganizationProfile;

/**
 * Reads an organization's profile from where it is owned (UAM), without any caching or degrading. Unlike
 * {@link OrganizationProfileResolver}, it tells "the organization does not exist" (empty) apart from "the lookup failed"
 * (an exception), which is what lets a cache keep the first briefly and never keep the second.
 */
public interface OrganizationProfileSource {

    /**
     * @return the profile, or empty when the organization is known not to exist
     * @throws RuntimeException when the owner cannot be reached or answers with an error (never an empty result for that)
     */
    Optional<OrganizationProfile> fetch(String tenantId);
}
