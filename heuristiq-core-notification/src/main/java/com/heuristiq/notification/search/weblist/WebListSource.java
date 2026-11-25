package com.heuristiq.notification.search.weblist;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.heuristiq.notification.adapters.in.web.broadcast.BroadcastController;
import com.heuristiq.notification.adapters.in.web.event.EventController;
import com.heuristiq.notification.adapters.in.web.template.TemplateController;
import com.heuristiq.notification.application.security.NmsPrincipal;

/**
 * The NMS lists the web screens read from search ({@code WEB_NMS_*}): one partition per organization, and its rows read through
 * this service's own list endpoints exactly as a person of that organization would get them. The contracts are
 * Rider_SearchAPI's (copied into {@code search/web-list-models.json}).
 */
@Component
public class WebListSource {

    static final String MODELS_RESOURCE = "/search/web-list-models.json";
    static final String DEFAULT_TENANT = "default";
    static final String INDEXER_USER = "search-indexer";
    private static final String TENANT_MDC_KEY = "tenantId";
    private static final int PAGE_SIZE = 100;

    /** One unit published and replaced as a whole: an organization's rows of a list. */
    public record Partition(String tenantId, String key) {

        public Partition {
            if (tenantId == null || tenantId.isBlank() || key == null || key.isBlank()) {
                throw new IllegalArgumentException("A partition needs an organization and a key");
            }
        }

        static Partition organization(String tenantId) {
            return new Partition(tenantId, tenantId);
        }
    }

    private final Map<String, JsonNode> models = new LinkedHashMap<>();
    private final ObjectMapper json;
    private final WebListTenants tenants;
    private final TemplateController templates;
    private final EventController events;
    private final BroadcastController broadcasts;

    public WebListSource(ObjectMapper mapper, WebListTenants tenants, @Lazy TemplateController templates,
                         @Lazy EventController events, @Lazy BroadcastController broadcasts) throws IOException {
        this.json = mapper.copy().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.tenants = tenants;
        this.templates = templates;
        this.events = events;
        this.broadcasts = broadcasts;
        try (InputStream in = getClass().getResourceAsStream(MODELS_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + MODELS_RESOURCE);
            }
            for (JsonNode spec : json.readTree(in)) {
                models.put(spec.path("model").asText(), spec);
            }
        }
    }

    public List<String> models() {
        return List.copyOf(models.keySet());
    }

    JsonNode spec(String model) {
        JsonNode spec = models.get(model);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown NMS list " + model);
        }
        return spec;
    }

    ObjectMapper json() {
        return json;
    }

    /** One partition per organization NMS has records for, plus the default organization. */
    List<Partition> partitions(String model) {
        spec(model);
        return tenants.organizations().stream().map(Partition::organization).toList();
    }

    /**
     * Every row of the list in this partition, read as a person of the partition's organization: the same verified-caller
     * principal {@code NmsAuthenticationFilter} sets for a request, so {@code TenantAccess} limits the read to that
     * organization's records plus the shared ones. The caller's own security context is restored afterwards.
     */
    void scan(String model, Partition partition, Consumer<Map<String, Object>> sink) {
        JsonNode spec = spec(model);
        String reader = spec.path("reader").asText();
        var previous = SecurityContextHolder.getContext();
        String previousTenant = MDC.get(TENANT_MDC_KEY);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                new NmsPrincipal(INDEXER_USER, partition.tenantId(), false), null, List.of()));
        SecurityContextHolder.setContext(context);
        MDC.put(TENANT_MDC_KEY, partition.tenantId());
        try {
            WebListRows.scan(json, page -> switch (reader) {
                case "templates" -> templates.listAll(PageRequest.of(page, PAGE_SIZE));
                case "events" -> events.listEvents();
                case "broadcasts" -> broadcasts.list(PageRequest.of(page, PAGE_SIZE));
                default -> throw new IllegalArgumentException("Unknown NMS list reader " + reader);
            }, spec.path("sourcePaged").asBoolean(), spec.path("sourceRows").asText(), sink);
        } finally {
            SecurityContextHolder.setContext(previous);
            if (previousTenant == null) {
                MDC.remove(TENANT_MDC_KEY);
            } else {
                MDC.put(TENANT_MDC_KEY, previousTenant);
            }
        }
    }
}
