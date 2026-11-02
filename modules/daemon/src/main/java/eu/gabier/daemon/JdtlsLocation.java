package eu.gabier.daemon;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Where the daemon finds the jdtls launcher, in this order: the path {@code GABIER_JDTLS} gives (used as it is, even
 * when nothing is there, so that the failure names it); the pinned jdtls that {@code mise run jdtls:install} installs,
 * {@code ~/.local/share/jdtls/<pinned version>/bin/jdtls}; a {@code jdtls} on the {@code PATH}.
 *
 * <p>The pinned version is the {@code jdtls_version} of {@code mise.toml}, which the build writes in the resource
 * {@code jdtls.properties}: the number is written once.
 */
final class JdtlsLocation {

    private static final String RESOURCE = "jdtls.properties";
    private static final String VARIABLE = "GABIER_JDTLS";
    private static final String LAUNCHER = "jdtls";
    private static final Pattern VERSION = Pattern.compile("\\d+(\\.\\d+)*");
    private static final Pattern PATH_SEPARATOR = Pattern.compile(Pattern.quote(File.pathSeparator));

    /** The version of jdtls the project pins. */
    static final String PINNED_VERSION = read(JdtlsLocation.class.getResourceAsStream(RESOURCE));

    private JdtlsLocation() {}

    /**
     * The launcher to start.
     *
     * @param environment the environment of the daemon
     * @param home the home folder of the user
     * @throws IOException when there is no launcher: the message names the three places looked at and the install
     */
    static Path launcher(Map<String, String> environment, Path home) throws IOException {
        var given = environment.getOrDefault(VARIABLE, "");
        if (!given.isBlank()) {
            return Path.of(given);
        }
        var installed = home.resolve(".local/share/jdtls")
                .resolve(PINNED_VERSION)
                .resolve("bin")
                .resolve(LAUNCHER);
        if (runnable(installed)) {
            return installed;
        }
        for (var entry : PATH_SEPARATOR.split(environment.getOrDefault("PATH", ""))) {
            if (!entry.isEmpty() && runnable(Path.of(entry, LAUNCHER))) {
                return Path.of(entry, LAUNCHER);
            }
        }

        throw new IOException("no jdtls found: " + VARIABLE + " is not set, " + installed + " is not installed, and no "
                + LAUNCHER + " is on the PATH; run `mise run jdtls:install` to install the pinned jdtls");
    }

    private static boolean runnable(Path file) {
        return Files.isRegularFile(file) && Files.isExecutable(file);
    }

    static String read(@Nullable InputStream resource) {
        if (resource == null) {
            throw new IllegalStateException("The resource " + RESOURCE + " is missing from the build");
        }

        var properties = new Properties();
        try (resource) {
            properties.load(resource);
        } catch (IOException e) {
            throw new UncheckedIOException("The resource " + RESOURCE + " cannot be read", e);
        }
        var version = properties.getProperty("version");
        if (version == null || !VERSION.matcher(version).matches()) {
            throw new IllegalStateException("The resource " + RESOURCE + " has no version of jdtls");
        }

        return version;
    }
}
