package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.Unregistration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The files jdtls asks to have watched, and what changed in them since it was told: no jdtls, no watcher thread. */
class WatchedFilesTest {

    private static final String METHOD = "workspace/didChangeWatchedFiles";

    @TempDir
    Path root;

    private Path greeter;

    @BeforeEach
    void writeTheProject() throws IOException {
        root = root.toRealPath();
        greeter = write("src/Greeter.java", "class Greeter {}\n");
    }

    private Path write(String relative, String text) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        return file;
    }

    /** The modification time moves by whole seconds, so that a rewrite is seen whatever the clock of the disk. */
    private void rewrite(Path file, String text, int seconds) throws IOException {
        FileTime before = Files.getLastModifiedTime(file);
        Files.writeString(file, text);
        Files.setLastModifiedTime(file, FileTime.from(before.toInstant().plusSeconds(seconds)));
    }

    private static Registration registration(String id, JsonObject... watchers) {
        var array = new JsonArray();
        for (JsonObject watcher : watchers) {
            array.add(watcher);
        }
        var options = new JsonObject();
        options.add("watchers", array);
        return new Registration(id, METHOD, options);
    }

    private static JsonObject glob(String pattern) {
        var watcher = new JsonObject();
        watcher.addProperty("globPattern", pattern);
        return watcher;
    }

    private static JsonObject glob(String pattern, int kind) {
        var watcher = glob(pattern);
        watcher.addProperty("kind", kind);
        return watcher;
    }

    private static JsonObject relative(String baseUri, String pattern) {
        var base = new JsonObject();
        base.addProperty("baseUri", baseUri);
        base.addProperty("pattern", pattern);
        var watcher = new JsonObject();
        watcher.add("globPattern", base);
        return watcher;
    }

    private WatchedFiles watching(JsonObject... watchers) {
        var files = WatchedFiles.of(root);
        files.register(registration("1", watchers));
        return files;
    }

    private static List<String> described(List<FileEvent> events) {
        return events.stream()
                .map(event -> event.getType() + " "
                        + Path.of(URI.create(event.getUri())).getFileName())
                .toList();
    }

    @Test
    void reportsAFileChangedSinceTheLastCall() throws IOException {
        var files = watching(glob("**/*.java"));

        rewrite(greeter, "class Greeter { int x; }\n", 5);

        assertThat(described(files.changes())).containsExactly("Changed Greeter.java");
        assertThat(files.changes()).isEmpty();
    }

    @Test
    void reportsAFileWhoseSizeChangedWhateverItsModificationTime() throws IOException {
        var files = watching(glob("**/*.java"));
        FileTime before = Files.getLastModifiedTime(greeter);

        Files.writeString(greeter, "class Greeter { int x; }\n");
        Files.setLastModifiedTime(greeter, before);

        assertThat(described(files.changes())).containsExactly("Changed Greeter.java");
    }

    @Test
    void reportsACreatedFileAndADeletedFileEachOnce() throws IOException {
        var files = watching(glob("**/*.java"));

        write("src/Welcomer.java", "class Welcomer {}\n");
        Files.delete(greeter);

        assertThat(described(files.changes())).containsExactly("Created Welcomer.java", "Deleted Greeter.java");
        assertThat(files.changes()).isEmpty();
    }

    @Test
    void reportsNothingWhenNothingChanged() {
        var files = watching(glob("**/*.java"));

        assertThat(files.changes()).isEmpty();
    }

    @Test
    void reportsAChangeBeforeJdtlsRegisteredItsWatchersOnceItHas() throws IOException {
        var files = WatchedFiles.of(root);
        rewrite(greeter, "class Greeter { int x; }\n", 5);

        assertThat(files.changes()).isEmpty();

        files.register(registration("1", glob("**/*.java")));

        assertThat(described(files.changes())).containsExactly("Changed Greeter.java");
    }

    @Test
    void reportsOnlyTheFilesThatMatchAGlob() throws IOException {
        var files = watching(glob("**/*.java"), glob("**/pom.xml"));

        write("src/notes.txt", "a note\n");
        write("pom.xml", "<project/>\n");

        assertThat(described(files.changes())).containsExactly("Created pom.xml");
    }

    @Test
    void reportsOnlyTheKindsOfChangeAWatcherAsksFor() throws IOException {
        var files = watching(glob("**/*.java", 4));

        write("src/Welcomer.java", "class Welcomer {}\n");
        rewrite(greeter, "class Greeter { int x; }\n", 5);

        assertThat(files.changes()).isEmpty();

        Files.delete(greeter);

        assertThat(described(files.changes())).containsExactly("Deleted Greeter.java");
    }

    @Test
    void asksForTheCreationAndTheChangeOfAFileWhenAWatcherSaysSo() throws IOException {
        var files = watching(glob("**/*.java", 3));

        write("src/Welcomer.java", "class Welcomer {}\n");
        rewrite(greeter, "class Greeter { int x; }\n", 5);

        assertThat(described(files.changes())).containsExactly("Changed Greeter.java", "Created Welcomer.java");
    }

    @Test
    void ignoresARelativePatternAsAFileScanNeverSeesAFolder() throws IOException {
        var files = watching(
                relative(root.getParent().toUri().toString(), root.getFileName().toString()));

        rewrite(greeter, "class Greeter { int x; }\n", 5);

        assertThat(files.changes()).isEmpty();
    }

    @Test
    void ignoresTheRegistrationOfAnotherCapability() throws IOException {
        var files = WatchedFiles.of(root);
        files.register(new Registration("1", "workspace/symbol", "options"));

        rewrite(greeter, "class Greeter { int x; }\n", 5);

        assertThat(files.changes()).isEmpty();
    }

    @Test
    void isRegisteredOnceJdtlsHasAskedForAFileToBeWatchedAndNotBefore() {
        var files = WatchedFiles.of(root);

        assertThat(files.registered()).isFalse();

        files.register(new Registration("1", "workspace/symbol", "options"));

        assertThat(files.registered()).isFalse();

        files.register(registration("2", glob("**/*.java")));

        assertThat(files.registered()).isTrue();

        files.unregister(new Unregistration("2", METHOD));

        assertThat(files.registered()).isFalse();
    }

    @Test
    void forgetsTheWatchersOfAnUnregistration() throws IOException {
        var files = WatchedFiles.of(root);
        files.register(registration("1", glob("**/*.java")));
        files.register(registration("2", glob("**/*.xml")));
        files.unregister(new Unregistration("1", METHOD));

        rewrite(greeter, "class Greeter { int x; }\n", 5);
        write("pom.xml", "<project/>\n");

        assertThat(described(files.changes())).containsExactly("Created pom.xml");
    }

    @Test
    void doesNotTakeALinkForAFile() throws IOException {
        var files = watching(glob("**"));
        Files.createSymbolicLink(root.resolve("link"), greeter);

        assertThat(files.changes()).isEmpty();
    }

    @Test
    void skipsAFolderItCannotReadAndReportsTheOthers() throws IOException {
        Path closed = root.resolve("closed");
        Files.createDirectories(closed);
        Files.setPosixFilePermissions(closed, PosixFilePermissions.fromString("---------"));
        try {
            var files = watching(glob("**/*.java"));

            rewrite(greeter, "class Greeter { int x; }\n", 5);

            assertThat(described(files.changes())).containsExactly("Changed Greeter.java");
        } finally {
            Files.setPosixFilePermissions(closed, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void knowsNothingOfAFolderThatDoesNotExistAndReportsItsFilesOnceItDoes() throws IOException {
        Path later = root.resolve("later");
        var files = WatchedFiles.of(later);
        files.register(registration("1", glob("**/*.java")));

        assertThat(files.changes()).isEmpty();

        Files.createDirectory(later);
        Files.writeString(later.resolve("A.java"), "class A {}\n");

        assertThat(described(files.changes())).containsExactly("Created A.java");
    }
}
