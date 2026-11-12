import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The four {@code extract-*} commands against the daemon served by the fake language server. */
final class ExtractChecks extends Checks {

    ExtractChecks(Session session) {
        super(session);
    }

    /**
     * {@code gabier extract-variable} against the daemon served by the fake language server: the dry run writes
     * nothing, the variable is declared under the name given and used in place of the expression, a selection that
     * is not an expression and a name that is not valid are refused as problem objects and write nothing.
     */
    void extractVariableWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("extract-project");
        Process process = startDaemon(fakeDaemon, project.toString());
        if (process == null) {
            failed();
            return;
        }
        Path greeter = project.resolve("src/main/java/demo/Greeter.java");
        String file = "extract-project/src/main/java/demo/Greeter.java";
        String original = Files.readString(greeter);

        Run dryRun = gabier("extract-variable", "--dry-run", file, "7:26", "7:48", "joined");
        expect(
                dryRun.exit() == 0
                        && dryRun.err().isEmpty()
                        && dryRun.out().lines().findFirst().orElse("").equals("dry run, nothing written")
                        && dryRun.out().contains("modified " + greeter)
                        && dryRun.out().contains("+        String joined = greeting + \", \" + name;")
                        && dryRun.out().contains("+        String message = joined;")
                        && Files.readString(greeter).equals(original),
                "gabier extract-variable --dry-run lists the file and the diff and writes nothing");

        Run notAnExpression = gabier("extract-variable", file, "8:9", "8:15", "joined");
        expect(
                notAnExpression.exit() == 2
                        && notAnExpression.out().isEmpty()
                        && notAnExpression.err().contains("(language-server-not-extractable; ")
                        && Files.readString(greeter).equals(original),
                "extract-variable of a selection that is not an expression is refused as a problem object, exit 2");

        Run invalidName = gabier("extract-variable", file, "7:26", "7:48", "1st");
        expect(
                invalidName.exit() == 2
                        && invalidName.out().isEmpty()
                        && invalidName.err().contains("(language-server-invalid-name; ")
                        && Files.readString(greeter).equals(original),
                "extract-variable to a name that is not an identifier is refused as a problem object, exit 2");

        Run json = gabier("--json", "extract-variable", "--dry-run", file, "7:26", "7:48", "joined");
        expect(
                json.exit() == 0
                        && json.out()
                                .startsWith("{\"created\":[],\"renamed\":[],\"deleted\":[],\"modified\":[\"" + greeter
                                        + "\"],\"diff\":\"")
                        && Files.readString(greeter).equals(original),
                "gabier --json extract-variable gives the result as the daemon gave it");

