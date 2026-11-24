package com.heuristq.dinein.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, Long> {

    Optional<PushSubscriptionEntity> findByProviderAndToken(String provider, String token);

    List<PushSubscriptionEntity> findByOwnerTypeAndOwnerIdAndActiveTrue(PushOwnerType ownerType, String ownerId);

    List<PushSubscriptionEntity> findByOwnerTypeAndOwnerIdInAndActiveTrue(PushOwnerType ownerType,
                                                                         Collection<String> ownerIds);
}
