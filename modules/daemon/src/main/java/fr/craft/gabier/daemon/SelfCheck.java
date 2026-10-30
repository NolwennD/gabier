package fr.craft.gabier.daemon;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;

/**
 * Proves that the daemon runs on the module path, with LSP4J as a real module and Gson able to reach into it. Gson
 * reflects on the LSP4J types: it can fail on the module path (a closed module) while passing on the class path, so the
 * check also refuses to pass on the class path.
 *
 * @param daemonModule the name of the module of the daemon, {@code null} when it is on the class path
 * @param position the name of the module of {@link Position}, {@code null} when it is on the class path
 */
record SelfCheck(@Nullable String daemonModule, @Nullable String position) {

    private static final String LSP4J_MODULE = "org.eclipse.lsp4j";
    private static final String POSITION_JSON = "{\"line\":1,\"character\":2}";

    static SelfCheck ofThisRuntime() {
        return new SelfCheck(
                SelfCheck.class.getModule().getName(),
                Position.class.getModule().getName());
    }

    static String positionAsJson() {
        return new Gson().toJson(new Position(1, 2));
    }

    List<String> problems() {
        return problems(new Gson()::toJson);
    }

    List<String> problems(Function<Object, String> toJson) {
        List<String> problems = new ArrayList<>();
        if (daemonModule == null) {
            problems.add("the daemon is not in a named module: it runs on the class path");
        }
        if (position == null) {
            problems.add("Position is not in the named module " + LSP4J_MODULE + ": it is on the class path");
        } else if (!position.equals(LSP4J_MODULE)) {
            problems.add("Position is in the module " + position + ", not in the named module " + LSP4J_MODULE);
        }
        try {
            String json = toJson.apply(new Position(1, 2));
            if (!POSITION_JSON.equals(json)) {
                problems.add("Gson wrote " + json + " instead of " + POSITION_JSON);
            }
        } catch (RuntimeException e) {
            problems.add("Gson cannot serialize a Position: " + e);
        }
        return problems;
    }

    String summary() {
        return "the daemon is in " + describe(daemonModule) + ", Position in " + describe(position) + ", Gson wrote "
                + positionAsJson();
    }

    private static String describe(@Nullable String module) {
        return module == null ? "no named module" : "the module " + module;
    }
}
