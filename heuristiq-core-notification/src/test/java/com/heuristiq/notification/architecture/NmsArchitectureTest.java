package com.heuristiq.notification.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the Clean Architecture Dependency Rule across the full package tree, now that Phase 5
 * (physical restructuring) has moved every class into its layer:
 *
 * <pre>
 * domain/          model (pure entities/enums), repository PORTS (interfaces only)
 * application/     port.in (use-case interfaces), port.out (ports the use cases depend on),
 *                  usecase(.support), dto (use-case boundary input/output)
 * adapters/in/*    web controllers, Kafka consumer
 * adapters/out/*   persistence/channel/integration -- concrete implementations of
 *                  application.port.out (or, for persistence, application.port-adjacent
 *                  domain.repository ports)
 * infrastructure/  @ConfigurationProperties, framework wiring (Spring Security, WebSocket,
 *                  Kafka/FCM config, schedulers) -- legitimately wires beans across every layer,
 *                  same precedent as PaymentAPI's root-level config package
 * </pre>
 *
 * shared/* (CommonRequestObject, GlobalExceptionHandler, etc.) deliberately isn't assigned to a
 * layer -- ArchUnit only checks accesses between declared layers, so an unclassified package is
 * simply not covered, not a violation.
 */
@AnalyzeClasses(packages = "com.heuristiq.notification", importOptions = ImportOption.DoNotIncludeTests.class)
class NmsArchitectureTest {

    /** domain.model must stay framework-free -- persistence/web concerns belong in adapters. */
    @ArchTest
    static final ArchRule domain_model_is_pure = noClasses()
            .that().resideInAPackage("..domain.model..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "com.mongodb..",
                    "org.bson..",
                    "..adapters..")
            .because("domain.model is the Entities layer and must not depend on frameworks or adapters");

    /**
     * The full Dependency Rule: source code dependencies only point inward. Domain depends on
     * nothing else in this tree; Application depends only on Domain (+ Infrastructure's typed
     * config); adapters depend inward but are never depended upon (Spring's DI container wires
     * the concrete adapter to the port interface at runtime -- that's not a compile-time
     * dependency edge ArchUnit would see as a violation of this rule).
     */
    @ArchTest
    static final ArchRule layered = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("AdaptersIn").definedBy("..adapters.in..")
            .layer("AdaptersOut").definedBy("..adapters.out..")
            .layer("Infrastructure").definedBy("..infrastructure..")

            // Incoming: who may reach each layer.
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "AdaptersIn", "AdaptersOut", "Infrastructure")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("AdaptersIn", "AdaptersOut", "Infrastructure")
            .whereLayer("AdaptersIn").mayNotBeAccessedByAnyLayer()
            .whereLayer("AdaptersOut").mayNotBeAccessedByAnyLayer()

            // Outgoing: what each layer may itself depend on -- this is the half that actually
            // catches "a use case reached into a concrete adapter instead of its own port
            // interface". Application may read Infrastructure's @ConfigurationProperties classes
            // (plain typed config values, not framework logic) but never Adapters directly.
            .whereLayer("Domain").mayNotAccessAnyLayer()
            .whereLayer("Application").mayOnlyAccessLayers("Domain", "Infrastructure")
            .whereLayer("AdaptersIn").mayOnlyAccessLayers("Domain", "Application")
            .whereLayer("AdaptersOut").mayOnlyAccessLayers("Domain", "Application", "Infrastructure");
}