        Run extracted = gabier("extract-variable", file, "7:26", "7:48", "joined");
        String text = Files.readString(greeter);
        expect(
                extracted.exit() == 0
                        && extracted.out().contains("modified " + greeter)
                        && text.contains("        String joined = greeting + \", \" + name;\n"
                                + "        String message = joined;\n"),
                "gabier extract-variable writes the file, with the variable declared under the name given");
        gabier("stop");
        expect(ends(process), "the daemon served by the fake stops after the extractions");
    }

    /**
     * {@code gabier extract-method} against the daemon served by the fake language server: the dry run writes
     * nothing, the method is declared under the name given and called in place of the statement, a selection that
     * cannot be extracted and a name that is not valid are refused as problem objects and write nothing.
     */
    void extractMethodWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("extract-method-project");
        Process process = startDaemon(fakeDaemon, project.toString());
        if (process == null) {
            failed();
            return;
        }
        Path totals = project.resolve("src/main/java/demo/Totals.java");
        String file = "extract-method-project/src/main/java/demo/Totals.java";
        String original = Files.readString(totals);

        Run dryRun = gabier("extract-method", "--dry-run", file, "6:9", "6:47", "printCount");
        expect(
                dryRun.exit() == 0
                        && dryRun.err().isEmpty()
                        && dryRun.out().lines().findFirst().orElse("").equals("dry run, nothing written")
                        && dryRun.out().contains("modified " + totals)
                        && dryRun.out().contains("+        printCount(count);")
                        && dryRun.out().contains("+    private void printCount(int count) {")
                        && Files.readString(totals).equals(original),
                "gabier extract-method --dry-run lists the file and the diff and writes nothing");

        Run notExtractable = gabier("extract-method", file, "6:31", "7:21", "partial");
        expect(
                notExtractable.exit() == 2
                        && notExtractable.out().isEmpty()
                        && notExtractable.err().contains("(language-server-not-extractable; ")
                        && Files.readString(totals).equals(original),
                "extract-method of half a statement is refused as a problem object, exit 2");

        Run invalidName = gabier("extract-method", file, "6:9", "6:47", "1st");
        expect(
                invalidName.exit() == 2
                        && invalidName.out().isEmpty()
                        && invalidName.err().contains("(language-server-invalid-name; ")
                        && Files.readString(totals).equals(original),
                "extract-method to a name that is not an identifier is refused as a problem object, exit 2");

        Run json = gabier("--json", "extract-method", "--dry-run", file, "6:9", "6:47", "printCount");
        expect(
                json.exit() == 0
                        && json.out()
                                .startsWith("{\"created\":[],\"renamed\":[],\"deleted\":[],\"modified\":[\"" + totals
                                        + "\"],\"diff\":\"")
                        && Files.readString(totals).equals(original),
                "gabier --json extract-method gives the result as the daemon gave it");

        Run extracted = gabier("extract-method", file, "6:9", "6:47", "printCount");
        String text = Files.readString(totals);
        expect(
                extracted.exit() == 0
                        && extracted.out().contains("modified " + totals)
                        && text.contains("        printCount(count);\n")
                        && text.contains("    private void printCount(int count) {\n"),
                "gabier extract-method writes the file, with the method declared under the name given");
        gabier("stop");
        expect(ends(process), "the daemon served by the fake stops after the method extractions");
    }

    /**
     * {@code gabier extract-constant} against the daemon served by the fake language server: the dry run writes
     * nothing, the constant is declared under the name given and used in place of the expression, an expression that
     * reads a local variable and a name that is not valid are refused as problem objects and write nothing.
     */
    void extractConstantWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("extract-constant-project");
        Process process = startDaemon(fakeDaemon, project.toString());
        if (process == null) {
            failed();
            return;
        }
        Path totals = project.resolve("src/main/java/demo/Totals.java");
        String file = "extract-constant-project/src/main/java/demo/Totals.java";
        String original = Files.readString(totals);

        Run dryRun = gabier("extract-constant", "--dry-run", file, "6:28", "6:37", "COUNT_LABEL");
        expect(
                dryRun.exit() == 0
                        && dryRun.err().isEmpty()
                        && dryRun.out().lines().findFirst().orElse("").equals("dry run, nothing written")
                        && dryRun.out().contains("modified " + totals)
                        && dryRun.out().contains("+    private static final String COUNT_LABEL = \"count: \";")
                        && dryRun.out().contains("+        System.out.println(COUNT_LABEL + count);")
                        && Files.readString(totals).equals(original),
                "gabier extract-constant --dry-run lists the file and the diff and writes nothing");

        Run notAConstant = gabier("extract-constant", file, "6:28", "6:45", "COUNT_LABEL");
        expect(
                notAConstant.exit() == 2
                        && notAConstant.out().isEmpty()
                        && notAConstant.err().contains("(language-server-not-extractable; ")
                        && Files.readString(totals).equals(original),
                "extract-constant of an expression that reads a local variable is refused as a problem object, exit 2");

        Run invalidName = gabier("extract-constant", file, "6:28", "6:37", "1ST");
        expect(
                invalidName.exit() == 2
                        && invalidName.out().isEmpty()
                        && invalidName.err().contains("(language-server-invalid-name; ")
                        && Files.readString(totals).equals(original),
                "extract-constant to a name that is not an identifier is refused as a problem object, exit 2");

        Run json = gabier("--json", "extract-constant", "--dry-run", file, "6:28", "6:37", "COUNT_LABEL");
        expect(
                json.exit() == 0
                        && json.out()
                                .startsWith("{\"created\":[],\"renamed\":[],\"deleted\":[],\"modified\":[\"" + totals
                                        + "\"],\"diff\":\"")
                        && Files.readString(totals).equals(original),
                "gabier --json extract-constant gives the result as the daemon gave it");

        Run extracted = gabier("extract-constant", file, "6:28", "6:37", "COUNT_LABEL");
        String text = Files.readString(totals);
        expect(
                extracted.exit() == 0
                        && extracted.out().contains("modified " + totals)
                        && text.contains("    private static final String COUNT_LABEL = \"count: \";\n")
                        && text.contains("        System.out.println(COUNT_LABEL + count);\n"),
                "gabier extract-constant writes the file, with the constant declared under the name given");
        gabier("stop");
        expect(ends(process), "the daemon served by the fake stops after the constant extractions");
    }

    /**
     * {@code gabier extract-field} against the daemon served by the fake language server: the dry run writes
     * nothing, the field is declared under the name given, assigned before the statement and used in place of the
     * expression, a selection that is not an expression and a name that is not valid are refused as problem objects
     * and write nothing.
     */
    void extractFieldWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("extract-field-project");
        Process process = startDaemon(fakeDaemon, project.toString());
        if (process == null) {
            failed();
            return;
        }
        Path totals = project.resolve("src/main/java/demo/Totals.java");
        Path greeter = project.resolve("src/main/java/demo/Greeter.java");
        String file = "extract-field-project/src/main/java/demo/Totals.java";
        String greeterFile = "extract-field-project/src/main/java/demo/Greeter.java";
        String original = Files.readString(totals);

        Run dryRun = gabier("extract-field", "--dry-run", file, "6:28", "6:37", "label");
        expect(
                dryRun.exit() == 0
                        && dryRun.err().isEmpty()
                        && dryRun.out().lines().findFirst().orElse("").equals("dry run, nothing written")
                        && dryRun.out().contains("modified " + totals)
                        && dryRun.out().contains("+    private String label;")
                        && dryRun.out().contains("+        System.out.println(label + count);")
                        && Files.readString(totals).equals(original),
                "gabier extract-field --dry-run lists the file and the diff and writes nothing");

        Run notAnExpression = gabier("extract-field", greeterFile, "8:9", "8:15", "label");
        expect(
                notAnExpression.exit() == 2
                        && notAnExpression.out().isEmpty()
                        && notAnExpression.err().contains("(language-server-not-extractable; ")
                        && Files.readString(totals).equals(original)
                        && !Files.readString(greeter).contains("label"),
                "extract-field of a selection that is not an expression is refused as a problem object, exit 2");

        Run invalidName = gabier("extract-field", file, "6:28", "6:37", "1st");
        expect(
                invalidName.exit() == 2
                        && invalidName.out().isEmpty()
                        && invalidName.err().contains("(language-server-invalid-name; ")
                        && Files.readString(totals).equals(original),
                "extract-field to a name that is not an identifier is refused as a problem object, exit 2");

        Run json = gabier("--json", "extract-field", "--dry-run", file, "6:28", "6:37", "label");
        expect(
                json.exit() == 0
                        && json.out()
                                .startsWith("{\"created\":[],\"renamed\":[],\"deleted\":[],\"modified\":[\"" + totals
                                        + "\"],\"diff\":\"")
                        && Files.readString(totals).equals(original),
                "gabier --json extract-field gives the result as the daemon gave it");

        Run extracted = gabier("extract-field", file, "6:28", "6:37", "label");
        String text = Files.readString(totals);
        expect(
                extracted.exit() == 0
                        && extracted.out().contains("modified " + totals)
                        && text.contains("    private String label;\n")
                        && text.contains("        label = \"count: \";\n        System.out.println(label + count);\n"),
                "gabier extract-field writes the file, with the field declared under the name given");
        gabier("stop");
        expect(ends(process), "the daemon served by the fake stops after the field extractions");
    }
}
