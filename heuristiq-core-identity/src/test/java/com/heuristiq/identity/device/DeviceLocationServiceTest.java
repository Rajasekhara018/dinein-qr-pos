package com.heuristiq.identity.device;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.device.repository.UserDeviceRepository;
import com.heuristiq.identity.session.UserSession;
import com.heuristiq.identity.session.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceLocationServiceTest {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0) Chrome/120";

    private UserDeviceRepository devices;
    private UserSessionRepository sessions;
    private DeviceLocationService service;
    private UserDevice device;

    @BeforeEach
    void setUp() {
        devices = mock(UserDeviceRepository.class);
        sessions = mock(UserSessionRepository.class);
        service = new DeviceLocationService(devices, sessions);
        device = UserDevice.builder().id("dev-1").userId("u1").deviceFingerprint(DeviceFingerprint.of(UA)).build();
        when(devices.findByUserId("u1")).thenReturn(List.of(device));
        when(devices.save(any(UserDevice.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static DeviceLocationService.LocationInput at(double lat, double lng) {
        return new DeviceLocationService.LocationInput(lat, lng, 25.4, null, null, null);
    }

    @SuppressWarnings("unchecked")
    @Test
    void theCoordinatesAreStoredOnTheDeviceThatMadeTheRequest() {
        UserDevice saved = service.record("u1", UA, at(12.971598, 77.594566));

        Map<String, Object> location = (Map<String, Object>) saved.getMetadata().get("location");
        assertEquals(12.971598, location.get("latitude"));
        assertEquals(77.594566, location.get("longitude"));
        assertEquals(25L, location.get("accuracyMeters"));
        assertEquals(true, location.containsKey("capturedAt"));
    }

    @Test
    void labelsFillTheColumnsTheActiveDevicesListAlreadyShows() {
        service.record("u1", UA, new DeviceLocationService.LocationInput(12.97, 77.59, null, "  Bengaluru ", "Karnataka", "India"));

        assertEquals("Bengaluru", device.getCity());
        assertEquals("Karnataka", device.getState());
        assertEquals("India", device.getCountry());
    }

    @Test
    void existingMetadataIsKept() {
        device.setMetadata(Map.of("note", "kept"));

        service.record("u1", UA, at(1, 2));

        assertEquals("kept", device.getMetadata().get("note"));
    }

    @Test
    void onlyTheActiveSessionsOfThatDeviceAreUpdated() {
        UserSession mine = UserSession.builder().sessionId("s1").userId("u1").deviceId("dev-1").build();
        UserSession other = UserSession.builder().sessionId("s2").userId("u1").deviceId("dev-2").build();
        when(sessions.findByUserIdAndActiveTrue("u1")).thenReturn(List.of(mine, other));

        service.record("u1", UA, new DeviceLocationService.LocationInput(12.97, 77.59, null, "Bengaluru", null, null));

        assertEquals("Bengaluru", mine.getCity());
        assertEquals(true, mine.getMetadata().containsKey("location"));
        assertNull(other.getCity());
        assertNull(other.getMetadata());
        verify(sessions).saveAll(List.of(mine));
    }

    @Test
    void aBrowserWithNoRegisteredDeviceIsRejected() {
        assertThrows(UamException.class, () -> service.record("u1", "Some other browser", at(1, 2)));
        verify(devices, never()).save(any());
    }

    @Test
    void outOfRangeOrMissingCoordinatesAreRejected() {
        assertThrows(UamException.class, () -> service.record("u1", UA, at(91, 0)));
        assertThrows(UamException.class, () -> service.record("u1", UA, at(0, 181)));
        assertThrows(UamException.class, () -> service.record("u1", UA, new DeviceLocationService.LocationInput(null, 5.0, null, null, null, null)));
        verify(sessions, never()).saveAll(anyList());
    }

    @Test
    void labelsAreCleanedOfControlCharactersAndLimited() {
        assertEquals("Pune", DeviceLocationService.clean("Pu\nne\t"));
        assertEquals(100, DeviceLocationService.clean("x".repeat(300)).length());
        assertNull(DeviceLocationService.clean("   "));
    }
}
