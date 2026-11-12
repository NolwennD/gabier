import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** {@code move-type-to-file}, {@code move}, {@code inline} and {@code create} against the daemon served by the fake language server. */
final class MoveChecks extends Checks {

    MoveChecks(Session session) {
        super(session);
    }

    /**
     * {@code gabier move-type-to-file} against the daemon served by the fake language server: the dry run writes
     * nothing, the type is moved to a file of its own, and a top-level type is refused as a problem object that
     * writes nothing.
     */
    void moveTypeToFileWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("move-type-project");
        Process process = startDaemon(fakeDaemon, project.toString());
        if (process == null) {
            failed();
            return;
        }
        Path shapes = project.resolve("src/main/java/demo/Shapes.java");
        Path circle = project.resolve("src/main/java/demo/Circle.java");
        String file = "move-type-project/src/main/java/demo/Shapes.java";
        String original = Files.readString(shapes);

        Run dryRun = gabier("move-type-to-file", "--dry-run", file, "6:25");
        expect(
                dryRun.exit() == 0
                        && dryRun.err().isEmpty()
                        && dryRun.out().lines().findFirst().orElse("").equals("dry run, nothing written")
                        && dryRun.out().contains("created " + circle)
                        && dryRun.out().contains("+public class Circle {")
                        && Files.readString(shapes).equals(original)
                        && !Files.exists(circle),
                "gabier move-type-to-file --dry-run lists the files and the diff and writes nothing");

        Run topLevel = gabier("move-type-to-file", file, "3:14");
        expect(
                topLevel.exit() == 2
                        && topLevel.out().isEmpty()
                        && topLevel.err().contains("(language-server-not-movable; ")
                        && Files.readString(shapes).equals(original),
                "move-type-to-file of a top-level type is refused as a problem object, exit 2");

        Run json = gabier("--json", "move-type-to-file", "--dry-run", file, "6:25");
        expect(
                json.exit() == 0
                        && json.out().contains("\"created\":[\"" + circle + "\"]")
                        && Files.readString(shapes).equals(original),
                "gabier --json move-type-to-file gives the result as the daemon gave it");

