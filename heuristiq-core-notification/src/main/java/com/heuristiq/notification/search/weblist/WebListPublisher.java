package com.heuristiq.notification.search.weblist;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Publishes the NMS web lists ({@code WEB_NMS_*}) to Rider_SearchAPI: every few minutes in the background, and shortly after
 * a change made through this API (see {@link WebListChanges}). Each organization's partition is replaced as a whole (rows that
 * disappeared are removed from search), and a list is marked complete once all its partitions went through, which is when
 * search starts serving it. Everything runs on one background thread, so a change never overlaps the periodic run.
 */
@Component
public class WebListPublisher implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WebListPublisher.class);
    static final int BATCH_SIZE = 100;
    private static final String PENDING_SEPARATOR = "|";

    private final WebListSource source;
    private final WebListTransport transport;
    private final WebListProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile ScheduledExecutorService scheduler;
    /** Lists changed since the last drain, as {@code model|tenantId} ({@code model|} for the whole list). */
    private final Set<String> pending = new LinkedHashSet<>();
    private boolean drainScheduled;

    public WebListPublisher(WebListSource source, WebListTransport transport, WebListProperties properties) {
        this.source = source;
        this.transport = transport;
        this.properties = properties;
    }

    /** What one full run published: rows sent, and the lists or partitions that failed (with why). */
    public record Report(long records, List<String> failures) {
    }

    /**
     * Publishes every list, unless a run is already under way (then returns 0 rows and no failures without doing anything).
     * A failing partition is logged and skipped; the list is then not marked complete, and the next run tries again.
     */
    public Report publishAll() {
        if (!properties.enabled() || !running.compareAndSet(false, true)) {
            return new Report(0, List.of());
        }
        long records = 0;
        List<String> failures = new ArrayList<>();
        try {
            for (String model : source.models()) {
                records += publishModel(model, failures);
            }
            log.info("search.weblist.done records={} failures={}", records, failures.size());
            return new Report(records, List.copyOf(failures));
        } finally {
            running.set(false);
        }
    }

    /** Every partition of one list, then MODEL_COMPLETE when none failed. */
    private long publishModel(String model, List<String> failures) {
        long records = 0;
        try {
            boolean complete = true;
            for (WebListSource.Partition partition : source.partitions(model)) {
                try {
                    records += publish(model, partition);
                } catch (RuntimeException e) {
                    complete = false;
                    failures.add(model + "/" + partition.key() + ": " + e.getMessage());
                    log.warn("search.weblist.failed model={} partition={} reason={}", model, partition.key(), e.getMessage());
                }
            }
            if (complete) {
                transport.send("MODEL_COMPLETE", model, WebListSource.Partition.organization(WebListSource.DEFAULT_TENANT),
                        UUID.randomUUID().toString(), List.of());
            }
        } catch (RuntimeException e) {
            failures.add(model + ": " + e.getMessage());
            log.warn("search.weblist.failed model={} reason={}", model, e.getMessage());
        }
        return records;
    }

    /**
     * Sends a list again shortly after a change: only {@code tenantId}'s partition when given, otherwise the whole list. Changes
     * arriving close together are sent once, after a short pause, on the publishing thread. Does nothing while publishing is off.
     */
    public void changed(String model, String tenantId) {
        ScheduledExecutorService current = scheduler;
        if (!properties.enabled() || current == null) {
            return;
        }
        synchronized (pending) {
            pending.add(model + PENDING_SEPARATOR + (tenantId == null || tenantId.isBlank() ? "" : tenantId.trim()));
            if (drainScheduled) {
                return;
            }
            drainScheduled = true;
        }
        current.schedule(this::drain, properties.changeDelayMs(), TimeUnit.MILLISECONDS);
    }

    /** Sends every list changed since the last drain: whole lists first, then single organizations not already covered. */
    void drain() {
        List<String> work;
        synchronized (pending) {
            work = new ArrayList<>(pending);
            pending.clear();
            drainScheduled = false;
        }
        Set<String> whole = new LinkedHashSet<>();
        for (String item : work) {
            if (item.endsWith(PENDING_SEPARATOR)) {
                whole.add(item.substring(0, item.length() - PENDING_SEPARATOR.length()));
            }
        }
        for (String model : whole) {
            publishModel(model, new ArrayList<>());
        }
        for (String item : work) {
            int at = item.indexOf(PENDING_SEPARATOR);
            String model = item.substring(0, at);
            String tenantId = item.substring(at + PENDING_SEPARATOR.length());
            if (tenantId.isEmpty() || whole.contains(model)) {
                continue;
            }
            try {
                publish(model, WebListSource.Partition.organization(tenantId));
            } catch (RuntimeException e) {
                log.warn("search.weblist.failed model={} partition={} reason={}", model, tenantId, e.getMessage());
            }
        }
        log.info("search.weblist.changed lists={}", work.size());
    }

    /** Replaces one partition of a list in search with its current rows. */
    long publish(String model, WebListSource.Partition partition) {
        JsonNode spec = source.spec(model);
        String generation = UUID.randomUUID().toString();
        transport.send("BEGIN", model, partition, generation, List.of());
        List<Map<String, Object>> batch = new ArrayList<>();
        long[] published = {0};
        source.scan(model, partition, row -> {
            batch.add(record(model, spec, partition, row, published[0]));
            published[0]++;
            if (batch.size() == BATCH_SIZE) {
                transport.send("BATCH", model, partition, generation, List.copyOf(batch));
                batch.clear();
            }
        });
        if (!batch.isEmpty()) {
            transport.send("BATCH", model, partition, generation, List.copyOf(batch));
        }
        transport.send("COMPLETE", model, partition, generation, List.of());
        return published[0];
    }

    /** One row as search stores it: the row itself as the payload, plus the slots it filters and sorts on. */
    Map<String, Object> record(String model, JsonNode spec, WebListSource.Partition partition, Map<String, Object> row, long position) {
        JsonNode data = source.json().valueToTree(row);
        JsonNode id = WebListRows.at(data, spec.path("id").asText());
        if (id.isMissingNode() || id.isNull() || id.isContainerNode() || id.asText().isBlank()) {
            throw new IllegalStateException("A " + model + " row has no id");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ownerKey", partition.tenantId());
        fields.put("tenantId", partition.tenantId());
        fields.put("payload", row);
        spec.path("slots").properties().forEach(slot -> {
            JsonNode value = WebListRows.at(data, slot.getValue().asText());
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.isValueNode() ? value.asText() : value.toString();
                if (slot.getKey().matches("(?:date|datetime)\\d+")) {
                    if (text.isBlank()) {
                        return;
                    }
                    text = instant(text.matches("\\d{4}-\\d{2}-\\d{2}") ? text + "T00:00:00Z" : text, spec);
                }
                fields.put(slot.getKey(), text);
            }
        });
        fields.put("text13", data.toString().toLowerCase(Locale.ROOT));
        if (spec.path("sourceOrdered").asBoolean()) {
            fields.put("number5", position);
        }
        spec.path("normalizers").properties().forEach(normalizer -> {
            Object value = fields.get(normalizer.getKey());
            if (value == null) {
                return;
            }
            switch (normalizer.getValue().asText()) {
                case "lower" -> fields.put(normalizer.getKey(), value.toString().toLowerCase(Locale.ROOT));
                case "upper" -> fields.put(normalizer.getKey(), value.toString().toUpperCase(Locale.ROOT));
                default -> { }
            }
        });
        JsonNode created = WebListRows.at(data, spec.path("createdAt").asText("createdAt"));
        fields.put("createdAt", created.isTextual() && !created.asText().isBlank()
                ? instant(created.asText(), spec) : Instant.EPOCH.toString());
        return Map.of("modelKey", key(partition.tenantId() + ":" + partition.key() + ":" + id.asText()), "fields", fields);
    }

    /** A timestamp as an instant; a zone-less one ({@code LocalDateTime}) is read in this server's zone ({@code sourceTimeZone: SYSTEM}). */
    private static String instant(String text, JsonNode spec) {
        try {
            return Instant.parse(text).toString();
        } catch (DateTimeParseException notAnInstant) {
            if (!"SYSTEM".equals(spec.path("sourceTimeZone").asText())) {
                throw notAnInstant;
            }
            return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant().toString();
        }
    }

    /** A stable, fixed-length search key for a row of a partition. */
    static String key(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Override
    public synchronized void start() {
        if (!properties.enabled() || scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "nms-search-weblists");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                publishAll();
            } catch (RuntimeException e) {
                log.warn("search.weblist.failed reason={}", e.getMessage());
            }
        }, properties.initialDelayMs(), properties.refreshMs(), TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    @Override
    public boolean isRunning() {
        return scheduler != null;
    }
}
