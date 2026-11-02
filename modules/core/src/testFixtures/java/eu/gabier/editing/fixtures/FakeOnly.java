package eu.gabier.editing.fixtures;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test that shows a case of the fake that no replay can show: a fault that the real jdtls cannot produce on
 * demand (a null answer, an {@code InvalidRequest}, an edit with no message, a server that dies, a timeout). Such a
 * test checks how the adapter handles a failure, never what jdtls answers when it is right, which is the business of the
 * scenarios and of their replay (see {@link Scenario}). The reason, which says why no replay can show it, is mandatory
 * (it does not compile without), and a blank one fails the build ({@code FakeOnlyReasonTest}); a test of the adapter that
 * makes its fake of jdtls play such a failure fails without the label ({@code FakeOnlyGuard}).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface FakeOnly {

    /** Why no replay on the real server can show this case. */
    String reason();
}
