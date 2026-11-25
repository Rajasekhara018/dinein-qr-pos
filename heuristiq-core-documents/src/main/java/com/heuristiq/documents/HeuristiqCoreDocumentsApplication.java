package com.heuristiq.documents;

import com.heuristiq.documents.config.DmsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * JPA/DataSource autoconfiguration is excluded here because it is wired manually and
 * conditionally in {@link com.heuristiq.documents.config.PostgresPersistenceConfig} only when
 * {@code railcab.dms.persistence.provider=postgres}. Without this exclusion, Boot would try
 * to build a DataSource on startup for every deployment just because the Postgres driver is
 * on the classpath, breaking the default MongoDB-backed deployment.
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class
})
@EnableScheduling
@EnableConfigurationProperties(DmsProperties.class)
public class HeuristiqCoreDocumentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(HeuristiqCoreDocumentsApplication.class, args);
    }
}
