package com.heuristq.dinein.shared.web;

/**
 * URL-path API versions. Controllers declare the version literally in their class-level {@code @RequestMapping}
 * (e.g. {@code /api/v1/admin/tables}); non-controller code that needs a full path (security matchers, filters,
 * cookie paths, generated URLs) uses these constants.
 */
public final class ApiPaths {

    public static final String V1 = "/api/v1";

    private ApiPaths() {
    }
}
