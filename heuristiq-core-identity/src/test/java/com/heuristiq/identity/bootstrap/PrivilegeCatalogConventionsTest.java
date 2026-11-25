package com.heuristiq.identity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Rules every entry of {@code privilege.json} must follow, so the role builder's View and Manage levels mean what they say:
 * <ul>
 *   <li><b>View</b> = READ and LIST, the GET calls: someone with only these can look but never create, change or delete.</li>
 *   <li><b>Manage</b> = CREATE, UPDATE, DELETE and MANAGE (a whole path): the calls that change something.</li>
 * </ul>
 * The operation must also agree with the end of the privilege's name, and the two copies (UAM and gateway) must be identical.
 */
class PrivilegeCatalogConventionsTest {
    private static final Path UAM_COPY = Path.of("src/main/resources/privilege.json");
    private static final Path GATEWAY_COPY = Path.of("../heuristiq-core-gateway/src/main/resources/privilege.json");

    private static final Set<String> VIEW_OPERATIONS = Set.of("READ", "LIST");
    private static final Set<String> CHANGE_OPERATIONS = Set.of("CREATE", "UPDATE", "DELETE", "MANAGE");

    /**
     * Entries that predate the rule and cannot be corrected without changing what the gateway lets through (it matches the
     * operation to the HTTP method): a POST that reads, or a name that ends in a verb of its own. Do not add to this list;
     * name a new privilege so that its last word agrees with its operation.
     */
    private static final Set<String> LEGACY_EXCEPTIONS = Set.of(
            "ADMIN_IN_APP_NOTIFICATION_MARK_READ", "ADMIN_IN_APP_NOTIFICATION_MARK_ALL_READ",
            "NMS_IN_APP_NOTIFICATION_MARK_READ", "NMS_IN_APP_NOTIFICATION_MARK_ALL_READ",
            "ADMIN_PAYMENT_LIST", "DISPATCH_DRIVER_STATUS_UPDATE", "RIDE_DRIVER_LOCATION_UPDATE", "MATRIMONY_PAYMENT_STATUS_UPDATE");

    private static JsonNode catalog;

    @BeforeAll
    static void load() throws IOException {
        catalog = new ObjectMapper().readTree(Files.readString(UAM_COPY));
    }

    @Test
    void theUamAndGatewayCopiesAreIdentical() throws IOException {
        assertThat(Files.readString(GATEWAY_COPY)).isEqualTo(Files.readString(UAM_COPY));
    }

    @Test
    void everyIdAndNameIsUnique() {
        Set<Integer> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (JsonNode entry : catalog) {
            if (!ids.add(entry.get("privilegeId").asInt())) {
                duplicates.add("id " + entry.get("privilegeId").asInt());
            }
            if (!names.add(entry.get("name").asText())) {
                duplicates.add("name " + entry.get("name").asText());
            }
        }
        assertThat(duplicates).as("ids are never reused and names never repeated").isEmpty();
    }

    @Test
    void everyOperationIsEitherViewOrManage() {
        List<String> unknown = new ArrayList<>();
        for (JsonNode entry : catalog) {
            String operation = entry.get("operation").asText();
            if (!VIEW_OPERATIONS.contains(operation) && !CHANGE_OPERATIONS.contains(operation)) {
                unknown.add(entry.get("name").asText() + " -> " + operation);
            }
        }
        assertThat(unknown).as("operation must be READ, LIST, CREATE, UPDATE, DELETE or MANAGE").isEmpty();
    }

    @Test
    void theEndOfTheNameAgreesWithTheOperation() {
        List<String> wrong = new ArrayList<>();
        for (JsonNode entry : catalog) {
            String name = entry.get("name").asText();
            String operation = entry.get("operation").asText();
            String lastWord = name.substring(name.lastIndexOf('_') + 1);
            boolean nameSaysView = VIEW_OPERATIONS.contains(lastWord);
            boolean nameSaysChange = CHANGE_OPERATIONS.contains(lastWord);
            boolean agrees = (!nameSaysView && !nameSaysChange)
                    || (nameSaysView && VIEW_OPERATIONS.contains(operation))
                    || (nameSaysChange && operation.equals(lastWord));
            if (!agrees && !LEGACY_EXCEPTIONS.contains(name)) {
                wrong.add(name + " is named ..._" + lastWord + " but its operation is " + operation);
            }
        }
        assertThat(wrong).as("a privilege that only looks is READ or LIST, one that changes something is CREATE, UPDATE, DELETE or MANAGE")
                .isEmpty();
    }
}
