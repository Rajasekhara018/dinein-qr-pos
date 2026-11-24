package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/** A source GetAll POST is a list read and requires that source GET's existing privilege. */
final class DirectGetAllPaths {
    private record Route(String service, String path) {}
    private static final List<Route> ROUTES = load();
    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private DirectGetAllPaths() {}

    private static List<Route> load() {
        try (var in = DirectGetAllPaths.class.getResourceAsStream("/direct-getall-routes.json")) {
            if (in == null) throw new IllegalStateException("Missing direct GetAll route catalog");
            List<Route> routes = new ArrayList<>();
            for (JsonNode row : new ObjectMapper().readTree(in)) routes.add(new Route(row.path("service").asText(), row.path("path").asText()));
            return List.copyOf(routes);
        } catch (IOException e) { throw new IllegalStateException("Cannot load direct GetAll routes", e); }
    }

    static String source(String path, HttpMethod method, String service) {
        if (method != HttpMethod.POST || !path.endsWith("/getall")) return null;
        String source = path.substring(0, path.length() - "/getall".length());
        return ROUTES.stream().anyMatch(route -> route.service().equals(service) && MATCHER.match(route.path(), source)) ? source : null;
    }
}
