package fr.craft.gabier.editing.domain;

/**
 * Why files cannot be moved to a package, when that is decided before the language server is asked or from its answer:
 * the server does not refuse these itself (it skips a file it does not know, moves a file onto a file of its name, and
 * answers no change for a package whose folder it does not know), or because the folder of the package could not be
 * made for the request.
 */
public enum NotMovableReason {
    ALREADY_IN_PACKAGE("already-in-package", "the file is already in that package"),
    NAME_TAKEN("name-taken", "a file of that name exists in that package"),
    NOT_A_JAVA_SOURCE("not-a-java-source", "the file is not a Java source (.java)"),
    OUTSIDE_SOURCE_FOLDERS("outside-source-folders", "the file is under no source folder of the project"),
    SEVERAL_SOURCE_FOLDERS("several-source-folders", "the files are not all in one source folder"),
    FOLDER_NOT_MADE("folder-not-made", "the folder of the package cannot be made"),
    NOTHING_TO_MOVE("nothing-to-move", "the language server has no change to make");

    private final String name;
    private final String sentence;

    NotMovableReason(String name, String sentence) {
        this.name = name;
        this.sentence = sentence;
    }

    /** The name a refusal shows as its {@code reason}. */
    public String get() {
        return name;
    }

    /** What the reason says, to follow a colon. */
    public String sentence() {
        return sentence;
    }
}
