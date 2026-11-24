package com.heuristiq.uam.organization.operatingmodel.support;

import com.heuristiq.uam.organization.operatingmodel.application.port.OperatingModelScheduleStore;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelConstants;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelException;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelPeriod;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test store with the same contract as the real ones: per organization, and refusing a write over a changed schedule. */
public final class InMemoryOperatingModelScheduleStore implements OperatingModelScheduleStore {

    private final Map<String, List<OperatingModelPeriod>> rows = new HashMap<>();
    private int writes;

    @Override
    public List<OperatingModelPeriod> find(String organizationId) {
        return rows.getOrDefault(organizationId, List.of());
    }

    @Override
    public void replace(String organizationId, List<OperatingModelPeriod> expected, List<OperatingModelPeriod> next, String userId,
                        Instant at) {
        if (!find(organizationId).equals(expected)) {
            throw OperatingModelException.conflict(OperatingModelConstants.ERROR_CHANGED_MEANWHILE);
        }
        writes++;
        rows.put(organizationId, List.copyOf(next));
    }

    /** Puts changes in place as if another instance or person had written them. */
    public void putDirectly(String organizationId, List<OperatingModelPeriod> periods) {
        rows.put(organizationId, List.copyOf(periods));
    }

    public int writes() {
        return writes;
    }
}
