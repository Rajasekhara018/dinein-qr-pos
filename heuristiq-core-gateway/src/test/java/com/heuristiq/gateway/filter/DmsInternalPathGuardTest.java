package com.heuristiq.gateway.filter;

import com.heuristiq.gateway.config.DmsRouteConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DmsInternalPathGuardTest {

    @Test
    void dmsInternalPathsAreRefusedInEverySpelling() {
        for (String path : new String[] {
                "/api/v1/dms/internal/document-types/apply",
                "/api/v1/dms/internal",
                "/api/v1/dms/INTERNAL/document-types/apply",
                "/api/v1/dms/%69nternal/document-types/apply",
                "/api/v1/dms//internal/document-types/apply",
                "/api/v1/dms/documents/../internal/document-types/apply"}) {
            assertThat(InternalPathGuard.isInternalPath(path, DmsRouteConstants.INTERNAL_PATH)).as(path).isTrue();
        }
    }

    @Test
    void ordinaryDmsPathsPass() {
        for (String path : new String[] {
                "/api/v1/dms/document-types",
                "/api/v1/dms/document-types/check-file",
                "/api/v1/dms/documents/metadata/internal-notes"}) {
            assertThat(InternalPathGuard.isInternalPath(path, DmsRouteConstants.INTERNAL_PATH)).as(path).isFalse();
        }
    }
}
