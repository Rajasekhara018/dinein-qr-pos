package com.heuristiq.uam.biometric.config;

import com.heuristiq.uam.audit.AuditLogServiceUseCase;
import com.heuristiq.uam.auth.AuthServiceUseCase;
import com.heuristiq.uam.biometric.adapter.out.AuditLogBiometricAudit;
import com.heuristiq.uam.biometric.adapter.out.AuthServiceBiometricSignIn;
import com.heuristiq.uam.biometric.adapter.out.JdbcBiometricAttemptStore;
import com.heuristiq.uam.biometric.adapter.out.JdbcBiometricChallengeStore;
import com.heuristiq.uam.biometric.adapter.out.JdbcBiometricCredentialStore;
import com.heuristiq.uam.biometric.adapter.out.MongoBiometricAttemptStore;
import com.heuristiq.uam.biometric.adapter.out.MongoBiometricChallengeStore;
import com.heuristiq.uam.biometric.adapter.out.MongoBiometricCredentialStore;
import com.heuristiq.uam.biometric.adapter.out.UamAccountReauthentication;
import com.heuristiq.uam.biometric.application.BiometricService;
import com.heuristiq.uam.biometric.application.BiometricSettings;
import com.heuristiq.uam.biometric.application.port.BiometricAttemptStore;
import com.heuristiq.uam.biometric.application.port.BiometricChallengeStore;
import com.heuristiq.uam.biometric.application.port.BiometricCredentialStore;
import com.heuristiq.uam.lockout.LoginAttemptServiceUseCase;
import com.heuristiq.uam.session.repository.UserSessionRepository;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.users.repository.UamUserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wiring of biometric sign-in. The credentials, challenges and attempt counters live in UAM's own database (Postgres tables
 * of migration V43, or Mongo collections of the same names) so every UAM instance shares them. Uses the UTC system clock
 * (UAM has no shared Clock bean). The one {@link BiometricService} bean is both the self-service/sign-in use case and the
 * revocation use case other features call.
 */
@Configuration
@EnableConfigurationProperties(BiometricProperties.class)
public class BiometricConfig {

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
    BiometricCredentialStore jdbcBiometricCredentialStore(JdbcTemplate jdbc) {
        return new JdbcBiometricCredentialStore(jdbc);
    }

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
    BiometricChallengeStore jdbcBiometricChallengeStore(JdbcTemplate jdbc) {
        return new JdbcBiometricChallengeStore(jdbc);
    }

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
    BiometricAttemptStore jdbcBiometricAttemptStore(JdbcTemplate jdbc) {
        return new JdbcBiometricAttemptStore(jdbc);
    }

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
    BiometricCredentialStore mongoBiometricCredentialStore(MongoTemplate mongo) {
        return new MongoBiometricCredentialStore(mongo);
    }

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
    BiometricChallengeStore mongoBiometricChallengeStore(MongoTemplate mongo) {
        return new MongoBiometricChallengeStore(mongo);
    }

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
    BiometricAttemptStore mongoBiometricAttemptStore(MongoTemplate mongo) {
        return new MongoBiometricAttemptStore(mongo);
    }

    @Bean
    BiometricService biometricService(BiometricCredentialStore credentials, BiometricChallengeStore challenges,
                                      BiometricAttemptStore attempts, AuditLogServiceUseCase auditLogs, AuthServiceUseCase auth,
                                      UamUserRepository users, UserSessionRepository sessions,
                                      UserCredentialServiceUseCase userCredentials, LoginAttemptServiceUseCase loginAttempts,
                                      BiometricProperties properties) {
        return new BiometricService(credentials, challenges, attempts, new AuditLogBiometricAudit(auditLogs),
                new AuthServiceBiometricSignIn(users, auth),
                new UamAccountReauthentication(sessions, userCredentials, loginAttempts, users, ZoneId.systemDefault()),
                new BiometricSettings(properties.reauthWindow(), properties.challengeTtl(), properties.inactivityExpiry(),
                        properties.maxFailedAttempts(), properties.attemptWindow()),
                Clock.systemUTC(), new SecureRandom());
    }
}
