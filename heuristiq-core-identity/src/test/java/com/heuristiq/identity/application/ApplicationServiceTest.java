package com.heuristiq.identity.application;

import com.heuristiq.identity.application.ApplicationServiceUseCase.CreateInput;
import com.heuristiq.identity.application.ApplicationServiceUseCase.DeleteInput;
import com.heuristiq.identity.application.ApplicationServiceUseCase.GetInput;
import com.heuristiq.identity.application.ApplicationServiceUseCase.ListInput;
import com.heuristiq.identity.application.ApplicationServiceUseCase.ListPageInput;
import com.heuristiq.identity.application.ApplicationServiceUseCase.UpdateInput;
import com.heuristiq.identity.application.catalogue.ApplicationCatalogue;
import com.heuristiq.identity.common.FieldValidationError;
import com.heuristiq.identity.common.RequestValidationException;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.enums.UamEnums.ApplicationType;
import com.heuristiq.identity.enums.UamEnums.AuthenticationMode;
import com.heuristiq.identity.enums.UamEnums.TenantStatus;
import com.heuristiq.identity.organization.DefaultOrganizationResolver;
import com.heuristiq.identity.organization.PlatformOrganizationProperties;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.organization.subscription.TenantApplication;
import com.heuristiq.identity.organization.subscription.TenantApplicationService;
import com.heuristiq.identity.organization.subscription.TenantApplicationStatus;
import com.heuristiq.identity.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Applications per organization: catalogue validation, the link to the session organization, code immutability, and that
 * one organization never sees, changes or removes another organization's applications.
 */
class ApplicationServiceTest {
    private static final String ORG_A = "org-a";
    private static final String ORG_B = "org-b";
    private static final String DEFAULT_ORG = "default-org-id";

    private InMemoryApplicationRepository applications;
    private InMemoryTenantApplicationRepository links;
    private TenantRepository tenants;
    private ApplicationService service;
    private com.heuristiq.identity.bootstrap.PermissionCatalogProvisioner catalogProvisioner;
    private com.heuristiq.identity.application.access.repository.ApplicationAccessRepository access;

