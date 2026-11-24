package com.heuristiq.search.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Leased source generations enqueue ordinary INDEX/DELETE events in SearchAPI's existing stream. */
@Service
public class ProjectionIngressService {
    private static final DefaultRedisScript<Long> BEGIN = script("""
            local lease=redis.call('GET',KEYS[1])
            if lease and lease~=ARGV[1] then return 0 end
            if not lease then redis.call('DEL',KEYS[2]) end
            redis.call('SET',KEYS[1],ARGV[1],'EX',300)
            return 1
            """);
    private static final DefaultRedisScript<Long> RECORDS = script("""
            if redis.call('GET',KEYS[1])~=ARGV[1] then return 0 end
            redis.call('EXPIRE',KEYS[1],300)
            for i=4,#ARGV,2 do
              redis.call('XADD',KEYS[3],'MAXLEN','~',ARGV[3],'*','operation','INDEX','model',ARGV[2],'modelKey',ARGV[i],'payload',ARGV[i+1])
              redis.call('SADD',KEYS[2],ARGV[i])
            end
            return 1
            """);
    private static final DefaultRedisScript<Long> DELETES = script("""
            if redis.call('GET',KEYS[1])~=ARGV[1] then return 0 end
            redis.call('EXPIRE',KEYS[1],300)
            for i=4,#ARGV do
              redis.call('XADD',KEYS[2],'MAXLEN','~',ARGV[3],'*','operation','DELETE','model',ARGV[2],'modelKey',ARGV[i])
            end
            return 1
            """);
    private static final DefaultRedisScript<Long> COMPLETE = script("""
            if redis.call('GET',KEYS[4])==ARGV[1] then return 1 end
            if redis.call('GET',KEYS[1])~=ARGV[1] then return 0 end
            redis.call('DEL',KEYS[3])
            if redis.call('EXISTS',KEYS[2])==1 then redis.call('RENAME',KEYS[2],KEYS[3]) end
            redis.call('SET',KEYS[4],ARGV[1])
            redis.call('DEL',KEYS[1])
            return 1
            """);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final String stream;
    private final long maxLength;
    public ProjectionIngressService(StringRedisTemplate redis, ObjectMapper json,
            @Value("${railcab.search.stream.name:railcab:search:index-stream}") String stream,
            @Value("${railcab.search.stream.max-length:100000}") long maxLength) {
        this.redis = redis; this.json = json; this.stream = stream; this.maxLength = maxLength;
    }

    public void accept(ProjectionIngressController.Batch batch) {
        if (batch == null || !WebSearchCatalog.contains(batch.model())) throw bad("Unknown source projection model");
        valid(batch.tenantId(), 64); valid(batch.partition(), 256); valid(batch.generation(), 64);
        if (batch.records() == null || batch.records().size() > 100) throw bad("A projection batch has at most 100 records");
        String prefix = "railcab:search:projection:" + hash(batch.model() + ":" + batch.tenantId() + ":" + batch.partition());
        String lease = prefix + ":lease", seen = prefix + ":seen", manifest = prefix + ":manifest", completed = prefix + ":completed";
        switch (batch.phase() == null ? "" : batch.phase()) {
            case "MODEL_COMPLETE" -> redis.opsForValue().set(readyKey(batch.model()), "true");
            case "TENANT_COMPLETE" -> redis.opsForValue().set(readyKey(batch.model()) + ":" + batch.tenantId(), "true");
            case "BEGIN" -> execute(BEGIN, List.of(lease, seen), batch.generation());
            case "BATCH" -> {
                List<String> args = new ArrayList<>(List.of(batch.generation(), batch.model(), Long.toString(maxLength)));
                for (var record : batch.records()) {
                    valid(record.modelKey(), 200);
                    if (record.fields() == null || !(record.fields().get("payload") instanceof Map<?,?>)) throw bad("A source record needs its public DTO");
                    Map<String,Object> fields = new LinkedHashMap<>(record.fields());
                    fields.put("ownerKey", batch.tenantId()); fields.put("tenantId", batch.tenantId());
                    args.add(record.modelKey());
                    try { args.add(json.writeValueAsString(fields)); }
                    catch (JsonProcessingException e) { throw bad("Invalid source record"); }
                }
                execute(RECORDS, List.of(lease, seen, stream), args.toArray(String[]::new));
            }
            case "COMPLETE" -> {
                if (batch.generation().equals(redis.opsForValue().get(completed))) return;
                List<String> stale = new ArrayList<>();
                try (var cursor = redis.opsForSet().scan(manifest, ScanOptions.scanOptions().count(1000).build())) {
                    while (cursor.hasNext()) {
                        String key = cursor.next();
                        if (!Boolean.TRUE.equals(redis.opsForSet().isMember(seen, key))) stale.add(key);
                        if (stale.size() == 100) { delete(lease, batch, stale); stale.clear(); }
                    }
                }
                if (!stale.isEmpty()) delete(lease, batch, stale);
                execute(COMPLETE, List.of(lease, seen, manifest, completed, readyKey(batch.model())), batch.generation());
            }
            default -> throw bad("Unknown source projection phase");
        }
    }
    public boolean ready(String model) { return "true".equals(redis.opsForValue().get(readyKey(model))); }
    public boolean ready(String model, String tenant) {
        return ready(model) || (tenant != null && "true".equals(redis.opsForValue().get(readyKey(model) + ":" + tenant)));
    }
    private void delete(String lease, ProjectionIngressController.Batch batch, List<String> keys) {
        List<String> args = new ArrayList<>(List.of(batch.generation(), batch.model(), Long.toString(maxLength))); args.addAll(keys);
        execute(DELETES, List.of(lease, stream), args.toArray(String[]::new));
    }
    private void execute(DefaultRedisScript<Long> script, List<String> keys, String... values) {
        Long accepted = redis.execute(script, keys, (Object[]) values);
        if (!Long.valueOf(1).equals(accepted)) throw new ResponseStatusException(HttpStatus.CONFLICT, "The source projection lease was lost");
    }
    private static String readyKey(String model) { return "railcab:search:projection:ready:" + model; }
    private static void valid(String value, int limit) { if (value == null || value.isBlank() || value.length() > limit) throw bad("Invalid projection identity"); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static DefaultRedisScript<Long> script(String value) { return new DefaultRedisScript<>(value, Long.class); }
    private static String hash(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