        Run moved = gabier("move-type-to-file", file, "6:25");
        expect(
                moved.exit() == 0
                        && moved.out().contains("created " + circle)
                        && Files.readString(circle).startsWith("package demo;\n\npublic class Circle {\n")
                        && !Files.readString(shapes).contains("class Circle"),
                "gabier move-type-to-file writes the file of the type and takes the type out of its outer class");
        gabier("stop");
        expect(ends(process), "the daemon served by the fake stops after the move of a type");
    }

    /**
     * {@code gabier move} against the daemon served by the fake language server: the dry run writes nothing, not even
     * the folder of the new package, the class is moved to the package with its folder made, and a class already in
     * the package is refused as a problem object that writes nothing.
     */
    void moveToAPackageWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("move-package-project");
        Process process = startDaemon(fakeDaemon, project.toString());
        if (process == null) {
            failed();
            return;
        }
        Path invoice = project.resolve("src/main/java/demo/Invoice.java");
        Path archive = project.resolve("src/main/java/demo/archive");
        Path archived = archive.resolve("Invoice.java");
        String file = "move-package-project/src/main/java/demo/Invoice.java";
        String original = Files.readString(invoice);

        Run dryRun = gabier("move", "--dry-run", file, "demo.archive");
        expect(
                dryRun.exit() == 0
                        && dryRun.err().isEmpty()
                        && dryRun.out().lines().findFirst().orElse("").equals("dry run, nothing written")
                        && dryRun.out().contains("renamed " + invoice + " -> " + archived)
                        && Files.readString(invoice).equals(original)
                        && !Files.exists(archive),
                "gabier move --dry-run lists the files and the diff and writes nothing, not even the folder");

        Run samePackage = gabier("move", file, "demo");
        expect(
                samePackage.exit() == 2
                        && samePackage.out().isEmpty()
                        && samePackage.err().contains("(language-server-not-movable-to-package; ")
                        && Files.readString(invoice).equals(original),
                "move to the package of the class is refused as a problem object, exit 2");

        Run notAPackage = gabier("move", file, "demo..archive");
        expect(
                notAPackage.exit() == 2
                        && notAPackage.out().isEmpty()
                        && notAPackage.err().contains("(editing-not-a-package-name; ")
                        && !Files.exists(archive),
                "move to a name that is not a package is refused as a problem object, exit 2");

        Run json = gabier("--json", "move", "--dry-run", file, "demo.archive");
        expect(
                json.exit() == 0
                        && json.out().contains("\"renamed\":[")
                        && Files.readString(invoice).equals(original)
                        && !Files.exists(archive),
                "gabier --json move gives the result as the daemon gave it");

        Run moved = gabier("move", file, "demo.archive");
        expect(
                moved.exit() == 0
                        && moved.out().contains("renamed " + invoice + " -> " + archived)
                        && !Files.exists(invoice)
                        && Files.readString(archived).startsWith("package demo.archive;\n"),
                "gabier move writes the class in the folder it makes for the package");
        gabier("stop");
        expect(ends(process), "the daemon served by the fake stops after the move to a package");
    }

    /**
     * {@code gabier inline} against the daemon served by the fake language server: the dry run writes nothing, the
     * constant is inlined in the files that use it and its declaration is removed, and a variable assigned twice is
     * refused as a problem object that writes nothing.
     */
    void inlineWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("inline-project");
        Process process = startDaemon(fakeDaemon, project.toString());
        if (process == null) {
            failed();
            return;
        }
        Path prices = project.resolve("src/main/java/demo/Prices.java");
        Path checkout = project.resolve("src/main/java/demo/Checkout.java");
        String file = "inline-project/src/main/java/demo/Prices.java";
        String original = Files.readString(prices);

        Run dryRun = gabier("inline", "--dry-run", file, "4:22");
        expect(
                dryRun.exit() == 0
                        && dryRun.err().isEmpty()
                        && dryRun.out().lines().findFirst().orElse("").equals("dry run, nothing written")
                        && dryRun.out().contains("modified " + checkout)
                        && dryRun.out().contains("-    static final int VAT = 20;")
                        && Files.readString(prices).equals(original),
                "gabier inline --dry-run lists the files and the diff and writes nothing");

        Run twice = gabier("inline", file, "21:13");
        expect(
                twice.exit() == 2
                        && twice.out().isEmpty()
                        && twice.err().contains("(language-server-not-inlinable; ")
                        && Files.readString(prices).equals(original),
                "inline of a variable assigned twice is refused as a problem object, exit 2");

        Run json = gabier("--json", "inline", "--dry-run", file, "4:22");
        expect(
                json.exit() == 0
                        && json.out().contains("\"modified\":[")
                        && Files.readString(prices).equals(original),
                "gabier --json inline gives the result as the daemon gave it");

        Run inlined = gabier("inline", file, "4:22");
        expect(
                inlined.exit() == 0
                        && inlined.out().contains("modified " + prices)
                        && !Files.readString(prices).contains("VAT")
                        && !Files.readString(checkout).contains("VAT"),
                "gabier inline replaces the constant by its value in the files that use it and removes it");
        gabier("stop");
        expect(ends(process), "the daemon served by the fake stops after the inlining");
    }

    /**
     * {@code gabier create} against the daemon served by the fake language server: the dry run writes nothing, the
     * class is created with the package line the server gave, and a second creation of the file, and a name that is
     * not the name of a type, are refused as problem objects that write nothing.
     */
    void createWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("create-project");
        Process process = startDaemon(fakeDaemon, project.toString());
        if (process == null) {
            failed();
            return;
        }
        Path widget = project.resolve("src/main/java/demo/Widget.java");
        String file = "create-project/src/main/java/demo/Widget.java";

        Run dryRun = gabier("create", "--dry-run", file, "class");
        expect(
                dryRun.exit() == 0
                        && dryRun.err().isEmpty()
                        && dryRun.out().lines().findFirst().orElse("").equals("dry run, nothing written")
                        && dryRun.out().contains("created " + widget)
                        && dryRun.out().contains("+public class Widget {")
                        && Files.notExists(widget),
                "gabier create --dry-run lists the file and the diff and writes nothing");

        Run json = gabier("--json", "create", "--dry-run", file, "class");
        expect(
                json.exit() == 0 && json.out().contains("\"created\":[") && Files.notExists(widget),
                "gabier --json create gives the result as the daemon gave it");

        Run kind = gabier("create", file, "annotation");
        expect(
                kind.exit() == 2
                        && kind.out().isEmpty()
                        && kind.err().contains("(invalid-parameter; ")
                        && Files.notExists(widget),
                "create of a kind that is not one of the four is refused as a problem object, exit 2");

        Run created = gabier("create", file, "class");
        expect(
                created.exit() == 0
                        && created.out().contains("created " + widget)
                        && Files.exists(widget)
                        && Files.readString(widget).equals("package demo;\n\npublic class Widget {\n\n}\n"),
                "gabier create writes the file with the package line, the class and no comment");

        Run again = gabier("create", file, "class");
        expect(
                again.exit() == 2
                        && again.out().isEmpty()
                        && again.err().contains("(editing-file-exists; ")
                        && Files.readString(widget).equals("package demo;\n\npublic class Widget {\n\n}\n"),
                "create of a file that exists is refused as a problem object and leaves the file as it was");

        Run name = gabier("create", "create-project/src/main/java/demo/widget-impl.java", "class");
        expect(
                name.exit() == 2
                        && name.out().isEmpty()
                        && name.err().contains("(editing-not-a-type-name; ")
                        && Files.notExists(project.resolve("src/main/java/demo/widget-impl.java")),
                "create of a file whose name is not a type name is refused as a problem object, exit 2");
        gabier("stop");
        expect(ends(process), "the daemon served by the fake stops after the creation");
    }
}
