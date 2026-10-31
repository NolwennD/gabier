package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.application.Rename;
import fr.craft.gabier.editing.application.RenameTarget;
import java.util.Map;
import java.util.Set;

/**
 * {@code rename-token}: renames the symbol that the first occurrence of an identifier in a file names, given as the
 * parameters {@code file} (absolute), {@code token}, {@code newName} and {@code dryRun}. It answers as {@code rename}
 * does; an identifier that does not occur in the file as a whole word is refused as the problem object it is.
 */
record RenameTokenMethod(Rename rename) implements DaemonMethod {

    private static final String TOKEN = "token";

    @Override
    public String name() {
        return "rename-token";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(ChangeRequest.FILE, TOKEN, ChangeRequest.NEW_NAME, ChangeRequest.DRY_RUN);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        var request = new ChangeRequest(name(), params);
        var target = new RenameTarget.Token(request.file(), request.token(TOKEN));

        return request.run(rename, target);
    }
}
