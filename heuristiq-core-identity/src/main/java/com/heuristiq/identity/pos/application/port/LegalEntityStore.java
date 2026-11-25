package com.heuristiq.identity.pos.application.port;

import com.heuristiq.identity.pos.domain.LegalEntity;
import java.util.List;
import java.util.Optional;

/** Persistence of a tenant's legal entities. Every call is scoped to one tenant. */
public interface LegalEntityStore {

    Optional<LegalEntity> find(String tenantId, String id);

    List<LegalEntity> list(String tenantId);

    /** Stores a new legal entity exactly as given. */
    LegalEntity insert(LegalEntity entity);

    /**
     * Replaces the stored entity with {@code entity} (which carries the new version) when the stored version equals
     * {@code expectedVersion}; otherwise throws {@code PosException.conflict("pos.staleVersion", "version")}.
     */
    LegalEntity update(LegalEntity entity, long expectedVersion);
}
