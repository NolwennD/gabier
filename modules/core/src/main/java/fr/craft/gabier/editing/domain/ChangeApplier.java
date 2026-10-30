package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.Assert;

/**
 * Applies a {@link ChangeSet} to the files of a {@link FileStore}, all or nothing.
 *
 * <p>The set is first run in memory, on a picture of the files: if any change is refused, nothing has been written. The
 * writes then run in the order of the set. If one fails, the ones already done are undone, in reverse order, so that
 * the files are back as they were. A file that someone else changes between the check and the writes is not detected.
 *
 * <p>A file is known by its path made absolute and normalized (no {@code .} nor {@code ..} segment), so two spellings of
 * one path are one file, and the reports give it in that form. The cleaning is on the text of the path: a link in it is
 * not followed. A set creates files and never folders: a created or renamed-to file whose folder does not exist is
 * refused (its folder missing, or not a folder) when the set is planned, by the dry run as by the application.
 *
 * <p>Applying and previewing a set is all it does, so it lives in the domain; the use cases of the application layer
 * ({@code Rename}) ask a language server for a set and hand it to this class.
 */
public final class ChangeApplier {

    private final FileStore store;

    public ChangeApplier(FileStore store) {
        Assert.notNull("store", store);

        this.store = store;
    }

    /**
     * What applying the set would do, without writing anything.
     *
     * @throws EditingException if the set is refused
     */
    public ChangeReport dryRun(ChangeSet changes) {
        return plan(changes).report();
    }

    /**
     * Writes the set.
     *
     * @return what was done
     * @throws EditingException if the set is refused or a write fails; in both cases the files are as they were
     */
    public ChangeReport apply(ChangeSet changes) {
        ChangePlan plan = plan(changes);
        plan.execute(store);

        return plan.report();
    }

    private ChangePlan plan(ChangeSet changes) {
        Assert.notNull("changes", changes);

        return new PlanBuilder(store).plan(changes);
    }
}
