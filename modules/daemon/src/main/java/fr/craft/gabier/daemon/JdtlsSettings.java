package fr.craft.gabier.daemon;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a jdtls session is asked to do and how long it may take.
 *
 * @param projectRoot the folder jdtls is initialized on
 * @param expectedProjects the folders of the projects jdtls must have imported when it says it is ready (absolute and
 *     normalized, whatever they were given as); none when the project is served whatever jdtls finds in it
 * @param timeout how long the whole start may take, all its waits together, and then each request to the started jdtls
 * @param treeInterval how long to wait between two readings of the process tree of jdtls
 * @param shutdownTimeout how long jdtls is given to answer the shutdown and to exit, and then each process of its tree
 *     to end once killed
 * @param workspace where jdtls keeps its workspace, and what becomes of it
 */
record JdtlsSettings(
        Path projectRoot,
        List<Path> expectedProjects,
        Duration timeout,
        Duration treeInterval,
        Duration shutdownTimeout,
        JdtlsWorkspace workspace) {

    private static final Duration TREE_INTERVAL = Duration.ofMillis(250);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    JdtlsSettings {
        expectedProjects = expectedProjects.stream()
                .map(project -> project.toAbsolutePath().normalize())
                .toList();
    }

    static JdtlsSettings of(Path projectRoot, List<Path> expectedProjects, Duration timeout) {
        return new JdtlsSettings(
                projectRoot, expectedProjects, timeout, TREE_INTERVAL, SHUTDOWN_TIMEOUT, JdtlsWorkspace.temporary());
    }

    /** The same settings with this workspace. */
    JdtlsSettings withWorkspace(JdtlsWorkspace workspace) {
        return new JdtlsSettings(projectRoot, expectedProjects, timeout, treeInterval, shutdownTimeout, workspace);
    }

    /**
     * The {@code java.*} preferences jdtls is initialized with ({@code initializationOptions.settings}, in the nested
     * form of the JSON settings). They are the ones that decide what the organization of the imports writes (Eclipse JDT
     * LS, {@code Preferences}; the descriptions are those of the {@code vscode-java} settings):
     *
     * <ul>
     *   <li>{@code java.sources.organizeImports.starThreshold} and {@code staticStarThreshold}: the number of imports of
     *       one package (or of one type, for the static ones) from which jdtls writes {@code import p.*;} instead. The
     *       default is 99, which is not "never"; the project bans star imports (Checkstyle {@code AvoidStarImport}), so
     *       both are the largest {@code int};
     *   <li>{@code java.completion.importOrder}: the groups of imports, in order, a group being a package prefix, {@code
     *       "#"} the static imports and {@code ""} every import that no other group takes. The default ({@code java},
     *       {@code javax}, {@code org}, {@code com}) puts a blank line between the groups; the project keeps the static
     *       imports first, a blank line, then one block of all the others in ASCII order (open-java-format), which is
     *       {@code "#"} then {@code ""};
     *   <li>{@code java.templates.typeComment}: the Javadoc jdtls writes above the type of a file it creates (the
     *       snippets of {@code SnippetCompletionProposal} use it). By default it is a Javadoc with the name of the
     *       type, and an empty setting means that default. A comment of white space only, which the evaluation of the template
     *       judges empty ({@code StubUtility.evaluateTemplate}), gives a type with no comment. There is no file header
     *       to turn off: {@code java.templates.fileHeader} is empty by default. {@code
     *       java.codeGeneration.generateComments} is {@code false} by default, and does not govern these snippets;
     *   <li>{@code java.references.includeAccessors}: with the default ({@code true}) a search for the references of a
     *       field also gives the calls of its getter, its setter and its builder ({@code ReferencesHandler}), which are
     *       uses of other methods, so it is {@code false}: the references of a symbol are the places that name it;
     *   <li>{@code java.references.includeDecompiledSources}: the sources of a class file jdtls decompiles to answer a
     *       search. It acts only for a client that declares {@code classFileContentsSupport}, which gabier does not (see
     *       {@link JdtlsCapabilities#extended}): jdtls then answers no place in a class file at all, whatever this says.
     *       It is {@code false} so that the setting does not depend on that.
     *       {@code java.search.scope} stays {@code all}: the test sources are searched too.
     * </ul>
     */
    static Map<String, Object> javaPreferences() {
        return Map.of("java", javaSection());
    }

    private static Map<String, Object> javaSection() {
        Map<String, Object> thresholds =
                Map.of("starThreshold", Integer.MAX_VALUE, "staticStarThreshold", Integer.MAX_VALUE);

        return Map.of(
                "sources", Map.of("organizeImports", thresholds),
                "completion", Map.of("importOrder", List.of("#", "")),
                "templates", Map.of("typeComment", List.of(" ")),
                "references", Map.of("includeAccessors", false, "includeDecompiledSources", false));
    }

    /**
     * The preferences of {@link #javaPreferences} with the clean-ups of the setting {@code java.cleanup.actions}: the
     * identifiers that {@code java/cleanup} runs on a file, in the order of the list ({@code
     * SaveActionHandler.performManualCleanupActions} and {@code CleanUpRegistry} of Eclipse JDT LS). It is sent with
     * {@code workspace/didChangeConfiguration}, together with the preferences of the start, so that none falls back to
     * its default whatever jdtls does with a configuration that lacks it. The clean-ups run on save only with {@code
     * java.saveActions.cleanup}, which stays false, and the client does not declare {@code canUseInternalSettings}
     * (see {@link JdtlsCapabilities#extended}), which would make jdtls read the clean-ups from the JDT UI preferences of
     * the project instead.
     *
     * @param ids the identifiers of the clean-ups, none to clear the setting
     */
    static Map<String, Object> withCleanUps(List<String> ids) {
        Map<String, Object> java = new HashMap<>(javaSection());
        java.put("cleanup", Map.of("actions", List.copyOf(ids)));

        return Map.of("java", java);
    }
}
