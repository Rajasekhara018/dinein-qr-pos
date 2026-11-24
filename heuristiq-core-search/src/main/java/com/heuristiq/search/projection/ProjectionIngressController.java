package com.heuristiq.search.projection;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

/** Internal source publishing only; never a browser-supplied table snapshot. */
@RestController
@RequestMapping("/api/v1/search/internal/projections")
public class ProjectionIngressController {
    public record Record(String modelKey, Map<String,Object> fields) {}
    public record Batch(String phase, String model, String tenantId, String partition, String generation, List<Record> records) {}
    private final ProjectionIngressService service;
    public ProjectionIngressController(ProjectionIngressService service) { this.service = service; }
    @PostMapping
    public Map<String,Object> accept(@RequestBody Batch batch) {
        service.accept(batch);
        return Map.of("success", true);
    }
}
