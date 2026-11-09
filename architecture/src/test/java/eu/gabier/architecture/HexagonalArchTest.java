package eu.gabier.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.base.HasDescription;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import eu.gabier.daemon.violation.DependsOnAnotherContextDomain;
import eu.gabier.daemon.violation.DependsOnTheEditingDomain;
import eu.gabier.protocol.domain.ForeignDomainType;
import eu.gabier.shared.generation.ExcludeFromGeneratedCodeCoverage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The package rules inside the modules, and the purity of the domain. Adapted from the {@code HexagonalArchTest} that
 * seed4j generates (without what is Spring: components, controllers, wire), for a JPMS build: the edges between modules
 * are enforced by the compiler, these tests only look at packages. The classes of every module are on this project's
 * classpath (see its build file).
 *
 * <p>The packages follow {@code eu.gabier.<context>.domain}, {@code .application}, {@code .infrastructure.primary}
 * and {@code .infrastructure.secondary}. The {@code editing} context of the {@code core} module is the first to have
 * them, but three of the four (no primary adapter yet): a rule that matches no class passes ({@code allowEmptyShould}),
 * and starts to bite with the first class of its package. The test fixtures of a module (the fake language server of the
 * core) are not on this project's classpath, which only has the main code of the modules.
 */
class HexagonalArchTest {

    private static final String ROOT_PACKAGE = "eu.gabier";

    /** Our own code shared by every context: it is not a context. */
    private static final String SHARED = "shared";

    /**
     * The only packages a domain class may depend on: the JDK, our own shared code (the {@code shared} module, which
     * holds the exclusion annotation), the null-safety annotations (JSpecify, and the NullAway ones; the user's decision
     * of 30-10-2026), and the domain itself. The list exists to keep third-party code (libraries and
     * frameworks) out of the domain. Adding a third-party dependency is the user's decision, never an agent's: it is
     * one line here, and nowhere else.
     *
     * <p>Unlike the seed4j template, the empty package {@code ""} (see
     * https://github.com/TNG/ArchUnit/issues/191#issuecomment-507964792) is not in the list: with ArchUnit 1.4,
     * primitives and arrays pass without it (checked with a domain record holding an {@code int} and a {@code long[]}).
     */
    private static final String[] DOMAIN_ALLOWED_PACKAGES = {
        "java..",
        ROOT_PACKAGE + "." + SHARED + "..",
        ROOT_PACKAGE + "..domain..",
        "org.jspecify..",
        "com.uber.nullaway.annotations.."
    };

    /** The only place where LSP4J lives: the daemon module (the {@code daemon} context). */
    private static final String DAEMON_PACKAGE = ROOT_PACKAGE + ".daemon";

    /** The contexts whose domain another context may depend on, because it implements its ports. */
    private static final Map<String, List<String>> IMPLEMENTED_DOMAINS =
            Map.of(DAEMON_PACKAGE, List.of(ROOT_PACKAGE + ".editing"));

    private static final String LSP4J_PACKAGE = "org.eclipse.lsp4j";

    /** The secondary adapters of the cli: the way to the daemon, which the commands only know as a port. */
    private static final String CLI_ADAPTERS_PACKAGE = ROOT_PACKAGE + ".cli.infrastructure.secondary";

    /**
     * The class of the cli that builds the adapter of the daemon and hands it to the commands: the composition root. The
     * layered rule forbids every layer to reach a secondary adapter, so the one place that names it is outside the
     * layers, in the root package of the context, and it is the entry point that wires what it runs.
     */
    private static final String CLI_COMPOSITION_ROOT = ROOT_PACKAGE + ".cli.Gabier";

    /**
     * The primary adapter that the composition root starts: the command line. Being outside the layers, the entry point
     * is the one class that reaches it too, and only it.
     */
    private static final String CLI_COMMAND_LINE = ROOT_PACKAGE + ".cli.infrastructure.primary.CommandLine";