    @BeforeEach
    void setUp() {
        applications = new InMemoryApplicationRepository();
        links = new InMemoryTenantApplicationRepository();
        tenants = mock(TenantRepository.class);
        access = mock(com.heuristiq.identity.application.access.repository.ApplicationAccessRepository.class);
        catalogProvisioner = mock(com.heuristiq.identity.bootstrap.PermissionCatalogProvisioner.class);
        for (String id : List.of(ORG_A, ORG_B, DEFAULT_ORG)) {
            when(tenants.findById(id)).thenReturn(Optional.of(Tenant.builder().id(id).status(TenantStatus.ACTIVE).build()));
        }
        when(tenants.findByCodeIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(tenants.findByCodeIgnoreCase("DEFAULT")).thenReturn(Optional.of(Tenant.builder().id(DEFAULT_ORG).code("DEFAULT").build()));
        TenantApplicationService organizationApplications = new TenantApplicationService(tenants, applications, links);
        service = new ApplicationService(applications, organizationApplications,
                new SessionOrganizationResolver(new DefaultOrganizationResolver(tenants, PlatformOrganizationProperties.defaults())),
                access, catalogProvisioner);
        TenantContext.set(ORG_A);
    }

    @AfterEach
    void clearSession() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static void signIn(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("someone", null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    static List<String> catalogueCodes() {
        return ApplicationCatalogue.codes();
    }

    @Test
    void creatingAnApplicationInvokesItsPrivilegeImport() {
        service.create(new CreateInput(request(ApplicationCatalogue.BESTRIDE_RIDER_APP, null, null, null)));
        org.mockito.Mockito.verify(catalogProvisioner).provisionApplication(org.mockito.ArgumentMatchers.argThat(
                app -> ApplicationCatalogue.BESTRIDE_RIDER_APP.equals(app.getCode())));
    }

    @Test
    void failedPrivilegeImportsKeepRegistrationSoSavingTheApplicationCanRetry() {
        org.mockito.Mockito.doThrow(new IllegalStateException("import failed")).doNothing().when(catalogProvisioner)
                .provisionApplication(org.mockito.ArgumentMatchers.any());
        assertThatThrownBy(() -> service.create(new CreateInput(request(ApplicationCatalogue.BESTRIDE_RIDER_APP, null, null, null))))
                .hasMessageContaining("import failed");
        Application app = applications.rows.get(0);
        assertThat(links.find(ORG_A, app.getCode())).isPresent();
        signIn("ROLE_SUPER_ADMIN");
        service.update(new UpdateInput(app.getId(), request(app.getCode(), null, null, null)));
        org.mockito.Mockito.verify(catalogProvisioner, org.mockito.Mockito.times(2)).provisionApplication(org.mockito.ArgumentMatchers.any());
    }

    // ── create ──────────────────────────────────────────────────────────────

    @ParameterizedTest
    @MethodSource("catalogueCodes")
    void everyCatalogueCodeCanBeCreatedAndDefaultsComeFromTheCatalogue(String code) {
        var entry = ApplicationCatalogue.find(code).orElseThrow();

        Application app = service.create(new CreateInput(request(" " + code.toLowerCase() + " ", null, null, null), "admin-a")).application();

        assertThat(app.getCode()).isEqualTo(code);
        assertThat(app.getName()).isEqualTo(entry.name());
        assertThat(app.getDescription()).isEqualTo(entry.description());
        assertThat(app.getType()).isEqualTo(entry.type());
        assertThat(app.getDisplayOrder()).isEqualTo(entry.displayOrder());
        assertThat(app.getActive()).isTrue();
        assertThat(links.find(ORG_A, code)).hasValueSatisfying(link -> {
            assertThat(link.getStatus()).isEqualTo(TenantApplicationStatus.ACTIVE);
            assertThat(link.getSubscribedBy()).isEqualTo("admin-a");
        });
    }

    @Test
    void aPlatformAdministratorsNameDescriptionAndOrderWinOverTheCatalogueDefaults() {
        signIn("ROLE_SUPER_ADMIN");
        Application app = service.create(new CreateInput(new UpsertApplicationRequest(ApplicationCatalogue.BEST_PURCHASE_APP, " Shop ",
                " Our shop ", null, null, null, null, null, ApplicationType.WEB, AuthenticationMode.LOCAL, false, true, 3))).application();

        assertThat(app.getName()).isEqualTo("Shop");
        assertThat(app.getDescription()).isEqualTo("Our shop");
        assertThat(app.getDisplayOrder()).isEqualTo(3);
        assertThat(app.getSsoEnabled()).isFalse();
        assertThat(app.getMfaRequired()).isTrue();
    }

    @Test
    void aCodeOutsideTheCatalogueIsAFieldError() {
        for (String code : List.of("RIDER", "MATRIMONY", "PASUPU_KUNKUMA_MATRIMONY", "CUSTOM_APP")) {
            assertThatThrownBy(() -> service.create(new CreateInput(request(code, null, null, null))))
                    .isInstanceOfSatisfying(RequestValidationException.class, e -> assertThat(e.getErrors())
                            .extracting(FieldValidationError::field).containsExactly(ApplicationConstants.FIELD_CODE));
        }
        assertThat(applications.rows).isEmpty();
        assertThat(links.rows).isEmpty();
    }

    @Test
    void aBlankCodeIsRejected() {
        assertThatThrownBy(() -> service.create(new CreateInput(request("  ", null, null, null))))
                .hasMessageContaining("code is required");
    }

    @Test
    void aTypeOtherThanTheCataloguesIsAFieldError() {
        assertThatThrownBy(() -> service.create(new CreateInput(request(ApplicationCatalogue.BESTRIDE_RIDER_APP, null, ApplicationType.API, null))))
                .isInstanceOfSatisfying(RequestValidationException.class, e -> assertThat(e.getErrors())
                        .extracting(FieldValidationError::field).containsExactly(ApplicationConstants.FIELD_TYPE));
    }

    @Test
    void creatingTheSameCodeTwiceInOneOrganizationIsAConflict() {
        service.create(new CreateInput(request(ApplicationCatalogue.FOOD_DELIVER_APP, null, null, null)));

        assertThatThrownBy(() -> service.create(new CreateInput(request("food_deliver_app", null, null, null))))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("FOOD_DELIVER_APP");
                });
        assertThat(applications.rows).hasSize(1);
    }

    @Test
    void anotherOrganizationCreatingTheSameCodeIsLinkedToTheOneRowWithoutChangingIt() {
        signIn("ROLE_SUPER_ADMIN");
        Application first = service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, "Shop A", null, null))).application();
        signIn("ROLE_ADMIN");
        TenantContext.set(ORG_B);

        Application second = service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, "Shop B", null, null))).application();

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getName()).isEqualTo("Shop A");
        assertThat(applications.rows).hasSize(1);
        assertThat(links.find(ORG_B, ApplicationCatalogue.BEST_PURCHASE_APP)).isPresent();
    }

    @Test
    void aCancelledLinkIsTurnedBackOnWhenTheApplicationIsCreatedAgain() {
        Application app = service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null))).application();
        TenantContext.set(ORG_B);
        service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null)));
        TenantContext.set(ORG_A);
        service.delete(new DeleteInput(app.getId(), "admin-a"));
        assertThat(links.find(ORG_A, ApplicationCatalogue.BEST_PURCHASE_APP).orElseThrow().getStatus()).isEqualTo(TenantApplicationStatus.CANCELLED);

        service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null)));

        assertThat(links.find(ORG_A, ApplicationCatalogue.BEST_PURCHASE_APP).orElseThrow().getStatus()).isEqualTo(TenantApplicationStatus.ACTIVE);
    }

    @Test
    void aNewRowIsTakenBackWhenTheOrganizationCannotBeLinked() {
        when(tenants.findById(ORG_A)).thenReturn(Optional.of(Tenant.builder().id(ORG_A).status(TenantStatus.ARCHIVED).build()));

        assertThatThrownBy(() -> service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null))))
                .isInstanceOf(UamException.class).hasMessageContaining("archived");

        assertThat(applications.rows).isEmpty();
        assertThat(links.rows).isEmpty();
    }

    @Test
    void anExistingRowIsKeptWhenTheOrganizationCannotBeLinked() {
        service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null)));
        TenantContext.set(ORG_B);
        links.save(TenantApplication.builder().tenantId(ORG_B).applicationCode("OTHER").build());
        when(tenants.findById(ORG_B)).thenReturn(Optional.of(Tenant.builder().id(ORG_B).status(TenantStatus.ACTIVE).maxApplications(1).build()));

        assertThatThrownBy(() -> service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(applications.rows).hasSize(1);
    }

    @Test
    void aCallerWithNoOrganizationCreatesInTheDefaultOrganization() {
        TenantContext.clear();

        service.create(new CreateInput(request(ApplicationCatalogue.CENTRALISED_WALLET_APP, null, null, null)));

        assertThat(links.find(DEFAULT_ORG, ApplicationCatalogue.CENTRALISED_WALLET_APP)).isPresent();
    }

    // ── update ──────────────────────────────────────────────────────────────

    @Test
    void updateChangesTheEditableFieldsAndKeepsTheCatalogueType() {
        signIn("ROLE_SUPER_ADMIN");
        Application app = service.create(new CreateInput(request(ApplicationCatalogue.BESTRIDE_RIDER_APP, null, null, null))).application();
        applications.rows.get(0).setAudit(null);

        Application out = service.update(new UpdateInput(app.getId(), new UpsertApplicationRequest("bestride_rider_app", " Ride ", " d ",
                " 1.0 ", " http://b ", "/home", " team ", "s@x.com", null, AuthenticationMode.OIDC, false, true, 7))).application();

        assertThat(out.getCode()).isEqualTo(ApplicationCatalogue.BESTRIDE_RIDER_APP);
        assertThat(out.getName()).isEqualTo("Ride");
        assertThat(out.getDescription()).isEqualTo("d");
        assertThat(out.getAppVersion()).isEqualTo("1.0");
        assertThat(out.getOwnerTeam()).isEqualTo("team");
        assertThat(out.getType()).isEqualTo(ApplicationType.MOBILE);
        assertThat(out.getAuthenticationMode()).isEqualTo(AuthenticationMode.OIDC);
        assertThat(out.getDisplayOrder()).isEqualTo(7);
    }

    @Test
    void updateFallsBackToTheCatalogueNameAndStampsTheUpdateTime() {
        signIn("ROLE_SUPER_ADMIN");
        Application app = service.create(new CreateInput(request(ApplicationCatalogue.BESTRIDE_RIDER_APP, "X", null, null))).application();

        Application out = service.update(new UpdateInput(app.getId(), request(ApplicationCatalogue.BESTRIDE_RIDER_APP, " ", null, null))).application();

        assertThat(out.getName()).isEqualTo("BestRide Rider");
        assertThat(out.getAudit().getUpdatedAt()).isNotNull();
    }

    @Test
    void theCodeCanNeverChange() {
        Application app = service.create(new CreateInput(request(ApplicationCatalogue.BESTRIDE_RIDER_APP, null, null, null))).application();

        assertThatThrownBy(() -> service.update(new UpdateInput(app.getId(), request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null))))
                .isInstanceOfSatisfying(RequestValidationException.class, e -> assertThat(e.getErrors())
                        .containsExactly(new FieldValidationError(ApplicationConstants.FIELD_CODE, ApplicationConstants.CODE_IMMUTABLE)));
        assertThat(applications.rows.get(0).getCode()).isEqualTo(ApplicationCatalogue.BESTRIDE_RIDER_APP);
    }

    @Test
    void updateRejectsAnotherTypeForACatalogueApplication() {
        Application app = service.create(new CreateInput(request(ApplicationCatalogue.BESTRIDE_RIDER_APP, null, null, null))).application();

        assertThatThrownBy(() -> service.update(new UpdateInput(app.getId(), request(ApplicationCatalogue.BESTRIDE_RIDER_APP, null, ApplicationType.WEB, null))))
                .isInstanceOf(RequestValidationException.class);
    }

    @Test
    void anOldApplicationOutsideTheCatalogueKeepsItsSentTypeAndCodeAsName() {
        Application legacy = applications.save(Application.builder().code("LEGACY").name("Legacy").active(true).build());
        links.save(TenantApplication.builder().tenantId(ORG_A).applicationCode("LEGACY").build());

        Application out = service.update(new UpdateInput(legacy.getId(), request("legacy", null, ApplicationType.API, null))).application();

        assertThat(out.getType()).isEqualTo(ApplicationType.API);
        assertThat(out.getName()).isEqualTo("Legacy");
    }

    // ── organization isolation ─────────────────────────────────────────────

    @Test
    void anotherOrganizationCannotReadChangeOrRemoveTheApplication() {
        Application app = service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null))).application();
        TenantContext.set(ORG_B);

        assertThat(service.list(new ListInput()).applications()).isEmpty();
        assertThat(service.list(new ListPageInput(0, 50)).applications().content()).isEmpty();
        assertThatThrownBy(() -> service.get(new GetInput(app.getId())))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.update(new UpdateInput(app.getId(), request(ApplicationCatalogue.BEST_PURCHASE_APP, "Hijack", null, null))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.delete(new DeleteInput(app.getId())))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(service.catalogue().items()).noneMatch(ApplicationCatalogueItem::created);
        assertThat(service.lookup().applications()).isEmpty();

        TenantContext.set(ORG_A);
        assertThat(service.get(new GetInput(app.getId())).application().getName()).isEqualTo("Best Purchase");
    }

    @Test
    void getOfAnUnknownIdIsNotFound() {
        assertThatThrownBy(() -> service.get(new GetInput("nope")))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── list ────────────────────────────────────────────────────────────────

    @Test
    void theListIsTheSessionOrganizationsApplicationsFilteredAndPaged() {
        service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null)));
        service.create(new CreateInput(request(ApplicationCatalogue.BESTRIDE_RIDER_APP, null, null, null)));
        service.create(new CreateInput(request(ApplicationCatalogue.FOOD_DELIVER_APP, null, null, null)));

        assertThat(service.list(new ListInput()).applications()).extracting(Application::getCode)
                .containsExactly("BESTRIDE_RIDER_APP", "BEST_PURCHASE_APP", "FOOD_DELIVER_APP");
        var firstPage = service.list(new ListPageInput(0, 2)).applications();
        assertThat(firstPage.content()).extracting(Application::getCode).containsExactly("BESTRIDE_RIDER_APP", "BEST_PURCHASE_APP");
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(service.list(new ListPageInput(5, 2)).applications().content()).isEmpty();
        assertThat(service.list(new ListPageInput(0, 50, " best_purchase_app ", null, null)).applications().content())
                .extracting(Application::getCode).containsExactly("BEST_PURCHASE_APP");
        assertThat(service.list(new ListPageInput(0, 50, null, "FOOD", null)).applications().content())
                .extracting(Application::getCode).containsExactly("FOOD_DELIVER_APP");
        assertThat(service.list(new ListPageInput(0, 50, null, null, ApplicationType.MOBILE)).applications().content())
                .extracting(Application::getCode).containsExactly("BESTRIDE_RIDER_APP", "FOOD_DELIVER_APP");
    }

    // ── delete ──────────────────────────────────────────────────────────────

    @Test
    void deletingTheOnlyUserOfAnApplicationRemovesTheRowAndCancelsTheLink() {
        Application app = service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null))).application();

        service.delete(new DeleteInput(app.getId(), "admin-a"));

        assertThat(applications.rows).isEmpty();
        assertThat(links.find(ORG_A, ApplicationCatalogue.BEST_PURCHASE_APP).orElseThrow().getStatus()).isEqualTo(TenantApplicationStatus.CANCELLED);
    }

    @Test
    void deletingAnApplicationThatRolesAndUsersCanStillUseIsRefusedWithAClearConflict() {
        Application app = service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null))).application();
        when(access.findByApplicationCode(ApplicationCatalogue.BEST_PURCHASE_APP)).thenReturn(List.of(
                grant(com.heuristiq.identity.application.access.AccessSubjectType.ROLE, "R1", true),
                grant(com.heuristiq.identity.application.access.AccessSubjectType.USER, "u1", true),
                grant(com.heuristiq.identity.application.access.AccessSubjectType.USER, "u2", true),
                grant(com.heuristiq.identity.application.access.AccessSubjectType.USER, "gone", false)));

        assertThatThrownBy(() -> service.delete(new DeleteInput(app.getId(), "admin-a")))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo(
                            "This application still has 1 roles and 2 users with access. Remove their access first.");
                });

        assertThat(applications.rows).hasSize(1);
        assertThat(links.find(ORG_A, ApplicationCatalogue.BEST_PURCHASE_APP).orElseThrow().getStatus()).isEqualTo(TenantApplicationStatus.ACTIVE);
    }

    @Test
    void revokedGrantsDoNotBlockTheDelete() {
        Application app = service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null))).application();
        when(access.findByApplicationCode(ApplicationCatalogue.BEST_PURCHASE_APP)).thenReturn(List.of(
                grant(com.heuristiq.identity.application.access.AccessSubjectType.USER, "gone", false)));

        service.delete(new DeleteInput(app.getId(), "admin-a"));

        assertThat(applications.rows).isEmpty();
    }

    @Test
    void aDatabaseReferenceThatStillExistsBecomesAConflictNotAServerError() {
        Application app = service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null))).application();
        var failing = org.mockito.Mockito.spy(applications);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("fk")).when(failing).delete(org.mockito.ArgumentMatchers.any());
        ApplicationService guarded = new ApplicationService(failing, new TenantApplicationService(tenants, applications, links),
                new SessionOrganizationResolver(new DefaultOrganizationResolver(tenants, PlatformOrganizationProperties.defaults())), access, catalogProvisioner);

        assertThatThrownBy(() -> guarded.delete(new DeleteInput(app.getId(), "admin-a")))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("still in use");
                });
    }

    private static com.heuristiq.identity.application.access.ApplicationAccess grant(
            com.heuristiq.identity.application.access.AccessSubjectType type, String subject, boolean active) {
        return com.heuristiq.identity.application.access.ApplicationAccess.builder().applicationCode(ApplicationCatalogue.BEST_PURCHASE_APP)
                .subjectType(type).subjectId(subject).active(active).build();
    }

    @Test
    void deletingAnApplicationAnotherOrganizationUsesOnlyRemovesItFromThisOrganization() {
        Application app = service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null))).application();
        TenantContext.set(ORG_B);
        service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null)));
        TenantContext.set(ORG_A);

        service.delete(new DeleteInput(app.getId()));

        assertThat(applications.rows).hasSize(1);
        assertThat(service.list(new ListInput()).applications()).isEmpty();
        TenantContext.set(ORG_B);
        assertThat(service.list(new ListInput()).applications()).hasSize(1);
    }

    // ── catalogue and lookup ────────────────────────────────────────────────

    @Test
    void theCatalogueListsAllFiveCodesAndMarksTheOnesThisOrganizationHas() {
        service.create(new CreateInput(request(ApplicationCatalogue.FOOD_DELIVER_APP, null, null, null)));

        List<ApplicationCatalogueItem> items = service.catalogue().items();

        assertThat(items).extracting(ApplicationCatalogueItem::code).containsExactly("PASUPU_KUMKUMA_MATRIMONY_APP",
                "BESTRIDE_RIDER_APP", "FOOD_DELIVER_APP", "BEST_PURCHASE_APP", "CENTRALISED_WALLET_APP",
                "TRAIN_BOOKING_APP", "FRANCHISE_MANAGEMENT_APP", "DINEIN_POS_APP");
        assertThat(items).filteredOn(ApplicationCatalogueItem::created).extracting(ApplicationCatalogueItem::code)
                .containsExactly("FOOD_DELIVER_APP");
        assertThat(items.get(0).iconHint()).isEqualTo("favorite");
    }

    @Test
    void theLookupHasOnlyActiveApplicationsWithLiveLinksSortedByDisplayOrder() {
        service.create(new CreateInput(request(ApplicationCatalogue.CENTRALISED_WALLET_APP, null, null, 1)));
        service.create(new CreateInput(request(ApplicationCatalogue.BESTRIDE_RIDER_APP, null, null, null)));
        service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null)));
        applications.rows.stream().filter(a -> ApplicationCatalogue.BEST_PURCHASE_APP.equals(a.getCode())).findFirst().orElseThrow().setActive(false);
        service.create(new CreateInput(request(ApplicationCatalogue.FOOD_DELIVER_APP, null, null, null)));
        links.find(ORG_A, ApplicationCatalogue.FOOD_DELIVER_APP).orElseThrow().setStatus(TenantApplicationStatus.SUSPENDED);
        service.create(new CreateInput(request(ApplicationCatalogue.PASUPU_KUMKUMA_MATRIMONY_APP, null, null, null)));
        links.find(ORG_A, ApplicationCatalogue.PASUPU_KUMKUMA_MATRIMONY_APP).orElseThrow().setValidUntil(LocalDate.of(2000, 1, 1));
        applications.save(Application.builder().code("LEGACY").name("Legacy").active(true).build());
        links.save(TenantApplication.builder().tenantId(ORG_A).applicationCode("LEGACY").build());

        List<ApplicationLookupItem> lookup = service.lookup().applications();

        assertThat(lookup).extracting(ApplicationLookupItem::code).containsExactly("CENTRALISED_WALLET_APP", "BESTRIDE_RIDER_APP");
        ApplicationLookupItem wallet = lookup.get(0);
        assertThat(wallet.name()).isEqualTo("Centralised Wallet");
        assertThat(wallet.type()).isEqualTo(ApplicationType.WEB);
        assertThat(wallet.displayOrder()).isEqualTo(1);
        assertThat(wallet.iconHint()).isEqualTo("account_balance_wallet");
        assertThat(wallet.description()).isNotBlank();
    }

    @Test
    void theLookupUsesTheCatalogueOrderAndNameWhenTheRowHasNone() {
        service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null)));
        Application app = applications.rows.get(0);
        app.setDisplayOrder(null);
        app.setName(null);
        app.setDescription(" ");

        ApplicationLookupItem item = service.lookup().applications().get(0);

        assertThat(item.displayOrder()).isEqualTo(40);
        assertThat(item.name()).isEqualTo("Best Purchase");
        assertThat(item.description()).isNull();
    }

    @Test
    void ensureCatalogueApplicationCreatesTheRowOnceFromTheCatalogueAndNeverLinksIt() {
        Application first = service.ensureCatalogueApplication(" pasupu_kumkuma_matrimony_app ");
        first.setName("Renamed by an admin");

        Application again = service.ensureCatalogueApplication(ApplicationCatalogue.PASUPU_KUMKUMA_MATRIMONY_APP);

        assertThat(again).isSameAs(first);
        assertThat(again.getName()).isEqualTo("Renamed by an admin");
        assertThat(first.getType()).isEqualTo(ApplicationType.WEB);
        assertThat(first.getAuthenticationMode()).isEqualTo(AuthenticationMode.LOCAL);
        assertThat(applications.rows).hasSize(1);
        assertThat(links.rows).isEmpty();
        assertThatThrownBy(() -> service.ensureCatalogueApplication("MATRIMONY")).isInstanceOf(RequestValidationException.class);
    }

    // ── shared row: name and description are the platform's ────────────────

    @Test
    void anOrganizationAdministratorGetsTheCatalogueNameWhenTheyCreateTheSharedRow() {
        signIn("ROLE_ADMIN");

        Application app = service.create(new CreateInput(new UpsertApplicationRequest(ApplicationCatalogue.BEST_PURCHASE_APP, "Mine", "Mine too",
                null, null, null, null, null, null, AuthenticationMode.LOCAL, null, null, null))).application();

        assertThat(app.getName()).isEqualTo("Best Purchase");
        assertThat(app.getDescription()).isEqualTo(ApplicationCatalogue.find(ApplicationCatalogue.BEST_PURCHASE_APP).orElseThrow().description());
    }

    @Test
    void anOrganizationAdministratorCannotChangeTheNameOrDescriptionOfTheSharedRow() {
        signIn("ROLE_ADMIN");
        Application app = service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null))).application();
        Application stored = applications.rows.get(0);

        assertThatThrownBy(() -> service.update(new UpdateInput(app.getId(), request(ApplicationCatalogue.BEST_PURCHASE_APP, "Renamed", null, null))))
                .isInstanceOf(SharedApplicationFieldsForbiddenException.class)
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.update(new UpdateInput(app.getId(), new UpsertApplicationRequest(ApplicationCatalogue.BEST_PURCHASE_APP,
                null, "New text", null, null, null, null, null, null, AuthenticationMode.LOCAL, null, null, null))))
                .isInstanceOf(SharedApplicationFieldsForbiddenException.class);
        assertThat(stored.getName()).isEqualTo("Best Purchase");
        assertThat(stored.getDescription()).isNotEqualTo("New text");
    }

    @Test
    void anOrganizationAdministratorMayResendTheUnchangedNameAndDescriptionAndChangeTheOtherFields() {
        signIn("ROLE_ADMIN");
        Application app = service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null))).application();

        Application out = service.update(new UpdateInput(app.getId(), new UpsertApplicationRequest(ApplicationCatalogue.BEST_PURCHASE_APP,
                app.getName(), app.getDescription(), "2.0", null, null, "team", null, null, AuthenticationMode.LOCAL, null, null, null))).application();

        assertThat(out.getName()).isEqualTo("Best Purchase");
        assertThat(out.getAppVersion()).isEqualTo("2.0");
        assertThat(out.getOwnerTeam()).isEqualTo("team");
    }

    @Test
    void aPlatformAdministratorCanRenameTheSharedRowAndEveryOrganizationSeesIt() {
        signIn("ROLE_ADMIN");
        Application app = service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null))).application();
        TenantContext.set(ORG_B);
        service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null)));
        signIn("ROLE_SUPER_ADMIN");
        TenantContext.set(ORG_A);

        service.update(new UpdateInput(app.getId(), request(ApplicationCatalogue.BEST_PURCHASE_APP, "Marketplace", null, null)));

        TenantContext.set(ORG_B);
        assertThat(service.list(new ListInput()).applications()).extracting(Application::getName).containsExactly("Marketplace");
    }

    // ── per-organization on/off switch (on the link, not the shared row) ───

    @Test
    void switchingOffForOneOrganizationLeavesTheSharedRowAndTheOtherOrganizationUntouched() {
        Application app = service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null))).application();
        TenantContext.set(ORG_B);
        service.create(new CreateInput(request(ApplicationCatalogue.BEST_PURCHASE_APP, null, null, null)));
        TenantContext.set(ORG_A);

        Application out = service.update(new UpdateInput(app.getId(), new UpsertApplicationRequest(ApplicationCatalogue.BEST_PURCHASE_APP, null, null,
                null, null, null, null, null, null, AuthenticationMode.LOCAL, null, null, null, false), "admin-a")).application();

        assertThat(out.getActive()).isFalse();
        assertThat(applications.rows.get(0).getActive()).isTrue();
        assertThat(links.find(ORG_A, ApplicationCatalogue.BEST_PURCHASE_APP).orElseThrow().getStatus()).isEqualTo(TenantApplicationStatus.SUSPENDED);
        assertThat(links.find(ORG_A, ApplicationCatalogue.BEST_PURCHASE_APP).orElseThrow().getUpdatedBy()).isEqualTo("admin-a");
        assertThat(service.lookup().applications()).isEmpty();
        assertThat(service.get(new GetInput(app.getId())).application().getActive()).isFalse();
        TenantContext.set(ORG_B);
        assertThat(service.lookup().applications()).extracting(ApplicationLookupItem::code).containsExactly(ApplicationCatalogue.BEST_PURCHASE_APP);
        assertThat(service.list(new ListInput()).applications()).extracting(Application::getActive).containsExactly(true);

        TenantContext.set(ORG_A);
        service.update(new UpdateInput(app.getId(), new UpsertApplicationRequest(ApplicationCatalogue.BEST_PURCHASE_APP, null, null,
                null, null, null, null, null, null, AuthenticationMode.LOCAL, null, null, null, true), "admin-a"));
        assertThat(service.lookup().applications()).hasSize(1);
    }

    // ── no session ──────────────────────────────────────────────────────────

    @Test
    void aSignedInCallerWhoseTokenHasNoOrganizationIsRefusedNotPlacedInTheDefaultOne() {
        TenantContext.clear();
        signIn("ROLE_ADMIN");

        assertThatThrownBy(() -> service.list(new ListInput()))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.catalogue()).isInstanceOf(UamException.class);
    }

    private static UpsertApplicationRequest request(String code, String name, ApplicationType type, Integer displayOrder) {
        return new UpsertApplicationRequest(code, name, null, null, null, null, null, null, type, AuthenticationMode.LOCAL,
                null, null, displayOrder);
    }
}
