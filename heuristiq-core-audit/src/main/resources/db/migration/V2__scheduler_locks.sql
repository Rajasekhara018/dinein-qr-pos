-- Database lease used by SchedulerLock so a periodic job (audit retention purge) runs on ONE service
-- instance at a time. A row is taken with an atomic upsert and simply expires if its holder dies.
CREATE TABLE scheduler_locks (
    name         varchar(100) PRIMARY KEY,
    holder       varchar(100) NOT NULL,
    locked_until timestamptz  NOT NULL
);
