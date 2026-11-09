package eu.gabier.cli.domain;

import java.util.ArrayList;
import java.util.List;

/** The parameters that several operations share, written once for {@link Operations}. */
final class Parameters {

    static final Parameter FILE = Parameter.string("file", "the Java file, a path in the project");
    static final Parameter DRY_RUN = Parameter.bool("dryRun", "true to say what would change and write nothing");
    static final Parameter LINE = Parameter.position("line", "the line of the position, counted from 1");
    static final Parameter COLUMN = Parameter.position("column", "the column of the position, counted from 1");
    static final Parameter NEW_NAME = Parameter.string("newName", "the new name");
    static final Parameter NAME = Parameter.string("name", "the name to give to what is created");

    private static final List<Parameter> RANGE = List.of(
            Parameter.position("startLine", "the line where the range starts, counted from 1"),
            Parameter.position("startColumn", "the column where the range starts, counted from 1"),
            Parameter.position("endLine", "the line where the range ends, counted from 1"),
            Parameter.position("endColumn", "the column where the range ends, counted from 1, the end excluded"));

    private Parameters() {}

    /** The parameters of a call on a range: the file, then the four numbers of the range, then the rest. */
    static List<Parameter> onARange(Parameter... after) {
        var parameters = new ArrayList<Parameter>();
        parameters.add(FILE);
        parameters.addAll(RANGE);
        parameters.addAll(List.of(after));

        return parameters;
    }
}
