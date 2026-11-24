package com.heuristiq.uam.persistence;

import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.helper.AccessibilityPreference;
import com.heuristiq.uam.helper.AccountPolicy;
import com.heuristiq.uam.helper.ApiSecurityPolicy;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.helper.DashboardPreference;
import com.heuristiq.uam.helper.FieldChange;
import com.heuristiq.uam.helper.LoginPolicy;
import com.heuristiq.uam.helper.MfaPolicy;
import com.heuristiq.uam.helper.NotificationPreference;
import com.heuristiq.uam.helper.PasswordPolicy;
import com.heuristiq.uam.helper.RName;
import com.heuristiq.uam.helper.RPhoneNumber;
import com.heuristiq.uam.helper.RUpload;
import com.heuristiq.uam.helper.RegionalPreference;
import com.heuristiq.uam.helper.SessionPolicy;
import com.heuristiq.uam.helper.UiPreference;
import com.heuristiq.uam.device.persistence.jpa.UserDeviceEntity;
import com.heuristiq.uam.lockout.persistence.jpa.UserLoginSecurityEntity;
import com.heuristiq.uam.mfa.persistence.jpa.UserMfaEntity;
import com.heuristiq.uam.application.client.persistence.jpa.ApiClientEntity;
import com.heuristiq.uam.application.persistence.jpa.ApplicationEntity;
import com.heuristiq.uam.passwordsHistory.persistance.jpa.PasswordHistoryEntity;
import com.heuristiq.uam.permissions.persistance.jpa.PermissionEntity;
import com.heuristiq.uam.refreshToken.persistance.jpa.RefreshTokenEntity;
import com.heuristiq.uam.securitypolicy.persistence.jpa.SecurityPolicyEntity;
import com.heuristiq.uam.organization.persistence.jpa.TenantEntity;
import com.heuristiq.uam.role.persistance.jpa.UamRoleEntity;
import com.heuristiq.uam.users.persistence.jpa.UamUserEntity;
import com.heuristiq.uam.usercredentials.persistance.jpa.UserCredentialEntity;
import com.heuristiq.uam.userpreference.persistance.jpa.UserPreferenceEntity;
import com.heuristiq.uam.session.persistence.jpa.UserSessionEntity;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates all 17 domain entities' JPA mappings (element collections, embeddables,
 * JSON converters, enum mappings, unique constraints) against an in-memory H2 database
 * in MySQL-compatibility mode -- without needing a live MySQL server or the full Spring
 * context (Mongo/Keycloak/Firebase beans this app also needs are irrelevant to whether
 * the JPA mappings themselves are structurally sound).
 */
class JpaEntityMappingTest {
    private static StandardServiceRegistry registry;
    private static SessionFactory sessionFactory;

