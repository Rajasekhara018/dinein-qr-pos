package com.railcab.dms.config;

import com.railcab.dms.common.DmsCommonConstants;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.hibernate5.HibernateExceptionTranslator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Wires JPA/PostgreSQL only when explicitly selected. Deliberately NOT relying on Spring
 * Boot's default DataSource/JPA autoconfiguration (excluded in RailCabDmsApplication) so the
 * default MongoDB deployment never needs a reachable Postgres instance just because the
 * driver is on the classpath.
 */
@Configuration
@EnableTransactionManagement
@ConditionalOnProperty(prefix = DmsCommonConstants.PERSISTENCE_PROPERTY_PREFIX, name = DmsCommonConstants.PERSISTENCE_PROVIDER_PROPERTY, havingValue = DmsCommonConstants.PERSISTENCE_POSTGRES)
@EnableJpaRepositories(basePackages = {
        "com.railcab.dms.document.persistence.postgres",
        "com.railcab.dms.document.audit.postgres",
        "com.railcab.dms.documenttype.adapter.out.postgres",
        "com.railcab.dms.centralaudit.postgres"
})
public class PostgresPersistenceConfig {

    @Bean
    public DataSource dataSource(DmsProperties properties) {
        DmsProperties.Persistence.Postgres postgres = properties.getPersistence().getPostgres();
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setMaximumPoolSize(postgres.getMaximumPoolSize());
        dataSource.setMinimumIdle(postgres.getMinimumIdle());
        dataSource.setConnectionTimeout(postgres.getConnectionTimeoutMs());
        dataSource.setIdleTimeout(postgres.getIdleTimeoutMs());
        dataSource.setMaxLifetime(postgres.getMaxLifetimeMs());
        dataSource.setLeakDetectionThreshold(postgres.getLeakDetectionThresholdMs());
        return dataSource;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(
                "com.railcab.dms.document.persistence.postgres",
                "com.railcab.dms.document.audit.postgres",
                "com.railcab.dms.documenttype.adapter.out.postgres",
                "com.railcab.dms.centralaudit.postgres"
        );
        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "update"));
        return factory;
    }

    @Bean
    public PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }

    @Bean
    public HibernateExceptionTranslator hibernateExceptionTranslator() {
        return new HibernateExceptionTranslator();
    }
}
