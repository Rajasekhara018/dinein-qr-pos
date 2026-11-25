package com.heuristiq.identity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The start-up seeding of the retired application codes (MATRIMONY and PASUPU_KUNKUMA_MATRIMONY) is gone: no seeder class, no
 * seed constants, and no main source that still names those codes. Existing rows are left in the database untouched.
 */
class LegacyApplicationSeedsRetiredTest {

    @Test
    void theApplicationSeederAndItsConstantsNoLongerExist() {
        assertThatThrownBy(() -> Class.forName("com.heuristiq.identity.bootstrap.ApplicationSeeder")).isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.heuristiq.identity.bootstrap.BootstrapApplicationConstants"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void noMainSourceNamesTheRetiredCodes() throws IOException {
        // MATRIMONY is a live privilege prefix, not an application code, in this catalogue.
        assertThat(com.heuristiq.identity.application.catalogue.ApplicationCatalogue.codes())
                .doesNotContain("MATRIMONY", "PASUPU_KUNKUMA_MATRIMONY");
        List<String> offenders;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith(Path.of("application", "catalogue", "ApplicationCatalogue.java")))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains("\"MATRIMONY\"") || source.contains("\"PASUPU_KUNKUMA_MATRIMONY\"");
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(Path::toString)
                    .toList();
        }
        assertThat(offenders).isEmpty();
    }
}
