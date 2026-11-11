package eu.gabier.testing.domain;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * The classes an {@code infinitest.filters} file leaves out of the tests to run, read as Infinitest reads it: one line
 * is one directive, and a pattern is a regular expression that must match the whole binary name of the class ({@code
 * app.GreeterTest$Slow} for a nested class).
 *
 * <ul>
 *   <li>{@code include <patterns>}: only the classes that match one of them are kept (no {@code include} line keeps all);
 *   <li>{@code exclude <patterns>}, and a line that is no directive, which Infinitest reads as an {@code exclude}: the
 *       classes that match are left out;
 *   <li>the patterns of a line are separated by commas, and white space around them is dropped;
 *   <li>a line that starts with {@code #} or {@code !} is a comment;
 *   <li>the lines about groups and listeners ({@code includeGroups}, {@code excludeGroups}, {@code testngListeners}, and
 *       the legacy {@code ## groups=}, {@code ## excluded-groups=}, {@code ## listeners=}) are read and left out of
 *       the scope: they say how a test runner runs the tests, and nothing about which tests are affected.
 * </ul>
 */
final class TestFilters {

    private static final Pattern INCLUDE = Pattern.compile("include\\s+(.+)");
    private static final Pattern EXCLUDE = Pattern.compile("exclude\\s+(.+)");
    private static final List<Pattern> OUT_OF_SCOPE = Stream.of(
                    "includeGroups\\s+(.+)",
                    "excludeGroups\\s+(.+)",
                    "testngListeners\\s+(.+)",
                    "^\\s*#+\\s?groups\\s?=\\s?(.+)",
                    "^\\s*#+\\s?excluded-groups\\s?=\\s?(.+)",
                    "^\\s*#+\\s?listeners\\s?=\\s?(.+)")
            .map(Pattern::compile)
            .toList();

    private final List<Pattern> included;
    private final List<Pattern> excluded;

    private TestFilters(List<Pattern> included, List<Pattern> excluded) {
        this.included = included;
        this.excluded = excluded;
    }

    /** No filter: no class is left out. */
    static TestFilters none() {
        return new TestFilters(List.of(), List.of());
    }

    /**
     * Reads the text of a filters file.
     *
     * @param file where the text comes from, named by the refusal
     * @throws TestingException if a pattern is not a regular expression
     */
    static TestFilters parse(Path file, String text) {
        List<Pattern> included = new ArrayList<>();
        List<Pattern> excluded = new ArrayList<>();
        List<String> lines = text.lines().toList();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            var include = INCLUDE.matcher(line);
            var exclude = EXCLUDE.matcher(line);
            if (include.matches()) {
                included.addAll(patterns(file, index + 1, include.group(1)));
            } else if (exclude.matches()) {
                excluded.addAll(patterns(file, index + 1, exclude.group(1)));
            } else if (!isOutOfScope(line) && !isComment(line)) {
                excluded.addAll(patterns(file, index + 1, line));
            }
        }

        return new TestFilters(included, excluded);
    }

    /** Whether the filters leave the class out: it is not included, or it is excluded. */
    boolean excludes(String className) {
        if (!included.isEmpty() && !matchesOne(included, className)) {
            return true;
        }

        return matchesOne(excluded, className);
    }

    private static boolean isOutOfScope(String line) {
        return OUT_OF_SCOPE.stream()
                .anyMatch(directive -> directive.matcher(line).matches());
    }

    private static boolean isComment(String line) {
        return line.startsWith("!") || line.startsWith("#");
    }

    private static boolean matchesOne(List<Pattern> patterns, String className) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(className).matches());
    }

    private static List<Pattern> patterns(Path file, int line, String patterns) {
        List<Pattern> compiled = new ArrayList<>();
        for (String pattern : patterns.split(",", -1)) {
            String trimmed = pattern.trim();
            if (!trimmed.isEmpty()) {
                compiled.add(compile(file, line, trimmed));
            }
        }

        return compiled;
    }

    private static Pattern compile(Path file, int line, String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException invalid) {
            throw TestingException.invalidTestFilter(file, line, pattern, invalid.getDescription());
        }
    }
}
