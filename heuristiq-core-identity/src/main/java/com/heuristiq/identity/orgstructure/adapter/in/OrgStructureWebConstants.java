package com.heuristiq.uam.orgstructure.adapter.in;

/** HTTP-facing fixed values of the org structure controllers: base path and request headers. */
final class OrgStructureWebConstants {

    /** Base path of every org structure endpoint. */
    static final String BASE = "/api/v1/uam/org-structure";

    /** Optional header naming the organization an organization-wide caller wants to work in. */
    static final String ORG_HEADER = "X-Org-Id";

    private OrgStructureWebConstants() {
    }
}
