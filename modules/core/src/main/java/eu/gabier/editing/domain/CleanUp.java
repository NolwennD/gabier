package eu.gabier.editing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.util.Arrays;
import java.util.List;

/**
 * The clean-ups of the language server that gabier applies to a file, each by the identifier the server knows it by.
 * The organization of the imports, which the server also offers as a clean-up, is not one of them: it is {@code
 * organize-imports}, which refuses the imports the server cannot judge.
 */
public enum CleanUp {
    ADD_DEPRECATED("addDeprecated"),
    ADD_FINAL_MODIFIER("addFinalModifier"),
    ADD_OVERRIDE("addOverride"),
    INSTANCEOF_PATTERN_MATCH("instanceofPatternMatch"),
    INVERT_EQUALS("invertEquals"),
    LAMBDA_EXPRESSION("lambdaExpression"),
    LAMBDA_EXPRESSION_FROM_ANONYMOUS_CLASS("lambdaExpressionFromAnonymousClass"),
    QUALIFY_MEMBERS("qualifyMembers"),
    QUALIFY_STATIC_MEMBERS("qualifyStaticMembers"),
    REDUNDANT_COMPARISON_STATEMENT("redundantComparisonStatement"),
    REDUNDANT_FALLING_THROUGH_BLOCK_END("redundantFallingThroughBlockEnd"),
    REDUNDANT_IF_CONDITION("redundantIfCondition"),
    REDUNDANT_MODIFIERS("redundantModifiers"),
    REDUNDANT_SUPER_CALL("redundantSuperCall"),
    RENAME_UNUSED_LOCAL_VARIABLES("renameUnusedLocalVariables"),
    STRING_CONCAT_TO_TEXT_BLOCK("stringConcatToTextBlock"),
    SWITCH_EXPRESSION("switchExpression"),
    TRY_WITH_RESOURCE("tryWithResource"),
    USE_SWITCH_FOR_INSTANCEOF_PATTERN("useSwitchForInstanceofPattern");

    private final String id;

    CleanUp(String id) {
        this.id = id;
    }

    /** The identifier the language server knows the clean-up by, which is also how a person names it. */
    public String id() {
        return id;
    }

    /** The identifiers of every clean-up, in the order of the help. */
    public static List<String> ids() {
        return Arrays.stream(values()).map(CleanUp::id).toList();
    }

    /**
     * The clean-up this identifier names.
     *
     * @throws EditingException if the server has no clean-up of that identifier
     */
    public static CleanUp of(String id) {
        Assert.notNull("id", id);

        return Arrays.stream(values())
                .filter(cleanUp -> cleanUp.id.equals(id))
                .findFirst()
                .orElseThrow(() -> EditingException.unknownCleanUp(id, ids()));
    }

    /**
     * The clean-ups these identifiers name, in the order given.
     *
     * @throws EditingException if the server has no clean-up of one of the identifiers (the first of them is named)
     */
    public static List<CleanUp> ofAll(List<String> ids) {
        Assert.field("ids", ids).notNull().notEmpty().noNullElement();

        return ids.stream().map(CleanUp::of).toList();
    }
}
