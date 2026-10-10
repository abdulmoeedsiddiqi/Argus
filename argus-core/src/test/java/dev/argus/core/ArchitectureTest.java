package dev.argus.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture rules for argus-core.
 * 1. Dependency Inversion: business logic must not depend on frameworks or infrastructure;
 *    adapters for these live in argus-gateway.
 * 2. Acyclic Dependencies: packages must not form dependency cycles. Shared value types live
 *    in dev.argus.core.model, which depends only on the JDK.
 */
@AnalyzeClasses(packages = "dev.argus.core", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule coreIsFrameworkFree = noClasses()
            .that().resideInAPackage("dev.argus.core..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "org.apache.kafka..",
                    "io.lettuce..",
                    "redis.clients..",
                    "jakarta.persistence..");

    @ArchTest
    static final ArchRule corePackagesAreAcyclic = slices()
            .matching("dev.argus.core.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule modelDependsOnNothingElseInCore = noClasses()
            .that().resideInAPackage("dev.argus.core.model..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "dev.argus.core.pipeline..", "dev.argus.core.routing..",
                    "dev.argus.core.auth..", "dev.argus.core.loadbalancing..",
                    "dev.argus.core.ratelimit..", "dev.argus.core.resilience..",
                    "dev.argus.core.events..");
}
