import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** {@code references}, {@code actions}, {@code apply-action} and {@code affected-tests} against the daemon served by the fake language server. */
final class QueryChecks extends Checks {

    QueryChecks(Session session) {
        super(session);
    }

    /**
     * {@code gabier references} against the daemon served by the fake language server: the uses of a type, in the
     * text and as JSON, the declaration of a method listed apart, a method nothing calls (no reference, exit 0), and
     * a position on nothing refused as a problem object. Nothing is written.
     */
    void referencesWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("references-project");
        Process process = startDaemon(fakeDaemon, project.toString());
        if (process == null) {
            failed();
            return;
        }
        Path ledger = project.resolve("src/main/java/demo/Ledger.java");
        Path bank = project.resolve("src/main/java/demo/app/Bank.java");
        String file = "references-project/src/main/java/demo/Ledger.java";
        String before = Files.readString(ledger);

        Run uses = gabier("references", file, "3:14");
        expect(
                uses.exit() == 0
                        && uses.err().isEmpty()
                        && uses.out().lines().count() == 5
                        && uses.out().lines().findFirst().orElse("").equals(bank + ":3:8: import demo.Ledger;")
                        && uses.out().contains("LedgerTest.java:5:9: Ledger ledger = new Ledger();"),
                "gabier references lists the uses of a type, with positions from 1 and the source lines");

        Run json = gabier("--json", "references", file, "3:14");
        expect(
                json.exit() == 0
                        && json.out().startsWith("{\"declarations\":[],\"references\":[{\"file\":\"" + bank + "\"")
                        && json.out().contains("\"line\":2,\"column\":7,\"source\":\"import demo.Ledger;\""),
                "gabier --json references gives the result as the daemon gave it, with positions from 0");

        Run declaration = gabier(
                "references", "--include-declaration", "references-project/src/main/java/demo/app/Bank.java", "8:16");
        expect(
                declaration.exit() == 0
                        && declaration.out().lines().findFirst().orElse("").equals("declaration:")
                        && declaration.out().contains(ledger + ":10:17: public void deposit(int amount) {")
                        && declaration.out().contains("references:"),
                "gabier references --include-declaration lists the declaration apart");

        Run nothing = gabier("references", file, "19:9");
        expect(
                nothing.exit() == 0 && nothing.err().isEmpty() && nothing.out().equals("no references"),
                "gabier references says that a symbol nothing uses has no reference, exit 0");

        Run emptyJson = gabier("--json", "references", file, "19:9");
        expect(
                emptyJson.exit() == 0 && emptyJson.out().equals("{\"declarations\":[],\"references\":[]}"),
                "gabier --json references gives two empty lists for a symbol nothing uses");

        Run blank = gabier("references", file, "5:1");
        expect(
                blank.exit() == 2 && blank.out().isEmpty() && blank.err().contains("(language-server-no-such-symbol; "),
                "references of a position on nothing is refused as a problem object, exit 2");

