package com.railcab.nms.application.dto;

/** What a message says about the organization sending it. Any field may be null when the organization has not set it. */
public record OrganizationProfile(String name, String code, String supportEmail, String supportPhone) {
}
