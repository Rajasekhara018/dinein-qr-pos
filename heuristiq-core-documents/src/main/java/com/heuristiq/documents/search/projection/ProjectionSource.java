package com.railcab.dms.search.projection;

import java.util.List;

/**
 * Port: reads the rows of one web model of one organization, a page at a time, through DMS's own application
 * services, so the published rows are what the list API returns.
 */
public interface ProjectionSource {

    /** One page of rows, and whether it is the last. */
    record Page(List<ProjectionRecord> records, boolean last) {
    }

    /** The Rider_SearchAPI model this source fills. */
    String model();

    /** Rows of the organization in the source's own order; {@code size} is at most {@link ProjectionConstants#BATCH_SIZE}. */
    Page page(String tenantId, int pageIndex, int size);
}
