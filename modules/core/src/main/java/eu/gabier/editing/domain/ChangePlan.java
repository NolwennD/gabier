package eu.gabier.editing.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A set of changes that has been checked against the files and found applicable: the operations to run on the {@link
 * FileStore}, in order, and the report of what they do. Nothing has been written when a plan exists.
 */
final class ChangePlan {

    private final List<Step> steps;
    private final ChangeReport report;

    ChangePlan(List<Step> steps, ChangeReport report) {
        this.steps = List.copyOf(steps);
        this.report = report;
    }

    ChangeReport report() {
        return report;
    }

    /** The same plan, then the removal of each folder that its steps leave empty, in the order given; same report. */
    ChangePlan removingWhenEmptied(List<Path> folders) {
        List<Step> all = new ArrayList<>(steps);
        folders.forEach(folder -> all.add(new Step.RemoveFolderIfEmpty(folder)));

        return new ChangePlan(all, report);
    }

    /**
     * Runs the steps in order. When one fails, every step tried so far, the failed one included (it may have written
     * half of its file), is undone from the last to the first.
     *
     * @throws EditingException if a step fails: with the key {@code WRITE_FAILED} once the files are back as they were,
     *     {@code ROLLBACK_INCOMPLETE} if some could not be put back
     */
    void execute(FileStore store) {
        Deque<Step> tried = new ArrayDeque<>();
        for (Step step : steps) {
            tried.push(step);
            try {
                step.apply(store);
            } catch (IOException | RuntimeException failure) {
                throw rollBack(store, tried, failure);
            }
        }
    }

    private static EditingException rollBack(FileStore store, Deque<Step> tried, Exception failure) {
        Path failed = tried.peek().target();
        List<Path> notRestored = new ArrayList<>();
        List<Exception> undoFailures = new ArrayList<>();
        for (Step step : tried) {
            try {
                step.undo(store);
            } catch (IOException | RuntimeException undoFailure) {
                notRestored.add(step.target());
                undoFailures.add(undoFailure);
            }
        }
        EditingException exception = notRestored.isEmpty()
                ? EditingException.writeFailed(failed, failure)
                : EditingException.rollbackIncomplete(failed, notRestored, failure);
        undoFailures.forEach(exception::addSuppressed);

        return exception;
    }
}