    /** The JSpecify annotation that makes NullAway check a package; by name, as the module only needs it to compile. */
    private static final String NULL_MARKED = "org.jspecify.annotations.NullMarked";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT_PACKAGE);

    /**
     * The contexts: the direct sub-packages of the root package other than {@code shared}, such as {@code
     * eu.gabier.protocol}, wherever their module is. They are read from the classes, so a new module needs no
     * line here.
     */
    private static final List<String> CONTEXTS = CLASSES.stream()
            .map(JavaClass::getPackageName)
            .filter(name -> name.startsWith(ROOT_PACKAGE + "."))
            .map(name -> name.substring(ROOT_PACKAGE.length() + 1).split("\\.")[0])
            .filter(name -> !name.equals(SHARED))
            .distinct()
            .sorted()
            .map(name -> ROOT_PACKAGE + "." + name)
            .toList();

    /**
     * The rule that a context only depends on its own domain and on the shared kernel, not on the domain of another
     * context. The {@code daemon} is not a business context: it is the composition root and the home of the adapters of
     * the real servers, so the rule applies to it too, except for the domains it implements, listed in {@link
     * #IMPLEMENTED_DOMAINS} (today the {@code editing} domain, whose port {@code JavaLanguageServer} the jdtls adapter
     * implements). Empty when no domain is left to forbid.
     */
    private static Optional<ArchRule> notDependOnOtherDomains(String context, List<String> allContexts) {
        List<String> implemented = IMPLEMENTED_DOMAINS.getOrDefault(context, List.of());
        String[] otherDomains = allContexts.stream()
                .filter(other -> !other.equals(context) && !implemented.contains(other))
                .map(other -> other + ".domain..")
                .toArray(String[]::new);
        if (otherDomains.length == 0) {
            return Optional.empty();
        }
        return Optional.of(noClasses()
                .that()
                .resideInAnyPackage(context + "..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(otherDomains)
                .because("Contexts can only depend on classes in the same context or shared kernels")
                .allowEmptyShould(true));
    }

    /** The rule of every context, with no exemption: the one loop that the real classes and the tests both run. */
    private static List<ArchRule> notDependOnOtherDomainsOfEveryContext(List<String> contexts) {
        return contexts.stream()
                .map(context -> notDependOnOtherDomains(context, contexts))
                .flatMap(Optional::stream)
                .toList();
    }

    @Test
    void importsTheClassesOfTheModules() {
        // Without them every rule below would pass for nothing.
        assertThat(CLASSES).as("classes found under %s", ROOT_PACKAGE).isNotEmpty();
    }

    @Test
    void packagesShouldBeFreeOfCycles() {
        // One slice per package: a cycle between two packages, at any depth, is a violation.
        slices().matching(ROOT_PACKAGE + ".(**)")
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Nested
    class BoundedContexts {

        @Test
        void shouldNotDependOnOtherBoundedContextDomains() {
            notDependOnOtherDomainsOfEveryContext(CONTEXTS).forEach(rule -> rule.check(CLASSES));
        }

        @Test
        void shouldRunTheRuleOverTheDaemonAmongTheContexts() {
            List<ArchRule> rules = notDependOnOtherDomainsOfEveryContext(CONTEXTS);
            JavaClasses daemonClass = new ClassFileImporter().importClasses(DependsOnAnotherContextDomain.class);

            assertThatThrownBy(() -> rules.forEach(rule -> rule.check(daemonClass)))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining(DependsOnAnotherContextDomain.class.getName());
        }

        @Test
        void shouldApplyToTheDaemonForTheDomainsItDoesNotImplement() {
            ArchRule rule = notDependOnOtherDomains(DAEMON_PACKAGE, CONTEXTS).orElseThrow();
            JavaClasses daemonClass = new ClassFileImporter().importClasses(DependsOnAnotherContextDomain.class);

            assertThatThrownBy(() -> rule.check(daemonClass))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining(DependsOnAnotherContextDomain.class.getName())
                    .hasMessageContaining(ForeignDomainType.class.getName());
        }

        @Test
        void shouldLetTheDaemonDependOnTheDomainItImplements() {
            ArchRule rule = notDependOnOtherDomains(DAEMON_PACKAGE, CONTEXTS).orElseThrow();
            JavaClasses daemonClass = new ClassFileImporter().importClasses(DependsOnTheEditingDomain.class);

            assertThatCode(() -> rule.check(daemonClass)).doesNotThrowAnyException();
        }

        @Test
        void shouldBeAnHexagonalArchitecture() {
            CONTEXTS.forEach(context -> layeredArchitecture()
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
                    // The layers are optional, but a class outside them that reaches a secondary adapter still
                    // counts as a layer access: the composition root of the cli is that class, see CliAdapters.
                    .ignoreDependency(name(CLI_COMPOSITION_ROOT), resideInAPackage(CLI_ADAPTERS_PACKAGE + ".."))
                    .ignoreDependency(name(CLI_COMPOSITION_ROOT), name(CLI_COMMAND_LINE))
                    .because("Each bounded context should implement a hexagonal architecture")
                    .check(CLASSES));
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
                    .check(CLASSES);
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
                    .check(CLASSES);
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
                    .check(CLASSES);
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
                    .check(CLASSES);
        }

        @Test
        void shouldNotDependOnSameContextPrimary() {
            CONTEXTS.forEach(context -> noClasses()
                    .that()
                    .resideInAPackage(context + ".infrastructure.secondary..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(context + ".infrastructure.primary..")
                    .because("Secondary should not loop to its own context's primary")
                    .allowEmptyShould(true)
                    .check(CLASSES));
        }
    }

    @Nested
    class CliAdapters {

        /** Nothing of the cli depends on its adapters but the adapters themselves and the given composition root. */
        private static ArchRule onlyReachedBy(String compositionRoot) {
            return noClasses()
                    .that()
                    .resideInAPackage(ROOT_PACKAGE + ".cli..")
                    .and()
                    .resideOutsideOfPackage(CLI_ADAPTERS_PACKAGE + "..")
                    .and()
                    .doNotHaveFullyQualifiedName(compositionRoot)
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(CLI_ADAPTERS_PACKAGE + "..")
                    .because("The commands reach the daemon through its port; only the composition root, which builds"
                            + " the adapter, names it")
                    .allowEmptyShould(true);
        }

        @Test
        void shouldOnlyBeReachedByTheCompositionRoot() {
            onlyReachedBy(CLI_COMPOSITION_ROOT).check(CLASSES);
        }

        @Test
        void shouldFindTheCompositionRootWhereItWires() {
            // Without the exemption the rule fails on the class that wires the adapter: the name and the rule are live.
            ArchRule rule = onlyReachedBy("eu.gabier.cli.NoSuchClass");

            assertThatThrownBy(() -> rule.check(CLASSES))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining(CLI_COMPOSITION_ROOT);
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
                    .check(CLASSES);
        }
    }

    @Nested
    class NullSafety {

        @Test
        void packagesShouldBeNullMarked() {
            classes()
                    .that()
                    .doNotHaveSimpleName("package-info")
                    .should(resideInANullMarkedPackage())
                    .because("NullAway only checks what is @NullMarked: a package without a package-info.java carrying"
                            + " @NullMarked is silently left unchecked")
                    .allowEmptyShould(true)
                    .check(CLASSES);
        }

        private static ArchCondition<JavaClass> resideInANullMarkedPackage() {
            return new ArchCondition<>("reside in a package annotated with @NullMarked") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    if (!javaClass.getPackage().isAnnotatedWith(NULL_MARKED)) {
                        events.add(violated(
                                javaClass,
                                javaClass.getDescription() + " is in the package " + javaClass.getPackageName()
                                        + ", which has no package-info.java annotated with @NullMarked"));
                    }
                }
            };
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
                    .check(CLASSES);
        }

        @Test
        void methodsAndConstructorsShouldGiveAReason() {
            codeUnits()
                    .that()
                    .areAnnotatedWith(ExcludeFromGeneratedCodeCoverage.class)
                    .should(giveAReason())
                    .allowEmptyShould(true)
                    .check(CLASSES);
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
