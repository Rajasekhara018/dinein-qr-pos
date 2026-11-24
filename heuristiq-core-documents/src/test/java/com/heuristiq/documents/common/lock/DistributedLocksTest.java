package com.railcab.dms.common.lock;

import com.railcab.dms.common.lock.mongo.MongoDistributedLock;
import com.railcab.dms.common.lock.postgres.PostgresDistributedLock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributedLocksTest {

    private static Object jobLock(String owner) throws Exception {
        Class<?> type = Class.forName("com.railcab.dms.common.lock.mongo.MongoDistributedLock$JobLock");
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object lock = ctor.newInstance();
        Field f = type.getDeclaredField("owner");
        f.setAccessible(true);
        f.set(lock, owner);
        return lock;
    }

    @Test
    void mongoAcquireSucceedsOnlyWhenReturnedOwnerIsCaller() throws Exception {
        MongoTemplate template = mock(MongoTemplate.class);
        MongoDistributedLock lock = new MongoDistributedLock(template);
        when(template.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), any(Class.class),
                anyString())).thenReturn(jobLock("me"), jobLock("other"), null);

        assertTrue(lock.tryAcquire("job", Duration.ofMinutes(1), "me"));
        assertFalse(lock.tryAcquire("job", Duration.ofMinutes(1), "me"));
        assertFalse(lock.tryAcquire("job", Duration.ofMinutes(1), "me"));
    }

    @Test
    void mongoAcquireIsFalseWhenAnotherHolderWinsTheUpsertRace() {
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), any(Class.class),
                anyString())).thenThrow(new DuplicateKeyException("dup"));
        assertFalse(new MongoDistributedLock(template).tryAcquire("job", Duration.ofMinutes(1), "me"));
    }

    @Test
    void mongoReleaseOnlyTouchesLocksOfThatOwner() {
        MongoTemplate template = mock(MongoTemplate.class);
        new MongoDistributedLock(template).release("job", "me");
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(template).updateFirst(query.capture(), any(Update.class), anyString());
        String json = query.getValue().getQueryObject().toJson();
        assertTrue(json.contains("job") && json.contains("me"), json);
    }

    @Test
    @SuppressWarnings("unchecked")
    void postgresAcquireDependsOnReturnedOwner() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PostgresDistributedLock lock = new PostgresDistributedLock(jdbc);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of("me"), List.of(), List.of("other"));

        assertTrue(lock.tryAcquire("job", Duration.ofMinutes(1), "me"));
        assertFalse(lock.tryAcquire("job", Duration.ofMinutes(1), "me"));
        assertFalse(lock.tryAcquire("job", Duration.ofMinutes(1), "me"));
    }

    @Test
    void postgresCreatesTableAndReleasesByOwner() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PostgresDistributedLock lock = new PostgresDistributedLock(jdbc);
        java.lang.reflect.Method m = PostgresDistributedLock.class.getDeclaredMethod("createLockTable");
        m.setAccessible(true);
        m.invoke(lock);
        verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("dms_job_locks"));

        lock.release("job", "me");
        verify(jdbc).update(anyString(), any(java.sql.Timestamp.class), eq("job"), eq("me"));
    }
}
