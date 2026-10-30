package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.Assert;

/**
 * Applies a {@link ChangeSet} to the files of a {@link FileStore}, all or nothing.
 *
 * <p>The set is first run in memory, on a picture of the files: if any change is refused, nothing has been written. The
 * writes then run in the order of the set. If one fails, the ones already done are undone, in reverse order, so that
 * the files are back as they were. A file that someone else changes between the check and the writes is not detected.
 *
 * <p>There is no application layer in this context: applying and previewing a set is all there is to do, and the
 * domain does it.
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
