package fr.craft.gabier.architecture;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tngtech.archunit.base.HasDescription;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import fr.craft.gabier.shared.generation.ExcludeFromGeneratedCodeCoverage;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The package rules inside the modules, and the purity of the domain. Adapted from the {@code HexagonalArchTest} that
 * seed4j generates (without what is Spring: components, controllers, wire), for a JPMS build: the edges between modules
 * are enforced by the compiler, these tests only look at packages. The classes of every module are on this project's
 * classpath (see its build file).
 *
 * <p>The packages follow {@code fr.craft.gabier.<context>.domain}, {@code .application}, {@code .infrastructure.primary}
 * and {@code .infrastructure.secondary}. No module has them yet: a rule that matches no class passes
 * ({@code allowEmptyShould}), and starts to bite with the first class of its package.
 */
class HexagonalArchTest {

    private static final String ROOT_PACKAGE = "fr.craft.gabier";

    /** Our own code shared by every context: it is not a context. */
    private static final String SHARED = "shared";

    /**
     * The only packages a domain class may depend on: the JDK, our own shared code (the {@code shared} module, which
     * holds the exclusion annotation), and the domain itself. The list exists to keep third-party code (libraries and
     * frameworks) out of the domain. Adding a third-party dependency is the user's decision, never an agent's: it is
     * one line here, and nowhere else.
     *
     * <p>Unlike the seed4j template, the empty package {@code ""} (see
     * https://github.com/TNG/ArchUnit/issues/191#issuecomment-507964792) is not in the list: with ArchUnit 1.4,
     * primitives and arrays pass without it (checked with a domain record holding an {@code int} and a {@code long[]}).
     */
    private static final String[] DOMAIN_ALLOWED_PACKAGES = {
        "java..", ROOT_PACKAGE + "." + SHARED + "..", ROOT_PACKAGE + "..domain.."
    };

    /** The only place where LSP4J lives: the daemon module (the {@code daemon} context). */
    private static final String DAEMON_PACKAGE = ROOT_PACKAGE + ".daemon";

    private static final String LSP4J_PACKAGE = "org.eclipse.lsp4j";

