package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.privilege.PrivilegeCatalogLoader;
import com.heuristiq.gateway.privilege.PrivilegeEntry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The indexed catalog lookup must give exactly the decisions of the old full scan, over the whole real
 * {@code privilege.json}: for paths built from every entry (and variants around them), every service and every
 * method, the matching entries (in the same order) and the deciding privilege ids are identical.
 */
class PrivilegeCatalogIndexEquivalenceTest {

    private static final HttpMethod[] METHODS = {HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH,
            HttpMethod.DELETE, HttpMethod.valueOf("PROPFIND")};

    private final AntPathMatcher matcher = new AntPathMatcher();

    /** The scan the filter did before the index: every entry, in catalog order. */
    private static List<CompiledPrivilege> fullScan(PrivilegeCatalogIndex index, AntPathMatcher matcher, String uri,
            HttpMethod method, String service) {
        return index.all().stream().filter(e -> e.matches(matcher, uri, method, service)).toList();
    }

    /** The filter's decision from a list of matching entries: the privilege ids tied at the most specific pattern. */
    private Set<Integer> decisiveIds(List<CompiledPrivilege> matching, String uri) {
        if (matching.isEmpty()) {
            return Set.of();
        }
        Comparator<String> comparator = matcher.getPatternComparator(uri);
        String mostSpecific = matching.stream().map(e -> e.pattern).min(comparator).orElseThrow();
        return matching.stream().filter(e -> comparator.compare(e.pattern, mostSpecific) == 0)
                .map(e -> e.privilegeId).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> samplePathsFor(String apipath) {
        String concrete = apipath.replaceAll("\\{[^/]+}", "42").replace("/**", "/x/y").replace("*", "abc");
        Set<String> paths = new LinkedHashSet<>();
        paths.add(concrete);
        paths.add(concrete + "/extra");
        int lastSlash = concrete.lastIndexOf('/');
        if (lastSlash > 0) {
            paths.add(concrete.substring(0, lastSlash));
        }
        return paths;
    }

    @Test
    void indexedLookupMatchesTheFullScanForTheWholeRealCatalog() {
        List<PrivilegeEntry> entries = new PrivilegeCatalogLoader(new ObjectMapper()).load();
        assertThat(entries).hasSizeGreaterThan(1000);
        PrivilegeCatalogIndex index = PrivilegeCatalogIndex.of(entries);

        // Each sample path is tried as its own service and as service-less, each catalog path also as a neighbouring
        // service, with every kind of method.
        Set<List<String>> requests = new LinkedHashSet<>();
        for (String path : List.of("/", "/api", "/api/v1", "/api/v2/uam/users", "/actuator/health")) {
            requests.add(java.util.Arrays.asList(path, null));
            requests.add(java.util.Arrays.asList(path, "uam"));
        }
        for (PrivilegeEntry e : entries) {
            String neighbour = "uam".equals(e.service()) ? "admin" : "uam";
            for (String path : samplePathsFor(e.apipath())) {
                requests.add(java.util.Arrays.asList(path, e.service()));
                requests.add(java.util.Arrays.asList(path, null));
            }
            requests.add(java.util.Arrays.asList(e.apipath(), neighbour));
        }

        long comparisons = 0;
        long withMatches = 0;
        List<String> mismatches = new ArrayList<>();
        for (List<String> request : requests) {
            String path = request.get(0);
            String service = request.get(1);
            for (HttpMethod method : METHODS) {
                List<CompiledPrivilege> expected = fullScan(index, matcher, path, method, service);
                List<CompiledPrivilege> actual = index.matching(matcher, path, method, service);
                comparisons++;
                if (!expected.isEmpty()) {
                    withMatches++;
                }
                if (!expected.equals(actual) || !decisiveIds(expected, path).equals(decisiveIds(actual, path))) {
                    mismatches.add(method + " " + path + " service=" + service);
                }
            }
        }

        assertThat(mismatches).isEmpty();
        assertThat(comparisons).isGreaterThan(20_000);
        assertThat(withMatches).isGreaterThan(2_000);
    }

    @Test
    void anEntryOnlyMatchesItsOwnServiceAndMethods() {
        PrivilegeCatalogIndex index = PrivilegeCatalogIndex.of(List.of(
                new PrivilegeEntry(1, "/api/v1/uam/users/{id}", "uam", "READ"),
                new PrivilegeEntry(2, "/api/v1/uam/users/**", "uam", "MANAGE"),
                new PrivilegeEntry(3, "/api/v1/uam/users/{id}", "admin", "UPDATE"),
                new PrivilegeEntry(4, "/api/v1/uam/users/{id}", null, "DELETE")));

        assertThat(index.matching(matcher, "/api/v1/uam/users/7", HttpMethod.GET, "uam"))
                .extracting(e -> e.privilegeId).containsExactly(1, 2);
        assertThat(index.matching(matcher, "/api/v1/uam/users/7", HttpMethod.POST, "uam"))
                .extracting(e -> e.privilegeId).containsExactly(2);
        assertThat(index.matching(matcher, "/api/v1/uam/users/7", HttpMethod.PATCH, "admin"))
                .extracting(e -> e.privilegeId).containsExactly(3);
        assertThat(index.matching(matcher, "/api/v1/uam/users/7", HttpMethod.DELETE, null))
                .extracting(e -> e.privilegeId).containsExactly(4);
        assertThat(index.matching(matcher, "/api/v1/uam/users/7", HttpMethod.valueOf("PROPFIND"), "uam"))
                .extracting(e -> e.privilegeId).containsExactly(2);
        assertThat(index.matching(matcher, "/api/v1/uam/users/7", HttpMethod.GET, "rider-ride")).isEmpty();
        assertThat(PrivilegeCatalogIndex.empty().size()).isZero();
    }
}
