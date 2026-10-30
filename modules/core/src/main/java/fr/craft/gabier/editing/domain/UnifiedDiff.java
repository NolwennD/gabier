package fr.craft.gabier.editing.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * The difference between two texts in the unified format of {@code diff -u}, three lines of context, without the
 * timestamps of the headers. Lines are compared without their terminator (CRLF, LF and CR are the same), but the lack
 * of a terminator at the end of the text is a difference, shown as {@code \ No newline at end of file}.
 *
 * <p>The lines are compared with the algorithm of Myers, which is minimal and fast when the texts are close. Two texts
 * more than {@value #MAX_CHANGES} lines apart are shown as one block removed and one block added, which is a correct
 * diff but not a minimal one: it keeps the time and the memory of a dry run bounded.
 */
final class UnifiedDiff {

    private static final int CONTEXT = 3;
    private static final int MAX_CHANGES = 2000;
    private static final char SAME = ' ';
    private static final char REMOVED = '-';
    private static final char ADDED = '+';

    private UnifiedDiff() {}

    /**
     * Writes the unified diff that turns the old text into the new one.
     *
     * @param fromLabel the name of the old text in the header, {@code /dev/null} for a file that did not exist
     * @param toLabel the name of the new text in the header, {@code /dev/null} for a file that is gone
     */
    static String of(String fromLabel, String fromText, String toLabel, String toText) {
        List<Op> ops = compare(lines(fromText), lines(toText));
        StringBuilder diff = new StringBuilder("--- ")
                .append(fromLabel)
                .append("\n+++ ")
                .append(toLabel)
                .append('\n');
        // The line of each side that an operation stands at, counted before it (from 0).
        int[] fromBefore = new int[ops.size() + 1];
        int[] toBefore = new int[ops.size() + 1];
        for (int i = 0; i < ops.size(); i++) {
            fromBefore[i + 1] = fromBefore[i] + (ops.get(i).marker() == ADDED ? 0 : 1);
            toBefore[i + 1] = toBefore[i] + (ops.get(i).marker() == REMOVED ? 0 : 1);
        }
        for (int[] hunk : hunks(ops)) {
            int from = hunk[0];
            int to = hunk[1];
            diff.append("@@ -")
                    .append(range(fromBefore[from], fromBefore[to + 1] - fromBefore[from]))
                    .append(" +")
                    .append(range(toBefore[from], toBefore[to + 1] - toBefore[from]))
                    .append(" @@\n");
            ops.subList(from, to + 1).forEach(op -> append(diff, op));
        }

        return diff.toString();
    }

    private static void append(StringBuilder diff, Op op) {
        diff.append(op.marker()).append(op.line().content()).append('\n');
        if (!op.line().terminated()) {
            diff.append("\\ No newline at end of file\n");
        }
    }

    /** {@code start,count}, the start being the line before the range when it is empty and one-based otherwise. */
    private static String range(int before, int count) {
        return switch (count) {
            case 0 -> before + ",0";
            case 1 -> String.valueOf(before + 1);
            default -> (before + 1) + "," + count;
        };
    }

    /** The first and last operation of each hunk: the changes with their context, merged when the contexts touch. */
    private static List<int[]> hunks(List<Op> ops) {
        List<int[]> hunks = new ArrayList<>();
        for (int i = 0; i < ops.size(); i++) {
            if (ops.get(i).marker() == SAME) {
                continue;
            }
            int first = Math.max(0, i - CONTEXT);
            int last = Math.min(ops.size() - 1, i + CONTEXT);
            if (!hunks.isEmpty() && first <= hunks.getLast()[1] + 1) {
                hunks.getLast()[1] = last;
            } else {
                hunks.add(new int[] {first, last});
            }
        }

        return hunks;
    }

    private record Line(String content, boolean terminated) {}

    private record Op(char marker, Line line) {}

    private static List<Line> lines(String text) {
        List<Line> lines = new ArrayList<>();
        int start = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                lines.add(new Line(text.substring(start, i), true));
                i += c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n' ? 2 : 1;
                start = i;
            } else {
                i++;
            }
        }
        if (start < text.length()) {
            lines.add(new Line(text.substring(start), false));
        }

        return lines;
    }

    private static List<Op> compare(List<Line> from, List<Line> to) {
        int fromSize = from.size();
        int toSize = to.size();
        int maxChanges = Math.min(fromSize + toSize, MAX_CHANGES);
        // v[base + k] is the furthest x reached on the diagonal k = x - y.
        int base = maxChanges + 1;
        int[] v = new int[2 * maxChanges + 3];
        List<int[]> trace = new ArrayList<>();
        for (int changes = 0; changes <= maxChanges; changes++) {
            trace.add(Arrays.copyOfRange(v, base - changes - 1, base + changes + 2));
            for (int k = -changes; k <= changes; k += 2) {
                boolean fromInsertion = k == -changes || (k != changes && v[base + k - 1] < v[base + k + 1]);
                int x = fromInsertion ? v[base + k + 1] : v[base + k - 1] + 1;
                int y = x - k;
                while (x < fromSize && y < toSize && from.get(x).equals(to.get(y))) {
                    x++;
                    y++;
                }
                v[base + k] = x;
                if (x >= fromSize && y >= toSize) {
                    return path(from, to, trace, changes);
                }
            }
        }

        List<Op> ops = new ArrayList<>();
        from.forEach(line -> ops.add(new Op(REMOVED, line)));
        to.forEach(line -> ops.add(new Op(ADDED, line)));

        return ops;
    }

    /** Walks the trace of {@link #compare} backwards, from the end of both texts to their start. */
    private static List<Op> path(List<Line> from, List<Line> to, List<int[]> trace, int changes) {
        Deque<Op> ops = new ArrayDeque<>();
        int x = from.size();
        int y = to.size();
        for (int round = changes; round > 0; round--) {
            // The furthest points of the round before, for the diagonals -round-1 to round+1.
            int[] before = trace.get(round);
            int k = x - y;
            boolean fromInsertion =
                    k == -round || (k != round && before[k - 1 + round + 1] < before[k + 1 + round + 1]);
            int previousK = fromInsertion ? k + 1 : k - 1;
            int previousX = before[previousK + round + 1];
            int previousY = previousX - previousK;
            int afterChangeX = fromInsertion ? previousX : previousX + 1;
            while (x > afterChangeX) {
                x--;
                ops.addFirst(new Op(SAME, from.get(x)));
            }
            ops.addFirst(fromInsertion ? new Op(ADDED, to.get(previousY)) : new Op(REMOVED, from.get(previousX)));
            x = previousX;
            y = previousY;
        }
        while (x > 0) {
            x--;
            ops.addFirst(new Op(SAME, from.get(x)));
        }

        return new ArrayList<>(ops);
    }
}