        Run dryRun = gabier("references", "--dry-run", file, "3:14");
        expect(
                dryRun.exit() == 2 && dryRun.out().isEmpty() && dryRun.err().contains("(usage-error"),
                "references has no dry run: the option is a usage error");
        expect(Files.readString(ledger).equals(before) && Files.exists(bank), "gabier references writes nothing");
        gabier("stop");
        expect(ends(process), "the daemon served by the fake stops after the references");
    }

    /**
     * {@code gabier actions} and {@code gabier apply-action} against the daemon served by the fake language server:
     * the actions of a range as text and as JSON, a blank line that offers none (exit 0), an action applied by its
     * title after a dry run that writes nothing, a title that is not offered and an action that is a command for an
     * editor, both refused as problem objects, and the usage errors.
     */
    void actionsWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("actions-project");
        Process process = startDaemon(fakeDaemon, project.toString());
        if (process == null) {
            failed();
            return;
        }
        Path offers = project.resolve("src/main/java/demo/Offers.java");
        String file = "actions-project/src/main/java/demo/Offers.java";
        String title = "Import 'List' (java.util)";
        String before = Files.readString(offers);

        Run offered = gabier("actions", file, "4:5", "4:9");
        expect(
                offered.exit() == 0 && offered.err().isEmpty() && offered.out().equals("1 quickfix " + title),
                "gabier actions lists the actions of a range, with their number, kind and title");

        Run json = gabier("--json", "actions", file, "4:5", "4:9");
        expect(
                json.exit() == 0
                        && json.out().equals("{\"actions\":[{\"kind\":\"quickfix\",\"title\":\"" + title + "\"}]}"),
                "gabier --json actions gives the result as the daemon gave it");

        Run nothing = gabier("actions", file, "7:1", "7:1");
        expect(
                nothing.exit() == 0 && nothing.err().isEmpty() && nothing.out().equals("no actions"),
                "gabier actions says that a range with nothing offered has no actions, exit 0");

        String meta = "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
                + "\"io.modelcontextprotocol/clientCapabilities\":{}}";
        // A tool takes an absolute path: the server does not know the folder the session works in.
        String range = "\"file\":\"" + offers + "\",\"startLine\":4,\"startColumn\":5,\"endLine\":4,\"endColumn\":9";
        String calls = String.join(
                        "\n",
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"actions\","
                                + "\"arguments\":{" + range + "}," + meta + "}}",
                        "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"apply-action\","
                                + "\"arguments\":{" + range + ",\"title\":\"No such action\",\"dryRun\":false}," + meta
                                + "}}")
                + "\n";
        Run mcp = runBinaryReading(cli, directory, List.of("GABIER_SOCKET", socket.toString()), calls, "mcp");
        List<String> answers = mcp.out().lines().toList();
        System.out.println("gabier mcp (tools/call) -> " + answers.size() + " answers (exit " + mcp.exit()
                + "), stderr \"" + mcp.err() + "\"");
        // The calls run side by side and are answered as they end: each answer is found by its id.
        expect(
                mcp.exit() == 0
                        && mcp.err().isEmpty()
                        && answers.size() == 2
                        && answerOf(answers, "1").contains("\"text\":\"1 quickfix " + title + "\"")
                        && answerOf(answers, "1").contains("\"structuredContent\":{\"actions\":[{\"kind\":\"quickfix\"")
                        && answerOf(answers, "1").contains("\"isError\":false")
                        && answerOf(answers, "2").contains("\"isError\":true")
                        && answerOf(answers, "2").contains("\"key\":\"editing-no-such-code-action\"")
                        && answerOf(answers, "2").contains("Usage and other ways: gabier help apply-action"),
                "gabier mcp runs a tool against the daemon: the text of the command line and the result of the daemon,"
                        + " a refusal as a failed call with the pointer to the help, nothing else on the output");

        Run dryRun = gabier("apply-action", "--dry-run", file, "4:5", "4:9", title);
        expect(
                dryRun.exit() == 0
                        && dryRun.out().lines().findFirst().orElse("").equals("dry run, nothing written")
                        && dryRun.out().contains("modified " + offers)
                        && dryRun.out().contains("+import java.util.List;")
                        && Files.readString(offers).equals(before),
                "gabier apply-action --dry-run shows the change and writes nothing");

        Run unknown = gabier("apply-action", file, "4:5", "4:9", "No such action");
        expect(
                unknown.exit() == 2
                        && unknown.out().isEmpty()
                        && unknown.err().contains("(editing-no-such-code-action; ")
                        && Files.readString(offers).equals(before),
                "apply-action of a title the range does not offer is refused as a problem object, exit 2");

        Run command = gabier("apply-action", file, "4:18", "4:23", "Change signature for 'names'");
        expect(
                command.exit() == 2
                        && command.out().isEmpty()
                        && command.err().contains("(language-server-not-applicable; ")
                        && Files.readString(offers).equals(before),
                "apply-action of an action that is a command for an editor is refused, nothing written");

        Run listDryRun = gabier("actions", "--dry-run", file, "4:5", "4:9");
        expect(
                listDryRun.exit() == 2 && listDryRun.err().contains("(usage-error"),
                "actions has no dry run: the option is a usage error");

        Run applied = gabier("apply-action", file, "4:5", "4:9", title);
        expect(
                applied.exit() == 0
                        && applied.out().startsWith("modified " + offers)
                        && Files.readString(offers).contains("import java.util.List;"),
                "gabier apply-action applies the action of the title on disk");
        gabier("stop");
        expect(ends(process), "the daemon served by the fake stops after the actions");
    }

    /**
     * {@code gabier affected-tests} against the daemon served by the fake language server: the shop of the project
     * is compiled with javac, and the tests that a change to a class affects are the ones the classes say, with the
     * filters of the project applied. The files are given relative to the directory the cli runs in.
     */
    void affectedTestsWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("affected-project");
        Process process = startDaemon(fakeDaemon, project.toString());
        if (process == null) {
            failed();
            return;
        }
        String greeter = "affected-project/src/main/java/shop/Greeter.java";

        Run used = gabier("affected-tests", greeter);
        expect(
                used.exit() == 0
                        && used.err().isEmpty()
                        && used.out().equals("shop.ArchitectureTest\nshop.FrenchGreeterTest\nshop.GreeterTest"),
                "gabier affected-tests lists the tests that use a class, the filtered one left out, one per line");

        Run json = gabier("--json", "affected-tests", greeter);
        expect(
                json.exit() == 0
                        && json.out()
                                .equals("{\"tests\":[\"shop.ArchitectureTest\",\"shop.FrenchGreeterTest\","
                                        + "\"shop.GreeterTest\"]}"),
                "gabier --json affected-tests gives the result as the daemon gave it");

        Run notJava = gabier("affected-tests", "affected-project/README.md");
        expect(
                notJava.exit() == 0 && notJava.err().isEmpty() && notJava.out().equals("no affected tests"),
                "a file that is not Java affects no test: no affected tests, exit 0");

        Run missing = gabier("affected-tests", "affected-project/src/main/java/shop/Gone.java");
        expect(
                missing.exit() == 2 && missing.out().isEmpty() && missing.err().contains("(testing-file-missing; "),
                "a Java file that does not exist is refused as a problem object, exit 2");

        // The fake has no jdtls to be told of edits: the lines of this file are the Java files that changed.
        Files.writeString(project.resolve(".gabier-fake-changed"), "src/main/java/shop/Greeter.java\n");
        Run changed = gabier("affected-tests");
        expect(
                changed.exit() == 0
                        && changed.err().isEmpty()
                        && changed.out().equals("shop.ArchitectureTest\nshop.FrenchGreeterTest\nshop.GreeterTest"),
                "gabier affected-tests with no file lists the tests of the files that changed, one per line");

        Run answeredOnce = gabier("affected-tests");
        expect(
                answeredOnce.exit() == 0
                        && answeredOnce.err().isEmpty()
                        && answeredOnce.out().equals("no affected tests"),
                "a second gabier affected-tests with no new change prints no affected tests, exit 0");

        Files.writeString(project.resolve(".gabier-fake-changed"), "src/main/java/shop/Unused.java\n");
        Run changedAsJson = gabier("--json", "affected-tests");
        expect(
                changedAsJson.exit() == 0 && changedAsJson.out().equals("{\"tests\":[\"shop.ArchitectureTest\"]}"),
                "gabier --json affected-tests with no file gives the result as the daemon gave it");

        gabier("stop");
        expect(ends(process), "the daemon served by the fake stops after the affected tests");
    }
}
