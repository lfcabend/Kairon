package com.kairon.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * The guardrail that keeps the "modular" in modular monolith (docs/DESIGN.md §3.1).
 *
 * M0 only has the {@code common} and {@code meta} packages, so the enforceable
 * rules are limited. As feature modules land this class grows the real contracts:
 * a module may depend only on another module's {@code api} sub-package; controllers
 * never take or return JPA entities; only {@code assistant} imports the Anthropic SDK.
 */
@AnalyzeClasses(packages = "com.kairon", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule featureSlicesAreFreeOfCycles = SlicesRuleDefinition.slices()
            .matching("com.kairon.(*)..")
            .should().beFreeOfCycles();
}
