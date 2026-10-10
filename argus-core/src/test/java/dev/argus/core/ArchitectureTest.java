package dev.argus.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the Dependency Inversion Principle: business logic in argus-core must not depend
 * on frameworks or infrastructure. Adapters for these live in argus-gateway.
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
}