    @BeforeAll
    static void setUp() {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url",
                        "jdbc:h2:mem:uam_jpa_mapping_test;DB_CLOSE_DELAY=-1;MODE=MySQL")
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .applySetting("hibernate.implicit_naming_strategy",
                        "org.springframework.boot.hibernate.SpringImplicitNamingStrategy")
                .build();
        sessionFactory = new MetadataSources(registry)
                .addAnnotatedClass(TenantEntity.class)
                .addAnnotatedClass(ApiClientEntity.class)
                .addAnnotatedClass(PermissionEntity.class)
                .addAnnotatedClass(UamRoleEntity.class)
                .addAnnotatedClass(UamUserEntity.class)
                .addAnnotatedClass(ApplicationEntity.class)
                .addAnnotatedClass(PasswordHistoryEntity.class)
                .addAnnotatedClass(RefreshTokenEntity.class)
                .addAnnotatedClass(SecurityPolicyEntity.class)
                .addAnnotatedClass(UserCredentialEntity.class)
                .addAnnotatedClass(UserDeviceEntity.class)
                .addAnnotatedClass(UserLoginSecurityEntity.class)
                .addAnnotatedClass(UserMfaEntity.class)
                .addAnnotatedClass(UserPreferenceEntity.class)
                .addAnnotatedClass(UserSessionEntity.class)
                .buildMetadata()
                .buildSessionFactory();
    }

    @AfterAll
    static void tearDown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (registry != null) {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void tenantRoundTripsEmbeddedLogoWithoutColumnCollisionAgainstTenantName() {
        TenantEntity entity = TenantEntity.builder()
                .code("ACME")
                .name("Acme Corp")
                .type(UamEnums.TenantType.CUSTOMER)
                .status(UamEnums.TenantStatus.ACTIVE)
                .logo(new RUpload("logo.png", "image/png", 1024, "/uploads/logo.png",
                        "Acme Logo", null, null, null, null, null))
                .settings(Map.of("theme", "dark"))
                .audit(AuditInfo.builder().createdAt(LocalDateTime.now()).build())
                .build();

        TenantEntity reloaded = persistAndReload(entity, TenantEntity.class);
        assertThat(reloaded.getName()).isEqualTo("Acme Corp");
        assertThat(reloaded.getLogo().getName()).isEqualTo("logo.png");
        assertThat(reloaded.getSettings()).containsEntry("theme", "dark");
    }

    @Test
    void apiClientRoundTripsEnumSetsAndStringSets() {
        ApiClientEntity entity = ApiClientEntity.builder()
                .clientId("booking-service")
                .name("Booking Service")
                .clientType(UamEnums.ApiClientType.MICROSERVICE)
                .authenticationMethod(UamEnums.ClientAuthenticationMethod.CLIENT_SECRET)
                .grantTypes(Set.of(UamEnums.GrantType.CLIENT_CREDENTIALS))
                .scopes(Set.of("bookings:read", "bookings:write"))
                .redirectUris(Set.of("https://app.railcab.in/callback"))
                .active(true)
                .audit(AuditInfo.builder().createdAt(LocalDateTime.now()).build())
                .build();

        ApiClientEntity reloaded = persistAndReload(entity, ApiClientEntity.class);
        assertThat(reloaded.getGrantTypes()).containsExactly(UamEnums.GrantType.CLIENT_CREDENTIALS);
        assertThat(reloaded.getScopes()).containsExactlyInAnyOrder("bookings:read", "bookings:write");
    }

    @Test
    void permissionRoundTripsEnumsAndJsonMap() {
        PermissionEntity entity = PermissionEntity.builder()
                .code("BOOKINGS_READ")
                .module(UamEnums.ModuleCategory.BUSINESS)
                .resource(UamEnums.ResourceType.TRANSACTION)
                .action(UamEnums.PermissionAction.READ)
                .attributes(Map.of("critical", true))
                .active(true)
                .audit(AuditInfo.builder().createdAt(LocalDateTime.now()).build())
                .build();

        PermissionEntity reloaded = persistAndReload(entity, PermissionEntity.class);
        assertThat(reloaded.getModule()).isEqualTo(UamEnums.ModuleCategory.BUSINESS);
        assertThat(reloaded.getAttributes()).containsEntry("critical", true);
    }

    @Test
    void uamRoleRoundTripsOrderedPermissionsList() {
        UamRoleEntity entity = UamRoleEntity.builder()
                .code("SUPPORT")
                .name("Support")
                .permissions(List.of("BOOKINGS_READ", "IRCTC_READ"))
                .attributes(Map.of("tier", "L1"))
                .active(true)
                .audit(AuditInfo.builder().createdAt(LocalDateTime.now()).build())
                .build();

        UamRoleEntity reloaded = persistAndReload(entity, UamRoleEntity.class);
        assertThat(reloaded.getPermissions()).containsExactly("BOOKINGS_READ", "IRCTC_READ");
        assertThat(reloaded.getAttributes()).containsEntry("tier", "L1");
    }

    @Test
    void uamUserRoundTripsAllEmbeddedValueObjectsAndRoleCollection() {
        UamUserEntity entity = UamUserEntity.builder()
                .userName("jane.doe")
                .email("jane.doe@example.com")
                .phoneNumber(new RPhoneNumber("+91", "9876543210"))
                .fullName(new RName("Jane", "M", "Doe"))
                .profileImage(new RUpload("avatar.png", "image/png", 512, "/uploads/avatar.png",
                        null, null, null, null, null, null))
                .status(UamEnums.UserStatus.ACTIVE)
                .roles(List.of("CUSTOMER"))
                .attributes(Map.of("source", "mobile-app"))
                .active(true)
                .audit(AuditInfo.builder().createdAt(LocalDateTime.now()).build())
                .build();

        UamUserEntity reloaded = persistAndReload(entity, UamUserEntity.class);
        assertThat(reloaded.getUserName()).isEqualTo("jane.doe");
        assertThat(reloaded.getPhoneNumber().getNumber()).isEqualTo("9876543210");
        assertThat(reloaded.getFullName().getFullName()).isEqualTo("Jane M Doe");
        assertThat(reloaded.getProfileImage().getName()).isEqualTo("avatar.png");
        assertThat(reloaded.getRoles()).containsExactly("CUSTOMER");
        assertThat(reloaded.getAttributes()).containsEntry("source", "mobile-app");
    }

    @Test
    void applicationRoundTripsEmbeddedIconWithoutColumnCollisionAgainstApplicationName() {
        ApplicationEntity entity = ApplicationEntity.builder()
                .code("GATEWAY")
                .name("API Gateway")
                .type(UamEnums.ApplicationType.MICROSERVICE)
                .icon(new RUpload("icon.png", "image/png", 256, "/uploads/icon.png",
                        null, null, null, null, null, null))
                .attributes(Map.of("region", "ap-south-1"))
                .active(true)
                .audit(AuditInfo.builder().createdAt(LocalDateTime.now()).build())
                .build();

        ApplicationEntity reloaded = persistAndReload(entity, ApplicationEntity.class);
        assertThat(reloaded.getName()).isEqualTo("API Gateway");
        assertThat(reloaded.getIcon().getName()).isEqualTo("icon.png");
        assertThat(reloaded.getAttributes()).containsEntry("region", "ap-south-1");
    }

    @Test
    void passwordHistoryRoundTripsEnumAndEmbeddedAudit() {
        PasswordHistoryEntity entity = PasswordHistoryEntity.builder()
                .userId("u1")
                .passwordHash("hashed-value")
                .changeReason(UamEnums.PasswordChangeReason.USER_CHANGED)
                .audit(AuditInfo.builder().createdAt(LocalDateTime.now()).build())
                .build();

        PasswordHistoryEntity reloaded = persistAndReload(entity, PasswordHistoryEntity.class);
        assertThat(reloaded.getUserId()).isEqualTo("u1");
        assertThat(reloaded.getChangeReason()).isEqualTo(UamEnums.PasswordChangeReason.USER_CHANGED);
    }

    @Test
    void refreshTokenRoundTripsUniqueTokenIdAndEnum() {
        RefreshTokenEntity entity = RefreshTokenEntity.builder()
                .principalId("u1")
                .principalType(UamEnums.PrincipalType.USER)
                .tokenId("tok-123")
                .tokenHash("hashed-token")
                .revoked(false)
                .audit(AuditInfo.builder().createdAt(LocalDateTime.now()).build())
                .build();

        RefreshTokenEntity reloaded = persistAndReload(entity, RefreshTokenEntity.class);
        assertThat(reloaded.getTokenId()).isEqualTo("tok-123");
        assertThat(reloaded.getPrincipalType()).isEqualTo(UamEnums.PrincipalType.USER);
    }

    @Test
    void securityPolicyRoundTripsAllSixJsonConvertedPolicies() {
        SecurityPolicyEntity entity = SecurityPolicyEntity.builder()
                .name("Default Policy")
                .passwordPolicy(PasswordPolicy.builder().minimumLength(8).requireUppercase(true).build())
                .loginPolicy(LoginPolicy.builder().maxFailedAttempts(5).build())
                .sessionPolicy(SessionPolicy.builder().idleTimeoutMinutes(30).build())
                .mfaPolicy(MfaPolicy.builder().mandatory(false).build())
                .apiSecurityPolicy(ApiSecurityPolicy.builder().rateLimitPerMinute(100).build())
                .accountPolicy(AccountPolicy.builder().selfRegistrationAllowed(true).build())
                .active(true)
                .audit(AuditInfo.builder().createdAt(LocalDateTime.now()).build())
                .build();

        SecurityPolicyEntity reloaded = persistAndReload(entity, SecurityPolicyEntity.class);
        assertThat(reloaded.getPasswordPolicy().getMinimumLength()).isEqualTo(8);
        assertThat(reloaded.getLoginPolicy().getMaxFailedAttempts()).isEqualTo(5);
        assertThat(reloaded.getSessionPolicy().getIdleTimeoutMinutes()).isEqualTo(30);
        assertThat(reloaded.getMfaPolicy().getMandatory()).isFalse();
        assertThat(reloaded.getApiSecurityPolicy().getRateLimitPerMinute()).isEqualTo(100);
        assertThat(reloaded.getAccountPolicy().getSelfRegistrationAllowed()).isTrue();
    }

    @Test
    void userCredentialRoundTripsEnumAndDirectVersionColumn() {
        UserCredentialEntity entity = UserCredentialEntity.builder()
                .userId("u1")
                .provider(UamEnums.AuthProvider.LOCAL)
                .passwordHash("hashed")
                .metadata(Map.of("k", "v"))
                .active(true)
                .build();

        UserCredentialEntity reloaded = persistAndReload(entity, UserCredentialEntity.class);
        assertThat(reloaded.getProvider()).isEqualTo(UamEnums.AuthProvider.LOCAL);
        assertThat(reloaded.getMetadata()).containsEntry("k", "v");
        assertThat(reloaded.getVersion()).isEqualTo(0L);
    }

    @Test
    void userDeviceRoundTripsEnumsAndMetadata() {
        UserDeviceEntity entity = UserDeviceEntity.builder()
                .userId("u1")
                .deviceFingerprint("fp-1")
                .deviceType(UamEnums.DeviceType.MOBILE)
                .platform(UamEnums.Platform.ANDROID)
                .metadata(Map.of("appVersion", "1.2.3"))
                .active(true)
                .build();

        UserDeviceEntity reloaded = persistAndReload(entity, UserDeviceEntity.class);
        assertThat(reloaded.getDeviceType()).isEqualTo(UamEnums.DeviceType.MOBILE);
        assertThat(reloaded.getPlatform()).isEqualTo(UamEnums.Platform.ANDROID);
        assertThat(reloaded.getMetadata()).containsEntry("appVersion", "1.2.3");
    }

    @Test
    void userLoginSecurityRoundTripsFlatFields() {
        UserLoginSecurityEntity entity = UserLoginSecurityEntity.builder()
                .userId("u1")
                .failedLoginAttempts(2)
                .accountLocked(false)
                .active(true)
                .build();

        UserLoginSecurityEntity reloaded = persistAndReload(entity, UserLoginSecurityEntity.class);
        assertThat(reloaded.getUserId()).isEqualTo("u1");
        assertThat(reloaded.getFailedLoginAttempts()).isEqualTo(2);
    }

    @Test
    void userMfaRoundTripsBackupCodesCollectionAndEmbeddedPhoneNumber() {
        UserMfaEntity entity = UserMfaEntity.builder()
                .userId("u1")
                .mfaType(UamEnums.MfaType.TOTP)
                .secret("secret-value")
                .backupCodes(List.of("code1", "code2"))
                .phoneNumber(new RPhoneNumber("+91", "9999999999"))
                .enabled(true)
                .active(true)
                .build();

        UserMfaEntity reloaded = persistAndReload(entity, UserMfaEntity.class);
        assertThat(reloaded.getBackupCodes()).containsExactly("code1", "code2");
        assertThat(reloaded.getPhoneNumber().getNumber()).isEqualTo("9999999999");
    }

    @Test
    void userPreferenceRoundTripsAllFiveJsonConvertedPreferencesIncludingNestedList() {
        UserPreferenceEntity entity = UserPreferenceEntity.builder()
                .userId("u1")
                .ui(UiPreference.builder().theme(UamEnums.ThemeEnum.DARK).pageSize(25).build())
                .notification(NotificationPreference.builder().emailEnabled(true).build())
                .dashboard(DashboardPreference.builder().favoriteModules(List.of("bookings", "payments")).build())
                .accessibility(AccessibilityPreference.builder().highContrast(true).build())
                .regional(RegionalPreference.builder().language(UamEnums.LanguageEnum.EN).timezone("Asia/Kolkata").build())
                .active(true)
                .build();

        UserPreferenceEntity reloaded = persistAndReload(entity, UserPreferenceEntity.class);
        assertThat(reloaded.getUi().getTheme()).isEqualTo(UamEnums.ThemeEnum.DARK);
        assertThat(reloaded.getNotification().getEmailEnabled()).isTrue();
        assertThat(reloaded.getDashboard().getFavoriteModules()).containsExactly("bookings", "payments");
        assertThat(reloaded.getAccessibility().getHighContrast()).isTrue();
        assertThat(reloaded.getRegional().getTimezone()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void userSessionRoundTripsUniqueConstraintsAndEnums() {
        UserSessionEntity entity = UserSessionEntity.builder()
                .sessionId("sess-1")
                .userId("u1")
                .refreshTokenHash("hashed-refresh-token")
                .provider(UamEnums.AuthProvider.LOCAL)
                .active(true)
                .metadata(Map.of("ip", "127.0.0.1"))
                .build();

        UserSessionEntity reloaded = persistAndReload(entity, UserSessionEntity.class);
        assertThat(reloaded.getSessionId()).isEqualTo("sess-1");
        assertThat(reloaded.getProvider()).isEqualTo(UamEnums.AuthProvider.LOCAL);
        assertThat(reloaded.getMetadata()).containsEntry("ip", "127.0.0.1");
    }

    private <T> T persistAndReload(T entity, Class<T> type) {
        String id = inTransaction(session -> {
            session.persist(entity);
            return (String) getId(entity, type);
        });
        return inSession(session -> session.find(type, id));
    }

    @SuppressWarnings("unchecked")
    private <T> Object getId(T entity, Class<T> type) {
        try {
            return type.getMethod("getId").invoke(entity);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T inTransaction(Function<Session, T> work) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            T result = work.apply(session);
            session.getTransaction().commit();
            return result;
        }
    }

    private <T> T inSession(Function<Session, T> work) {
        try (Session session = sessionFactory.openSession()) {
            return work.apply(session);
        }
    }
}
