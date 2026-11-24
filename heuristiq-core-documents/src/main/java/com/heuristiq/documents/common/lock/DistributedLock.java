package com.railcab.dms.common.lock;

import java.time.Duration;

/**
 * A short-lived, database-backed mutual-exclusion lease. Used to guard scheduled jobs
 * (e.g. {@code DocumentRetentionJob}) that must run on only one replica at a time even
 * though every pod in the deployment runs the same {@code @Scheduled} method.
 */
public interface DistributedLock {

    boolean tryAcquire(String key, Duration lease, String owner);

    void release(String key, String owner);
}
