package eu.gabier.cli.infrastructure.primary;

/**
 * The guide of the improvements, {@code gabier help guide}: the page {@code docs/improve.md}, which the build copies
 * into the resources of the cli (and the native image keeps, see {@code resource-config.json}), so that it is read
 * without a clone of the repository.
 */
final class GuideTopic {

    /** The word after {@code help} that asks for the guide. */
    static final String TOPIC = "guide";

    /** The option after {@code help} that asks the guide a question, as {@code --for=<task>}. */
    static final String FOR = "--for";

    /** What the guide is for, in one line. */
    static final String SUMMARY = "which problem each improvement of the code solves, and how to ask for it";

    private static final String RESOURCE = "improve.md";

    private GuideTopic() {}

    /** The text of the guide. */
    static String text() {
        return TextResource.read(RESOURCE, GuideTopic.class.getResourceAsStream(RESOURCE));
    }
}
