package eu.gabier.cli.domain;

import eu.gabier.shared.error.domain.Assert;
import java.util.HashSet;
import java.util.List;

/**
 * An operation the daemon serves, written once: the name of its method, what it does, what to know before calling it,
 * the refusals it can answer and its parameters. The command line describes how a person types it (its usage and its
 * example, {@code Command}); any other front end builds what it shows from this.
 *
 * @param method the name of the method of the daemon
 * @param summary one line: what it does
 * @param notes what a person must know before calling it, empty for most of them
 * @param refusals the refusals of the operation, each as the key of its problem, a colon and what it means, the ones
 *     that every call can meet being left to the front end
 * @param parameters its parameters, in the order a call gives them
 */
public record Operation(
        String method, String summary, String notes, List<String> refusals, List<Parameter> parameters) {

    public Operation {
        Assert.field("method", method).notBlank().noWhitespace();
        Assert.notBlank("summary", summary);
        Assert.notNull("notes", notes);
        Assert.field("refusals", refusals).notNull().noNullElement();
        refusals.forEach(refusal -> Assert.notBlank("refusal", refusal));
        Assert.field("parameters", parameters).notNull().noNullElement();
        var names = new HashSet<String>();
        parameters.forEach(parameter -> {
            if (!names.add(parameter.name())) {
                throw new IllegalArgumentException("The parameter " + parameter.name() + " is declared twice");
            }
        });

        refusals = List.copyOf(refusals);
        parameters = List.copyOf(parameters);
    }

    /** The names of the parameters, in order. */
    public List<String> parameterNames() {
        return parameters.stream().map(Parameter::name).toList();
    }
}
