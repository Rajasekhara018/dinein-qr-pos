package com.heuristiq.identity.organization.legalprofile.application;

import com.heuristiq.identity.organization.legalprofile.application.port.LegalOrganizationDirectory;
import com.heuristiq.identity.organization.legalprofile.application.port.LegalOrganizationDirectory.Organization;
import com.heuristiq.identity.organization.legalprofile.application.port.LegalProfileAudit;
import com.heuristiq.identity.organization.legalprofile.application.port.LegalProfileStore;
import com.heuristiq.identity.organization.legalprofile.domain.LegalProfile;
import com.heuristiq.identity.organization.legalprofile.domain.LegalProfileDraft;
import com.heuristiq.identity.organization.legalprofile.domain.LegalProfileException;
import com.heuristiq.identity.organization.legalprofile.domain.LegalProfileValidator;
import com.heuristiq.identity.organization.legalprofile.domain.StoredLegalProfile;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps an organization's legal profile. The legal name is the organization record's own field (read and written through
 * the directory); everything else is in the profile store under an optimistic-lock version. The profile is written first
 * (that is where a stale version is refused), then the legal name, so a refused save changes nothing. Refusals are thrown
 * as {@link LegalProfileException}.
 */
public class LegalProfileService implements LegalProfileUseCase {

    private final LegalOrganizationDirectory organizations;
    private final LegalProfileStore store;
    private final LegalProfileAudit audit;
    private final Clock clock;

    public LegalProfileService(LegalOrganizationDirectory organizations, LegalProfileStore store, LegalProfileAudit audit, Clock clock) {
        this.organizations = organizations;
        this.store = store;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public LegalProfileView view(String organizationId) {
        Organization organization = load(organizationId);
        return toView(organization, store.find(organization.id()));
    }

    @Override
    public LegalProfileView replace(String organizationId, LegalProfileDraft draft, Long expectedVersion, String actorUserId) {
        Organization organization = load(organizationId);
        LegalProfile next = LegalProfileValidator.validate(draft);
        Optional<StoredLegalProfile> current = store.find(organization.id());
        Long storedVersion = current.map(StoredLegalProfile::version).orElse(null);
        if (!Objects.equals(storedVersion, expectedVersion)) {
            throw LegalProfileException.staleVersion();
        }
        LegalProfile before = current.map(c -> c.profile().withLegalName(organization.legalName()))
                .orElseGet(() -> LegalProfile.empty(organization.legalName()));
        if (current.isPresent() && before.equals(next)) {
            return toView(organization, current);
        }
        StoredLegalProfile saved = store.save(organization.id(), next.withLegalName(null), expectedVersion, actorUserId, clock.instant());
        if (!Objects.equals(organization.legalName(), next.legalName())) {
            organizations.changeLegalName(organization.id(), next.legalName());
        }
        audit.profileChanged(organization.id(), actorUserId, before.toAuditMap(), next.toAuditMap());
        return new LegalProfileView(organization.id(), saved.profile().withLegalName(next.legalName()), saved.version(),
                saved.updatedAt(), saved.updatedBy());
    }

    private Organization load(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            throw LegalProfileException.organizationNotFound();
        }
        return organizations.find(organizationId.trim()).orElseThrow(LegalProfileException::organizationNotFound);
    }

    private static LegalProfileView toView(Organization organization, Optional<StoredLegalProfile> stored) {
        return stored
                .map(s -> new LegalProfileView(organization.id(), s.profile().withLegalName(organization.legalName()), s.version(),
                        s.updatedAt(), s.updatedBy()))
                .orElseGet(() -> new LegalProfileView(organization.id(), LegalProfile.empty(organization.legalName()), null, null, null));
    }
}
