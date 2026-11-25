package com.heuristiq.identity.device;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.device.repository.UserDeviceRepository;
import com.heuristiq.identity.session.UserSession;
import com.heuristiq.identity.session.repository.UserSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records where a signed-in user's device is, as reported by that device's own browser.
 *
 * <p>The location goes on the device row and on its active session(s) -- the Active devices list in
 * the profile is the session list. Coordinates are kept under {@code metadata.location}; the optional
 * city / state / country labels fill the existing columns the list already shows. The device is the
 * one the request comes from, found by the same User-Agent fingerprint login used to create it.
 */
@Service
public class DeviceLocationService {


    /** What the browser reported. Coordinates are required; the labels are optional. */
    public record LocationInput(Double latitude, Double longitude, Double accuracyMeters,
                                String city, String state, String country) {
    }

    private final UserDeviceRepository deviceRepository;
    private final UserSessionRepository sessionRepository;

    public DeviceLocationService(UserDeviceRepository deviceRepository, UserSessionRepository sessionRepository) {
        this.deviceRepository = deviceRepository;
        this.sessionRepository = sessionRepository;
    }

    public UserDevice record(String userId, String userAgent, LocationInput input) {
        validate(input);
        String fingerprint = DeviceFingerprint.of(userAgent);
        UserDevice device = deviceRepository.findByUserId(userId).stream()
                .filter(d -> fingerprint.equals(d.getDeviceFingerprint()))
                .findFirst()
                .orElseThrow(() -> UamException.notFound("Device not found"));

        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> location = locationMap(input, now);

        device.setMetadata(withLocation(device.getMetadata(), location));
        applyLabels(input, device::setCity, device::setState, device::setCountry);
        device.setUpdatedAt(now);
        UserDevice saved = deviceRepository.save(device);

        List<UserSession> sessions = new ArrayList<>();
        for (UserSession session : sessionRepository.findByUserIdAndActiveTrue(userId)) {
            if (device.getId().equals(session.getDeviceId())) {
                session.setMetadata(withLocation(session.getMetadata(), location));
                applyLabels(input, session::setCity, session::setState, session::setCountry);
                session.setUpdatedAt(now);
                sessions.add(session);
            }
        }
        if (!sessions.isEmpty()) {
            sessionRepository.saveAll(sessions);
        }
        return saved;
    }

    private static void validate(LocationInput in) {
        if (in == null || in.latitude() == null || in.longitude() == null
                || in.latitude().isNaN() || in.longitude().isNaN()
                || in.latitude() < -90 || in.latitude() > 90 || in.longitude() < -180 || in.longitude() > 180) {
            throw new UamException("A valid latitude (-90..90) and longitude (-180..180) are required");
        }
        if (in.accuracyMeters() != null && (in.accuracyMeters().isNaN() || in.accuracyMeters() < 0)) {
            throw new UamException("accuracyMeters must not be negative");
        }
    }

    private static Map<String, Object> locationMap(LocationInput in, LocalDateTime now) {
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("latitude", round(in.latitude()));
        location.put("longitude", round(in.longitude()));
        if (in.accuracyMeters() != null) {
            location.put("accuracyMeters", Math.round(in.accuracyMeters()));
        }
        location.put("capturedAt", now.toInstant(ZoneOffset.UTC).toString());
        return location;
    }

    /** Six decimals is about 10 cm: more than a browser can know. */
    private static double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    private static Map<String, Object> withLocation(Map<String, Object> existing, Map<String, Object> location) {
        Map<String, Object> merged = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
        merged.put(DeviceConstants.LOCATION_METADATA_KEY, location);
        return merged;
    }

    private static void applyLabels(LocationInput in, java.util.function.Consumer<String> city,
                                    java.util.function.Consumer<String> state, java.util.function.Consumer<String> country) {
        String c = clean(in.city());
        String s = clean(in.state());
        String k = clean(in.country());
        if (c != null) city.accept(c);
        if (s != null) state.accept(s);
        if (k != null) country.accept(k);
    }

    /** A short, printable label, or null; these are shown in the UI, so control characters are dropped. */
    static String clean(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("\\p{Cntrl}", "").trim();
        if (cleaned.isEmpty()) return null;
        return cleaned.length() > DeviceConstants.LOCATION_LABEL_MAX ? cleaned.substring(0, DeviceConstants.LOCATION_LABEL_MAX) : cleaned;
    }
}
