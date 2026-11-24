package com.heuristiq.uam.persistence;

import com.heuristiq.uam.testsupport.AdapterMappingVerifier;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises every Mongo and JPA repository adapter through its port against mocked stores and checks that
 * documents/entities map to domain objects (and back, for save) without losing or altering a field.
 */
class RepositoryAdaptersMappingTest {

    private static final String P = "com.heuristiq.uam.";

    private static final List<String> ADAPTERS = List.of(
            "application.access.repository.jpa.ApplicationAccessJpaRepositoryAdapter",
            "application.access.repository.mongo.ApplicationAccessMongoRepositoryAdapter",
            "application.client.repository.jpa.ApiClientJpaRepositoryAdapter",
            "application.client.repository.mongo.ApiClientMongoRepositoryAdapter",
            "application.repository.jpa.ApplicationJpaRepositoryAdapter",
            "application.repository.mongo.ApplicationMongoRepositoryAdapter",
            "device.repository.jpa.UserDeviceJpaRepositoryAdapter",
            "device.repository.mongo.UserDeviceMongoRepositoryAdapter",
            "lockout.repository.jpa.UserLoginSecurityJpaRepositoryAdapter",
            "lockout.repository.mongo.UserLoginSecurityMongoRepositoryAdapter",
            "mfa.repository.jpa.UserMfaJpaRepositoryAdapter",
            "mfa.repository.mongo.UserMfaMongoRepositoryAdapter",
            "organization.repository.jpa.TenantJpaRepositoryAdapter",
            "organization.repository.mongo.TenantMongoRepositoryAdapter",
            "passwordsHistory.repository.jpa.PasswordHistoryJpaRepositoryAdapter",
            "passwordsHistory.repository.mongo.PasswordHistoryMongoRepositoryAdapter",
            "permissions.repository.jpa.PermissionJpaRepositoryAdapter",
            "permissions.repository.mongo.PermissionMongoRepositoryAdapter",
            "refreshToken.repository.jpa.RefreshTokenJpaRepositoryAdapter",
            "refreshToken.repository.mongo.RefreshTokenMongoRepositoryAdapter",
            "securitypolicy.repository.jpa.SecurityPolicyJpaRepositoryAdapter",
            "securitypolicy.repository.mongo.SecurityPolicyMongoRepositoryAdapter",
            "session.repository.jpa.UserSessionJpaRepositoryAdapter",
            "session.repository.mongo.UserSessionMongoRepositoryAdapter",
            "usercredentials.repository.jpa.UserCredentialJpaRepositoryAdapter",
            "usercredentials.repository.mongo.UserCredentialMongoRepositoryAdapter",
            "userpreference.repository.jpa.UserPreferenceJpaRepositoryAdapter",
            "userpreference.repository.mongo.UserPreferenceMongoRepositoryAdapter",
            "organization.subscription.repository.jpa.TenantApplicationJpaRepositoryAdapter",
            "organization.subscription.repository.mongo.TenantApplicationMongoRepositoryAdapter",
            "organization.membership.repository.jpa.TenantMembershipJpaRepositoryAdapter",
            "organization.membership.repository.mongo.TenantMembershipMongoRepositoryAdapter",
            "role.repository.jpa.UamRoleJpaRepositoryAdapter",
            "role.repository.mongo.UamRoleMongoRepositoryAdapter",
            "users.repository.jpa.UamUserJpaRepositoryAdapter",
            "users.repository.mongo.UamUserMongoRepositoryAdapter");

    @TestFactory
    Stream<DynamicTest> everyAdapterMapsStoredShapeToDomainWithoutLosingFields() {
        return ADAPTERS.stream().map(name -> DynamicTest.dynamicTest(name, () -> {
            AdapterMappingVerifier.Outcome outcome = AdapterMappingVerifier.verify(Class.forName(P + name), Set.of());
            assertThat(outcome.methodsInvoked()).isPositive();
        }));
    }
}