    private static final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT_PACKAGE);

    /**
     * The contexts: the direct sub-packages of the root package other than {@code shared}, such as {@code
     * fr.craft.gabier.protocol}, wherever their module is. They are read from the classes, so a new module needs no
     * line here.
     */
    private static final List<String> contexts = classes.stream()
            .map(JavaClass::getPackageName)
            .filter(name -> name.startsWith(ROOT_PACKAGE + "."))
            .map(name -> name.substring(ROOT_PACKAGE.length() + 1).split("\\.")[0])
            .filter(name -> !name.equals(SHARED))
            .distinct()
            .sorted()
            .map(name -> ROOT_PACKAGE + "." + name)
            .toList();

    @Test
    void importsTheClassesOfTheModules() {
        // Without them every rule below would pass for nothing.
        assertFalse(classes.isEmpty(), "no class found under " + ROOT_PACKAGE);
    }

    @Test
    void packagesShouldBeFreeOfCycles() {
        // One slice per package: a cycle between two packages, at any depth, is a violation.
        slices().matching(ROOT_PACKAGE + ".(**)")
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true)
                .check(classes);
    }

    @Nested
    class BoundedContexts {

        @Test
        void shouldNotDependOnOtherBoundedContextDomains() {
            contexts.forEach(context -> {
                String[] otherDomains = otherBusinessContextsDomains(context);
                if (otherDomains.length == 0) {
                    return;
                }
                noClasses()
                        .that()
                        .resideInAnyPackage(context + "..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(otherDomains)
                        .because("Contexts can only depend on classes in the same context or shared kernels")
                        .allowEmptyShould(true)
                        .check(classes);
            });
        }

        @Test
        void shouldBeAnHexagonalArchitecture() {
            contexts.forEach(context -> layeredArchitecture()
                    .consideringOnlyDependenciesInAnyPackage(context + "..")
                    .withOptionalLayers(true)
                    .layer("domain")
                    .definedBy(context + ".domain..")
                    .layer("application services")
                    .definedBy(context + ".application..")
                    .layer("primary adapters")
                    .definedBy(context + ".infrastructure.primary..")
                    .layer("secondary adapters")
                    .definedBy(context + ".infrastructure.secondary..")
                    .whereLayer("application services")
                    .mayOnlyBeAccessedByLayers("primary adapters")
                    .whereLayer("primary adapters")
                    .mayNotBeAccessedByAnyLayer()
                    .whereLayer("secondary adapters")
                    .mayNotBeAccessedByAnyLayer()
                    .because("Each bounded context should implement a hexagonal architecture")
                    .check(classes));
        }

        private String[] otherBusinessContextsDomains(String context) {
            return contexts.stream()
                    .filter(other -> !context.equals(other))
                    .map(name -> name + ".domain..")
                    .toArray(String[]::new);
        }
    }

    @Nested
    class Domain {

        @Test
        void shouldNotDependOnOutside() {
            classes()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(DOMAIN_ALLOWED_PACKAGES)
                    .because("Domain model should only depend on domains and a very limited set of external"
                            + " dependencies")
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }

    @Nested
    class Application {

        @Test
        void shouldNotDependOnInfrastructure() {
            noClasses()
                    .that()
                    .resideInAPackage("..application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..infrastructure..")
                    .because("Application should only depend on domain, not on infrastructure")
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }

    @Nested
    class Primary {

        @Test
        void shouldNotDependOnSecondary() {
            noClasses()
                    .that()
                    .resideInAPackage("..primary..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..secondary..")
                    .because("Primary should not interact with secondary")
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }

    @Nested
    class Secondary {

        @Test
        void shouldNotDependOnApplication() {
            noClasses()
                    .that()
                    .resideInAPackage("..infrastructure.secondary..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..application..")
                    .because("Secondary should not depend on application")
                    .allowEmptyShould(true)
                    .check(classes);
        }

        @Test
        void shouldNotDependOnSameContextPrimary() {
            contexts.forEach(context -> noClasses()
                    .that()
                    .resideInAPackage(context + ".infrastructure.secondary..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(context + ".infrastructure.primary..")
                    .because("Secondary should not loop to its own context's primary")
                    .allowEmptyShould(true)
                    .check(classes));
        }
    }

    @Nested
    class Lsp4j {

        @Test
        void shouldOnlyBeUsedByTheDaemon() {
            noClasses()
                    .that()
                    .resideOutsideOfPackage(DAEMON_PACKAGE + "..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(LSP4J_PACKAGE + "..")
                    .because("LSP4J lives in the daemon module and no other module sees one of its types")
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }

    @Nested
    class CoverageExclusion {

        @Test
        void typesShouldGiveAReason() {
            classes()
                    .that()
                    .areAnnotatedWith(ExcludeFromGeneratedCodeCoverage.class)
                    .should(giveAReason())
                    .allowEmptyShould(true)
                    .check(classes);
        }

        @Test
        void methodsAndConstructorsShouldGiveAReason() {
            codeUnits()
                    .that()
                    .areAnnotatedWith(ExcludeFromGeneratedCodeCoverage.class)
                    .should(giveAReason())
                    .allowEmptyShould(true)
                    .check(classes);
        }

        /**
         * The compiler requires a reason; this rule requires that it says something. A reason absent from the class file
         * (not possible once compiled) counts as blank.
         */
        private static <T extends HasAnnotations<?> & HasDescription> ArchCondition<T> giveAReason() {
            return new ArchCondition<>("give a non-blank reason to @ExcludeFromGeneratedCodeCoverage") {
                @Override
                public void check(T annotated, ConditionEvents events) {
                    JavaAnnotation<?> annotation =
                            annotated.getAnnotationOfType(ExcludeFromGeneratedCodeCoverage.class.getName());
                    boolean blank = annotation
                            .tryGetExplicitlyDeclaredProperty("reason")
                            .map(reason -> reason instanceof String text && text.isBlank())
                            .orElse(true);
                    if (blank) {
                        events.add(violated(
                                annotated,
                                annotated.getDescription() + " is excluded from coverage without a reason: say why"));
                    }
                }
            };
        }
    }
}
