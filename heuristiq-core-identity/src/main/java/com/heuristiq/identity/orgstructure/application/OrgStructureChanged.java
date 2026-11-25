package com.heuristiq.identity.orgstructure.application;

/** Published after an organization's structure was published or rolled back: who is in which department may have changed. */
public record OrgStructureChanged(String tenantId) {
}
