package fr.craft.gabier.cli;

/** How far two words are: the letters to change, add or remove, two letters side by side swapped counting for one. */
final class EditDistance {

    private EditDistance() {}

    /** The optimal string alignment distance of {@code from} and {@code to}. */
    static int between(String from, String to) {
        var distances = new int[from.length() + 1][to.length() + 1];
        for (var i = 0; i <= from.length(); i++) {
            distances[i][0] = i;
        }
        for (var j = 0; j <= to.length(); j++) {
            distances[0][j] = j;
        }
        for (var i = 1; i <= from.length(); i++) {
            for (var j = 1; j <= to.length(); j++) {
                var cost = from.charAt(i - 1) == to.charAt(j - 1) ? 0 : 1;
                var distance = Math.min(
                        Math.min(distances[i - 1][j] + 1, distances[i][j - 1] + 1), distances[i - 1][j - 1] + cost);
                if (isSwap(from, to, i, j)) {
                    distance = Math.min(distance, distances[i - 2][j - 2] + 1);
                }
                distances[i][j] = distance;
            }
        }

        return distances[from.length()][to.length()];
    }

    private static boolean isSwap(String from, String to, int i, int j) {
        return i > 1 && j > 1 && from.charAt(i - 1) == to.charAt(j - 2) && from.charAt(i - 2) == to.charAt(j - 1);
    }
}
