package fr.craft.gabier.daemon;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The folder where every jdtls of a user shares the indexes of the libraries (the JDK, the jars of {@code ~/.gradle}),
 * so that a new project does not index them again. jdtls writes an index of each external jar to the folder of the
 * system property {@code jdt.core.sharedIndexLocation} once its import is over, and reads the index of a jar from it
 * when a project uses that jar ({@code IndexUtils.copyIndexesToSharedLocation} of Eclipse JDT LS; vscode-java sets the
 * property from {@code java.sharedIndexes.*}). No {@code java.*} setting gives this.
 *
 * <p>There is one folder per version of jdtls, because an index of another version is not readable by it: {@code
 * <cache>/jdtls-<version>/indexes}, where the cache is {@code GABIER_CACHE}, else {@code $XDG_CACHE_HOME/gabier}, else
 * {@code ~/.cache/gabier} (a relative path is no cache folder, and is ignored). The version is the one of the bundle
 * {@code plugins/org.eclipse.jdt.ls.core_<version>.jar} of the distribution of the launcher.
 *
 * @param folder where jdtls keeps the indexes it shares
 */
record SharedIndexes(Path folder) {

    private static final String CACHE_VARIABLE = "GABIER_CACHE";
    private static final String XDG_CACHE_VARIABLE = "XDG_CACHE_HOME";
    private static final String CORE_BUNDLE = "org.eclipse.jdt.ls.core_";

    /**
     * The shared indexes of the jdtls whose launcher is given; none when the distribution does not tell its version
     * (jdtls then keeps its indexes to itself, as it does without the property).
     *
     * @param launcher the {@code bin/jdtls} script of the distribution
     * @param home the home folder of the user, for the default cache
     */
    static Optional<SharedIndexes> of(Path launcher, Map<String, String> environment, Path home) {
        return version(launcher)
                .map(version -> cache(environment, home).resolve("jdtls-" + version + "/indexes"))
                .map(SharedIndexes::new);
    }

    /** The option of the JVM of jdtls that makes it share its indexes through the folder. */
    String jvmArgument() {
        return "-Djdt.core.sharedIndexLocation=" + folder;
    }

    private static Path cache(Map<String, String> environment, Path home) {
        return absolute(environment.get(CACHE_VARIABLE))
                .or(() -> absolute(environment.get(XDG_CACHE_VARIABLE)).map(base -> base.resolve("gabier")))
                .orElseGet(() -> home.resolve(".cache/gabier"));
    }

    private static Optional<Path> absolute(@Nullable String path) {
        return Optional.ofNullable(path)
                .filter(candidate -> !candidate.isBlank())
                .map(Path::of)
                .filter(Path::isAbsolute);
    }

    private static Optional<String> version(Path launcher) {
        Path plugins = launcher.toAbsolutePath().resolveSibling("../plugins");
        // No such folder: the launcher is not in a distribution, there is no version and so no folder.
        String[] bundles =
                plugins.toFile().list((folder, name) -> name.startsWith(CORE_BUNDLE) && name.endsWith(".jar"));

        return Optional.ofNullable(bundles).stream()
                .flatMap(Arrays::stream)
                .sorted()
                .findFirst()
                .map(name -> name.substring(CORE_BUNDLE.length(), name.length() - ".jar".length()));
    }
}
