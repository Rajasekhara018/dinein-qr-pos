package com.heuristiq.uam.organization.legalprofile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.heuristiq.uam.enums.UamEnums.TenantStatus;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.legalprofile.adapter.out.TenantLegalOrganizationDirectory;
import com.heuristiq.uam.organization.legalprofile.application.LegalProfileUseCase.LegalProfileView;
import com.heuristiq.uam.organization.legalprofile.application.port.LegalProfileAudit;
import com.heuristiq.uam.organization.legalprofile.domain.AddressKind;
import com.heuristiq.uam.organization.legalprofile.domain.LegalEntityType;
import com.heuristiq.uam.organization.legalprofile.domain.LegalProfile;
import com.heuristiq.uam.organization.legalprofile.domain.LegalProfileConstants;
import com.heuristiq.uam.organization.legalprofile.domain.LegalProfileDraft;
import com.heuristiq.uam.organization.legalprofile.domain.LegalProfileDraft.AddressDraft;
import com.heuristiq.uam.organization.legalprofile.domain.LegalProfileDraft.TaxIdDraft;
import com.heuristiq.uam.organization.legalprofile.domain.LegalProfileException;
import com.heuristiq.uam.organization.legalprofile.domain.StoredLegalProfile;
import com.heuristiq.uam.organization.legalprofile.domain.TaxId;
import com.heuristiq.uam.organization.legalprofile.domain.TaxIdType;
import com.heuristiq.uam.organization.legalprofile.support.InMemoryLegalProfileStore;
import com.heuristiq.uam.organization.repository.TenantRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The legal profile is kept per organization, versioned, audited, and its legal name lives on the tenant record. */
class LegalProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final Map<String, Tenant> tenantRows = new HashMap<>();
    private final List<Tenant> savedTenants = new ArrayList<>();
    private InMemoryLegalProfileStore store;
    private FakeAudit audit;
    private LegalProfileService service;

    @BeforeEach
    void setUp() {
        TenantRepository tenants = mock(TenantRepository.class);
        when(tenants.findById(any())).thenAnswer(i -> Optional.ofNullable(copy(tenantRows.get(i.<String>getArgument(0)))));
        when(tenants.save(any())).thenAnswer(i -> {
            Tenant t = i.getArgument(0);
            savedTenants.add(t);
            tenantRows.put(t.getId(), copy(t));
            return t;
        });
        tenantRows.put("acme", Tenant.builder().id("acme").code("ACME").name("Acme").legalName("Acme Old Name Pvt Ltd")
                .country("IN").currency("INR").timeZone("Asia/Kolkata").status(TenantStatus.ACTIVE).maxUsers(50)
                .settings(Map.of("dataScope", "DEPARTMENT")).build());
        tenantRows.put("globex", Tenant.builder().id("globex").code("GLOBEX").name("Globex").build());
        store = new InMemoryLegalProfileStore();
        audit = new FakeAudit();
        service = new LegalProfileService(new TenantLegalOrganizationDirectory(tenants), store, audit, CLOCK);
    }

    private static Tenant copy(Tenant t) {
        if (t == null) {
            return null;
        }
        return Tenant.builder().id(t.getId()).code(t.getCode()).name(t.getName()).legalName(t.getLegalName()).country(t.getCountry())
                .currency(t.getCurrency()).timeZone(t.getTimeZone()).status(t.getStatus()).maxUsers(t.getMaxUsers())
                .settings(t.getSettings()).build();
    }

    private static LegalProfileDraft draft(String legalName) {
        return new LegalProfileDraft("private_company", legalName, "Acme Rides", "U12345KA2020PTC123456", "in", true,
                List.of(new TaxIdDraft("PAN", "abcde 1234 f")),
                List.of(new AddressDraft("REGISTERED", "12 MG Road", null, "Bengaluru", "Karnataka", "560001", "IN")));
    }

    @Test
    void anOrganizationWithoutAProfileGetsAnEmptyOneWithItsLegalName() {
        LegalProfileView view = service.view("acme");

        assertThat(view.organizationId()).isEqualTo("acme");
        assertThat(view.profile()).isEqualTo(LegalProfile.empty("Acme Old Name Pvt Ltd"));
        assertThat(view.version()).isNull();
        assertThat(view.updatedAt()).isNull();
        assertThat(view.updatedBy()).isNull();
    }

    @Test
    void theFirstSaveStoresVersionOneWritesTheLegalNameToTheTenantAndIsAudited() {
        LegalProfileView view = service.replace("acme", draft("Acme Mobility Pvt Ltd"), null, "admin-1");

        assertThat(view.version()).isEqualTo(1L);
        assertThat(view.updatedAt()).isEqualTo(NOW);
        assertThat(view.updatedBy()).isEqualTo("admin-1");
        assertThat(view.profile().entityType()).isEqualTo(LegalEntityType.PRIVATE_COMPANY);
        assertThat(view.profile().legalName()).isEqualTo("Acme Mobility Pvt Ltd");
        assertThat(view.profile().taxIds()).containsExactly(new TaxId(TaxIdType.PAN, "ABCDE1234F"));
        assertThat(view.profile().addresses()).extracting(a -> a.kind()).containsExactly(AddressKind.REGISTERED);

        // The legal name is on the tenant; every other field of the tenant is as it was.
        Tenant tenant = tenantRows.get("acme");
        assertThat(tenant.getLegalName()).isEqualTo("Acme Mobility Pvt Ltd");
        assertThat(tenant.getCode()).isEqualTo("ACME");
        assertThat(tenant.getName()).isEqualTo("Acme");
        assertThat(tenant.getCountry()).isEqualTo("IN");
        assertThat(tenant.getCurrency()).isEqualTo("INR");
        assertThat(tenant.getTimeZone()).isEqualTo("Asia/Kolkata");
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.getMaxUsers()).isEqualTo(50);
        assertThat(tenant.getSettings()).containsEntry("dataScope", "DEPARTMENT");
        // The store does not keep a second copy of the legal name.
        assertThat(store.find("acme")).get().extracting(s -> s.profile().legalName()).isNull();

        assertThat(audit.events).hasSize(1);
        assertThat(audit.events.get(0).organizationId()).isEqualTo("acme");
        assertThat(audit.events.get(0).actor()).isEqualTo("admin-1");
        assertThat(audit.events.get(0).before()).containsEntry(LegalProfileConstants.FIELD_LEGAL_NAME, "Acme Old Name Pvt Ltd")
                .containsEntry(LegalProfileConstants.FIELD_ENTITY_TYPE, null);
        assertThat(audit.events.get(0).after()).containsEntry(LegalProfileConstants.FIELD_LEGAL_NAME, "Acme Mobility Pvt Ltd")
                .containsEntry(LegalProfileConstants.FIELD_ENTITY_TYPE, "PRIVATE_COMPANY");

        assertThat(service.view("acme").profile()).isEqualTo(view.profile());
    }

    @Test
    void anUpdateWithTheReadVersionReplacesTheProfileAndBumpsTheVersion() {
        service.replace("acme", draft("Acme Mobility Pvt Ltd"), null, "admin-1");

        LegalProfileView view = service.replace("acme", new LegalProfileDraft("LLP", "Acme Mobility Pvt Ltd", null, null, null, false,
                List.of(new TaxIdDraft("GSTIN", "29ABCDE1234F1Z5")), List.of()), 1L, "admin-2");

        assertThat(view.version()).isEqualTo(2L);
        assertThat(view.updatedBy()).isEqualTo("admin-2");
        assertThat(view.profile().entityType()).isEqualTo(LegalEntityType.LLP);
        // Tax ids and addresses are replaced as whole sets.
        assertThat(view.profile().taxIds()).containsExactly(new TaxId(TaxIdType.GSTIN, "29ABCDE1234F1Z5"));
        assertThat(view.profile().addresses()).isEmpty();
        // The legal name did not change, so the tenant was written only once (by the first save).
        assertThat(savedTenants).hasSize(1);
        assertThat(audit.events).hasSize(2);
    }

    @Test
    void sendingTheSameProfileAgainWritesNothing() {
        service.replace("acme", draft("Acme Mobility Pvt Ltd"), null, "admin-1");

        LegalProfileView view = service.replace("acme", draft(" Acme Mobility Pvt Ltd "), 1L, "admin-2");

        assertThat(view.version()).isEqualTo(1L);
        assertThat(view.updatedBy()).isEqualTo("admin-1");
        assertThat(audit.events).hasSize(1);
    }

    @Test
    void aStaleVersionIsAConflictAndChangesNothing() {
        service.replace("acme", draft("Acme Mobility Pvt Ltd"), null, "admin-1");
        service.replace("acme", draft("Acme Two Pvt Ltd"), 1L, "admin-1");

        assertThatThrownBy(() -> service.replace("acme", draft("Acme Three Pvt Ltd"), 1L, "admin-2"))
                .isInstanceOf(LegalProfileException.class)
                .hasMessage(LegalProfileConstants.ERROR_STALE_VERSION)
                .extracting(e -> ((LegalProfileException) e).kind()).isEqualTo(LegalProfileException.Kind.CONFLICT);
        assertThat(tenantRows.get("acme").getLegalName()).isEqualTo("Acme Two Pvt Ltd");
        assertThat(store.find("acme")).get().extracting(StoredLegalProfile::version).isEqualTo(2L);
    }

    @Test
    void aFirstSaveWhenAProfileAlreadyExistsIsAConflict() {
        service.replace("acme", draft("Acme Mobility Pvt Ltd"), null, "admin-1");

        assertThatThrownBy(() -> service.replace("acme", draft("Other"), null, "admin-2"))
                .isInstanceOf(LegalProfileException.class).hasMessage(LegalProfileConstants.ERROR_STALE_VERSION);
    }

    @Test
    void aVersionSentWhenNothingIsSavedIsAConflict() {
        assertThatThrownBy(() -> service.replace("acme", draft("Other"), 3L, "admin-2"))
                .isInstanceOf(LegalProfileException.class).hasMessage(LegalProfileConstants.ERROR_STALE_VERSION);
        assertThat(store.find("acme")).isEmpty();
    }

    @Test
    void aRaceLostAtTheStoreIsAConflictAndTheLegalNameIsNotWritten() {
        // Another instance saves between this request's read and its write.
        LegalProfileService racing = new LegalProfileService(new TenantLegalOrganizationDirectory(repositoryOver()), new InMemoryLegalProfileStore() {
            @Override
            public StoredLegalProfile save(String organizationId, LegalProfile profile, Long expectedVersion, String userId, Instant at) {
                putDirectly(organizationId, new StoredLegalProfile(LegalProfile.empty(null), 1L, at, "someone-else"));
                return super.save(organizationId, profile, expectedVersion, userId, at);
            }
        }, audit, CLOCK);

        assertThatThrownBy(() -> racing.replace("acme", draft("Acme Mobility Pvt Ltd"), null, "admin-1"))
                .isInstanceOf(LegalProfileException.class).hasMessage(LegalProfileConstants.ERROR_STALE_VERSION);
        assertThat(tenantRows.get("acme").getLegalName()).isEqualTo("Acme Old Name Pvt Ltd");
        assertThat(audit.events).isEmpty();
    }

    @Test
    void anInvalidProfileIsRefusedBeforeAnythingIsWritten() {
        assertThatThrownBy(() -> service.replace("acme", new LegalProfileDraft(null, null, null, null, null, null,
                List.of(new TaxIdDraft("PAN", "nope")), null), null, "admin-1"))
                .isInstanceOf(LegalProfileException.class)
                .hasMessage(LegalProfileConstants.ERROR_INVALID_TAX_ID);
        assertThat(store.find("acme")).isEmpty();
        assertThat(savedTenants).isEmpty();
    }

    @Test
    void clearingTheLegalNameClearsItOnTheTenant() {
        service.replace("acme", draft(null), null, "admin-1");

        assertThat(tenantRows.get("acme").getLegalName()).isNull();
        assertThat(service.view("acme").profile().legalName()).isNull();
    }

    @Test
    void anUnknownOrBlankOrganizationIsNotFound() {
        assertThatThrownBy(() -> service.view("missing")).isInstanceOf(LegalProfileException.class)
                .extracting(e -> ((LegalProfileException) e).kind()).isEqualTo(LegalProfileException.Kind.NOT_FOUND);
        assertThatThrownBy(() -> service.view(" ")).isInstanceOf(LegalProfileException.class);
        assertThatThrownBy(() -> service.view(null)).isInstanceOf(LegalProfileException.class);
        assertThatThrownBy(() -> service.replace("missing", draft("X"), null, "admin-1")).isInstanceOf(LegalProfileException.class)
                .hasMessage(LegalProfileConstants.ERROR_ORGANIZATION_NOT_FOUND);
    }

    @Test
    void organizationAsProfileIsInvisibleToOrganizationB() {
        service.replace("acme", draft("Acme Mobility Pvt Ltd"), null, "admin-1");

        LegalProfileView globex = service.view("globex");

        assertThat(globex.version()).isNull();
        assertThat(globex.profile()).isEqualTo(LegalProfile.empty(null));
        // B's first save is its own (version 1), not a conflict with A's row, and A's profile is untouched.
        assertThat(service.replace("globex", new LegalProfileDraft("OTHER", "Globex LLC", null, null, "US", false, null, null), null, "admin-9")
                .version()).isEqualTo(1L);
        assertThat(service.view("acme").profile().entityType()).isEqualTo(LegalEntityType.PRIVATE_COMPANY);
        assertThat(service.view("acme").profile().legalName()).isEqualTo("Acme Mobility Pvt Ltd");
        assertThat(tenantRows.get("globex").getLegalName()).isEqualTo("Globex LLC");
    }

    private TenantRepository repositoryOver() {
        TenantRepository tenants = mock(TenantRepository.class);
        when(tenants.findById(any())).thenAnswer(i -> Optional.ofNullable(copy(tenantRows.get(i.<String>getArgument(0)))));
        when(tenants.save(any())).thenAnswer(i -> {
            Tenant t = i.getArgument(0);
            tenantRows.put(t.getId(), copy(t));
            return t;
        });
        return tenants;
    }

    private record Event(String organizationId, String actor, Map<String, Object> before, Map<String, Object> after) {
    }

    private static final class FakeAudit implements LegalProfileAudit {
        private final List<Event> events = new ArrayList<>();

        @Override
        public void profileChanged(String organizationId, String actorUserId, Map<String, Object> before, Map<String, Object> after) {
            events.add(new Event(organizationId, actorUserId, before, after));
        }
    }
}
