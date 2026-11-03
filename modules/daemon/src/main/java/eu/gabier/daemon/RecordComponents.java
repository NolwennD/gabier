package eu.gabier.daemon;

import java.util.List;
import org.eclipse.lsp4j.Position;

/**
 * Which declarations of a file are components of a record. jdtls does not rename a component correctly (see {@code
 * RecordRenameScenarios}), and nothing but the semantic tokens tells a component from a field: {@code
 * textDocument/documentSymbol} lists both as a field, with the same range. The legend of jdtls has the token type
 * {@code recordComponent}, given to the name of a component in the header of its record.
 */
final class RecordComponents {

    /** The name of the token type, in the legend jdtls announces. */
    static final String TOKEN_TYPE = "recordComponent";

    /** The numbers of a token: line, column, length, type and modifiers. */
    private static final int NUMBERS_OF_A_TOKEN = 5;

    private RecordComponents() {}

    /**
     * Whether a token of the type of the record components starts at the position.
     *
     * @param tokens the {@code data} of a {@code SemanticTokens} answer: five numbers by token, the line relative to the
     *     previous token and the column relative to the previous token when it is on the same line, else to the start
     *     of the line
     * @param recordComponent the number of the token type {@code recordComponent} in the legend
     * @param position where the declaration starts, 0-based
     */
    static boolean areDeclaredAt(List<Integer> tokens, int recordComponent, Position position) {
        int line = 0;
        int column = 0;
        for (int first = 0; first + NUMBERS_OF_A_TOKEN <= tokens.size(); first += NUMBERS_OF_A_TOKEN) {
            int movedLines = tokens.get(first);
            line += movedLines;
            column = (movedLines == 0 ? column : 0) + tokens.get(first + 1);
            if (line == position.getLine()
                    && column == position.getCharacter()
                    && tokens.get(first + 3) == recordComponent) {
                return true;
            }
        }

        return false;
    }
}
