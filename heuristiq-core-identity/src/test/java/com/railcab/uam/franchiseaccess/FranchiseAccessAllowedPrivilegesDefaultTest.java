package com.heuristiq.uam.franchiseaccess;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The franchise-role privilege allow-list is written twice: the code's default and the default in application.properties (which
 * is what a deployment actually uses). When a privilege was added to one only, every new franchise agent failed with "Privilege
 * is not allowed for a franchise role". The two must hold exactly the same privileges.
 */
class FranchiseAccessAllowedPrivilegesDefaultTest {

    private static final String KEY = "railcab.uam.franchise-access.allowed-privilege-prefixes";

    @Test
    void thePropertiesDefaultListsExactlyTheCodesDefaultPrivileges() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            properties.load(in);
        }
        String raw = properties.getProperty(KEY);
        assertThat(raw).as(KEY).isNotNull().contains(":").endsWith("}");
        String defaults = raw.substring(raw.indexOf(':') + 1, raw.length() - 1);
        Set<String> fromProperties = Arrays.stream(defaults.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        assertThat(fromProperties).containsExactlyInAnyOrderElementsOf(FranchiseAccessConstants.DEFAULT_ALLOWED_PRIVILEGE_PREFIXES);
    }
}
