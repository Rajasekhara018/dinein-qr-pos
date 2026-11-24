package com.railcab.nms.search.weblist;

import java.util.ArrayList;
import java.util.List;

import com.railcab.nms.adapters.in.web.WebApiConstants;

/**
 * Which web lists a successful change made through this API affects, so they reach search right away instead of at the next
 * periodic publication.
 *
 * <p>Only a new broadcast is reliably limited to the caller's organization (it is always stamped with it). Everything else is
 * sent as a whole list: a template may be a shared one every organization sees (a caller may create or edit those), event
 * definitions belong to no organization, and a cancelled broadcast may be one stored before organizations existed.
 */
public final class WebListChanges {

    static final String TEMPLATE = "WEB_NMS_TEMPLATE";
    static final String EVENT_TEMPLATE = "WEB_NMS_EVENT_TEMPLATE";
    static final String EVENT = "WEB_NMS_EVENT";
    static final String BROADCAST = "WEB_NMS_BROADCAST";

    /** One list to send again: all of it, or only the caller's organization's partition. */
    public record Change(String model, boolean callerOnly) {
    }

    private WebListChanges() {
    }

    /** The lists a successful {@code method} request to {@code path} affects (none for a path that touches no list). */
    public static List<Change> of(String method, String path) {
        if (path == null || !path.startsWith(WebApiConstants.API_BASE_PATH + "/")) {
            return List.of();
        }
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        List<Change> changes = new ArrayList<>();
        if (under(trimmed, WebApiConstants.TEMPLATES_PATH)) {
            if (!trimmed.equals(WebApiConstants.TEMPLATES_PATH + "/test-send")) {
                all(changes, TEMPLATE, EVENT_TEMPLATE);
            }
        } else if (under(trimmed, WebApiConstants.EVENTS_PATH)) {
            all(changes, EVENT);
        } else if (under(trimmed, WebApiConstants.BROADCASTS_PATH)) {
            boolean create = "POST".equalsIgnoreCase(method) && trimmed.equals(WebApiConstants.BROADCASTS_PATH);
            changes.add(new Change(BROADCAST, create));
        } else if (under(trimmed, WebApiConstants.SEARCH_REINDEX_PATH)) {
            all(changes, TEMPLATE, EVENT_TEMPLATE, EVENT, BROADCAST);
        }
        return changes;
    }

    private static boolean under(String path, String base) {
        return path.equals(base) || path.startsWith(base + "/");
    }

    private static void all(List<Change> changes, String... models) {
        for (String model : models) {
            changes.add(new Change(model, false));
        }
    }
}
