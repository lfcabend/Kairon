package com.kairon.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import jakarta.persistence.Entity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The guardrails that keep the "modular" in modular monolith (docs/DESIGN.md §3.1).
 * As more feature modules land (M2+) the cross-module dependency matrix here grows;
 * with only {@code identity} present so far, the rules pin the layering that
 * matters now: entities stay in {@code domain}, controllers never see entities or
 * repositories, and a module's {@code domain}/{@code repo} internals are private.
 */
@AnalyzeClasses(packages = "com.kairon", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule featureSlicesAreFreeOfCycles = SlicesRuleDefinition.slices()
            .matching("com.kairon.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule jpaEntitiesLiveInDomainPackages = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..domain..")
            .as("JPA entities must live in a module's domain package");

    @ArchTest
    static final ArchRule controllersDoNotTouchEntitiesOrRepositories = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAnyPackage("..domain..", "..repo..")
            .as("web layer must go through application/api types, never entities or repositories");

    @ArchTest
    static final ArchRule identityInternalsArePrivate = noClasses()
            .that().resideOutsideOfPackage("com.kairon.identity..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.kairon.identity.domain..", "com.kairon.identity.repo..")
            .as("other modules may depend only on com.kairon.identity.api");

    @ArchTest
    static final ArchRule todoInternalsArePrivate = noClasses()
            .that().resideOutsideOfPackage("com.kairon.todo..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.kairon.todo.domain..", "com.kairon.todo.repo..")
            .as("other modules may depend only on com.kairon.todo.api");

    @ArchTest
    static final ArchRule journalInternalsArePrivate = noClasses()
            .that().resideOutsideOfPackage("com.kairon.journal..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.kairon.journal.domain..", "com.kairon.journal.repo..")
            .as("other modules may depend only on com.kairon.journal.api");

    @ArchTest
    static final ArchRule projectsInternalsArePrivate = noClasses()
            .that().resideOutsideOfPackage("com.kairon.projects..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.kairon.projects.domain..", "com.kairon.projects.repo..")
            .as("other modules may depend only on com.kairon.projects.api");

    @ArchTest
    static final ArchRule securityStackIsContainedToCommonAndIdentity = noClasses()
            .that().resideOutsideOfPackages("com.kairon.common..", "com.kairon.identity..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.security..", "com.nimbusds..")
            .as("only common (security plumbing) and identity wire Spring Security");

    @ArchTest
    static final ArchRule assistantInternalsArePrivate = noClasses()
            .that().resideOutsideOfPackage("com.kairon.assistant..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.kairon.assistant.domain..", "com.kairon.assistant.repo..")
            .as("other modules may depend only on com.kairon.assistant.api (none exists yet — M8 D1)");

    @ArchTest
    static final ArchRule onlyAssistantImportsTheAnthropicSdk = noClasses()
            .that().resideOutsideOfPackage("com.kairon.assistant..")
            .should().dependOnClassesThat().resideInAPackage("com.anthropic..")
            .as("assistant.llm.AnthropicClient is the only wrapper around the Anthropic SDK (docs/adr/0002)");
}
