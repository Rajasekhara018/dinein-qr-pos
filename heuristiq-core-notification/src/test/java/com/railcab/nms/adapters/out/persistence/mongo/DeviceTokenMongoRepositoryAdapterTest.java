package com.railcab.nms.adapters.out.persistence.mongo;

import com.railcab.nms.domain.model.DeviceRegistration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceTokenMongoRepositoryAdapterTest {

    private final DeviceTokenMongoSpringRepository spring = mock(DeviceTokenMongoSpringRepository.class);
    private final DeviceTokenMongoRepositoryAdapter adapter = new DeviceTokenMongoRepositoryAdapter(spring);

    private static final DeviceRegistration DETAILS = new DeviceRegistration(
            "android", "DRIVER", "DRIVER_APP", "Pixel 8", "Android 14", "1.4.2+17", "en_IN", "Asia/Kolkata");

    @Test
    void newTokenIsStoredWithItsDeviceDetailsAndLastSeen() {
        when(spring.findFirstByUserIdAndToken("u1", "tok")).thenReturn(Optional.empty());

        adapter.register("u1", "tok", DETAILS);

        ArgumentCaptor<DeviceTokenDocument> saved = ArgumentCaptor.forClass(DeviceTokenDocument.class);
        verify(spring).save(saved.capture());
        assertEquals("u1", saved.getValue().getUserId());
        assertEquals("DRIVER", saved.getValue().getUserType());
        assertEquals("Pixel 8", saved.getValue().getDeviceModel());
        assertEquals("1.4.2+17", saved.getValue().getAppVersion());
        assertNotNull(saved.getValue().getCreatedAt());
        assertNotNull(saved.getValue().getLastSeenAt());
    }

    @Test
    void repeatRegistrationUpdatesTheExistingRowInsteadOfAddingOne() {
        DeviceTokenDocument existing = new DeviceTokenDocument();
        existing.setUserId("u1");
        existing.setToken("tok");
        existing.setAppVersion("1.0.0");
        when(spring.findFirstByUserIdAndToken("u1", "tok")).thenReturn(Optional.of(existing));

        adapter.register("u1", "tok", DETAILS);

        ArgumentCaptor<DeviceTokenDocument> saved = ArgumentCaptor.forClass(DeviceTokenDocument.class);
        verify(spring).save(saved.capture());
        assertEquals(existing, saved.getValue());
        assertEquals("1.4.2+17", saved.getValue().getAppVersion());
    }

    @Test
    void theSameTokenUnderAnotherUserIsRemovedSoOldAccountsDoNotGetPushesOnThisDevice() {
        when(spring.findFirstByUserIdAndToken(any(), any())).thenReturn(Optional.empty());

        adapter.register("u2", "tok", DETAILS);

        verify(spring).deleteByTokenAndUserIdNot("tok", "u2");
    }

    @Test
    void registrationsBeyondTheCapAreDroppedOldestFirstIncludingLegacyRowsWithoutLastSeen() {
        when(spring.findFirstByUserIdAndToken(any(), any())).thenReturn(Optional.empty());
        LocalDateTime now = LocalDateTime.now();
        List<DeviceTokenDocument> stored = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            stored.add(doc("recent-" + i, now.minusMinutes(i), null));
        }
        // a pre-migration row has no lastSeenAt; its (old) createdAt must make it the eviction candidate
        stored.add(doc("legacy", null, now.minusDays(200)));
        when(spring.findByUserId("u1")).thenReturn(stored);

        adapter.register("u1", "recent-0", DETAILS);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DeviceTokenDocument>> dropped = ArgumentCaptor.forClass((Class) List.class);
        verify(spring).deleteAll(dropped.capture());
        assertEquals(1, dropped.getValue().size());
        assertEquals("legacy", dropped.getValue().get(0).getToken());
    }

    @Test
    void nothingIsDroppedWhileUnderTheCap() {
        when(spring.findFirstByUserIdAndToken(any(), any())).thenReturn(Optional.empty());
        when(spring.findByUserId("u1")).thenReturn(List.of(doc("a", LocalDateTime.now(), null)));

        adapter.register("u1", "a", DETAILS);

        verify(spring, never()).deleteAll(anyIterable());
    }

    @Test
    void aTokenReportedDeadIsRemovedForEveryUser() {
        adapter.unregisterToken("dead");

        verify(spring).deleteByToken("dead");
    }

    private static DeviceTokenDocument doc(String token, LocalDateTime lastSeen, LocalDateTime created) {
        DeviceTokenDocument doc = new DeviceTokenDocument();
        doc.setUserId("u1");
        doc.setToken(token);
        doc.setLastSeenAt(lastSeen);
        doc.setCreatedAt(created);
        return doc;
    }

    @Test
    void platformOnlyRegistrationStillWorksForOlderClients() {
        when(spring.findFirstByUserIdAndToken("u1", "tok")).thenReturn(Optional.empty());

        adapter.register("u1", "tok", "android");

        ArgumentCaptor<DeviceTokenDocument> saved = ArgumentCaptor.forClass(DeviceTokenDocument.class);
        verify(spring).save(saved.capture());
        assertEquals("android", saved.getValue().getPlatform());
    }
}
