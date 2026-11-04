# Improving Java with gabier: the problem each move solves

`gabier help guide` prints this page from the installed cli, with no clone of the repository.

You have a piece of Java and a problem with it: a string built with `+`, a cast after an `instanceof`, a loop on an
index, a condition to turn round. Find the situation below; each entry says what jdtls (the Java language server behind
gabier) can do about it, how to ask, what it looks like, and what it cannot do. What jdtls does not offer is said too,
as **by hand**, so that you do not look for it.

Every entry has the same lines, one per question:

- `Words:` what a person says about the problem, on one line.
- `Ask:` the command or the action that solves it, or `By hand:` when gabier has none.
- `Before:` and `After:` the code, when there is code to show.
- `Limits:` when it does not work, and what to do then.

Three ways to ask, from the widest to the narrowest:

- `gabier cleanup --dry-run <file> <cleanup>...` rewrites **every** place of a whole file that a clean-up of jdtls can
  rewrite. `--dry-run` shows the diff and writes nothing; leave it out to write. Clean-ups are applied in the order
  given. The identifiers are in `gabier help cleanup`.
- `gabier actions <file> <line>:<column> <line>:<column>` lists what jdtls offers on a range (a point is the same
  position twice), and `gabier apply-action [--dry-run] <file> <line>:<column> <line>:<column> "<title>"` applies
  **one** of them, at that place only. The title is the key, the number of the list changes from one call to the next.
- a command of gabier (`extract-variable`, `inline`, `rename`...) when `gabier actions` ends a line with `-> gabier
  <command>`: it takes the name you want, where `apply-action` takes the name jdtls guesses.

Run `gabier diagnostics <file>` after a change that crosses a file boundary, or when the language level of the project
may not allow what was written (a text block needs Java 15, a pattern in `instanceof` 16, a pattern in `switch` 21).

## Situations met while working on gabier

### A concatenation that is not only literals

Words: string concatenation, plus signs, build a json string, string with calls and quotes, text block does not apply

Ask: `gabier actions <file> <line>:<column> <line>:<column>` on a point of the concatenation, then `gabier apply-action
<file> <line>:<column> <line>:<column> "Use 'String.format' for string concatenation"`. jdtls also offers these
titles: `Use 'StringBuilder' for string concatenation` and `Use 'MessageFormat' for string concatenation`. It offers no
text block for this one.

Before:

```java
return "{\"name\":" + Json.quote(name) + ",\"summary\":" + Json.quote(summary) + "}";
```

After `String.format`:

```java
return String.format("{\"name\":%s,\"summary\":%s}", Json.quote(name), Json.quote(summary));
```

Limits: `stringConcatToTextBlock` rewrites a concatenation of three or more string literals and nothing else, so
`gabier cleanup --dry-run <file> stringConcatToTextBlock` answers `nothing to change` here. `String.format` puts the
whole expression on one line: the formatter, or a hand-written text block filled in by `formatted(...)`, makes it
readable again. The actions appear when the position is on the concatenation; on a range that holds all of it, jdtls
offers an extraction and a `try/catch` instead.

### Change one place, not the whole file

Words: only this one, not everywhere, one place, one method, a clean-up changed too much, apply to a range

Ask: `gabier actions <file> <line>:<column> <line>:<column>` on the place, then `gabier apply-action --dry-run <file>
<line>:<column> <line>:<column> "<title>"` with the title of the action. A clean-up and an action often do the same
rewrite; the clean-up does it everywhere, the action where you point.

Before (two concatenations of literals, the second must stay as it is):

```java
String a = "one\n" + "two\n" + "three\n";
String b = "four\n" + "five\n" + "six\n";
```

After `gabier apply-action <file> <line>:<column> <line>:<column> "Convert String concatenation to Text Block"`, with
the position on the first concatenation:

```java
String a = """
    one
    two
    three
    """;
String b = "four\n" + "five\n" + "six\n";
```

Limits: the title is the text of jdtls, so read it in `gabier actions`; a title that the range does not offer is refused
(`editing-no-such-code-action`). Not every clean-up has an action for one place: when `gabier actions` lists none, the
clean-up is the only way, and it changes the whole file, so read the `--dry-run` diff first.

### Rename a component of a record

Words: record component, rename a record field, record accessor, rename a parameter of a record

Ask: `gabier rename --dry-run <file> <line>:<column> <new-name>` with the position on the component in the header of the
record, then `gabier diagnostics <file>`.

Before:

```java
record Mark(String label, int rank) {
    public String label() {
        return label.trim();
    }

    String show() {
        return label + rank;
    }
}
```

After `gabier rename Mark.java <line>:<column> caption`, with the position on `label` in the header:

```java
record Mark(String caption, int rank) {
    public String getCaption() {
        return caption.trim();
    }

    String show() {
        return caption + rank;
    }
}
```

Limits: jdtls 1.63.0 fails on a record that has a compact constructor (`Mark { ... }`) or an explicit canonical
constructor (`Mark(String label, int rank) { this.label = label; ... }`): the answer is `internal-server-error`, with
nothing renamed, and the fix is upstream (Eclipse bug 566507 describes the rename of a component). Without a
constructor of that kind it renamed the header, the uses in the body and the calls of the accessor. Look at the
accessor in the example: an explicit accessor `label()` became `getCaption()`, which is no longer the accessor of the
component, so rename it back to `caption()` by hand. Other cases are reported to leave a use by simple name unrenamed
and the file no longer compiling (we did not reproduce it here): run `gabier diagnostics` on every file of the record.
A position on a call of the accessor (`mark.label()`) is declined. For a record with a constructor that fails, edit the
header, the constructor and the uses by hand, then `gabier diagnostics`.

### The package of one source set

Words: rename a package, move package, tests keep the old package, two source folders, src/test/java, package line

Ask: `gabier rename <file> <line>:<column> <new-name>` with the position on the name in the `package` line of a file of
the package, the whole new name. Then the same for a file of the package in the other source set.

Before (the package `demo.app` has classes in `src/main/java` and in `src/test/java`):

```text
src/main/java/demo/app/Greeter.java      package demo.app;
src/test/java/demo/app/GreeterTest.java  package demo.app;
```

After `gabier rename src/main/java/demo/app/Greeter.java 1:14 demo.shop`, only the main source set moved:

```text
src/main/java/demo/shop/Greeter.java     package demo.shop;
src/test/java/demo/app/GreeterTest.java  package demo.app;   (now imports demo.shop.Greeter)
```

Limits: jdtls renames the package in the source folder of the file only. The same package in the tests is a second call,
on a file of that source set. Sub-packages keep their names. `module-info.java`, string literals and build files are
text edits that gabier does not do. See `gabier help rename`.

## The language: clean-ups of jdtls

These are the 19 identifiers of `gabier help cleanup`. Each rewrites every place of the file it applies to. A clean-up
that finds nothing answers `nothing to change`. Where the entry says what Java it needs, the project must be at that
level or later; `gabier diagnostics` shows the errors if it is not.

### A string built from literals with `+` and line breaks

Words: text block, multi-line string, string concatenation of literals, escaped quotes, backslash n

Ask: `gabier cleanup --dry-run <file> stringConcatToTextBlock`

Before:

```java
return "Hello, \"world\"!\n" + "Welcome to demo.\n" + "Bye.\n";
```

After:

```java
return """
    Hello, "world"!
    Welcome to demo.
    Bye.
    """;
```

Limits: only a concatenation of three or more string literals; a concatenation of two is left alone, and so is one that
holds a call or a variable (`"{" + quote(x) + "}"`): see the first entry for those. Java 15 or later. Every place of the
file is rewritten; for one place, see "Change one place, not the whole file".

### A cast right after an `instanceof`

Words: instanceof then cast, pattern matching, instanceof pattern, remove the cast, type test and cast

Ask: `gabier cleanup --dry-run <file> instanceofPatternMatch`

Before:

```java
if (o instanceof String) {
    String s = (String) o;
    return s.trim();
}
```

After:

```java
if (o instanceof String s) {
    return s.trim();
}
```

Limits: Java 16 or later. It acts when the tested variable is cast to the tested type right after the test.

### A chain of `instanceof` tests

Words: if else if instanceof, type switch, pattern switch, many instanceof, dispatch on the type of an object

Ask: `gabier cleanup --dry-run <file> useSwitchForInstanceofPattern`

Before:

```java
if (o instanceof String s) {
    return "s" + s;
} else if (o instanceof Integer i) {
    return "i" + i;
} else {
    return "?";
}
```

After:

```java
return switch (o) {
    case String s -> "s" + s;
    case Integer i -> "i" + i;
    case null, default -> "?";
};
```

Limits: Java 21 or later. The tests must already bind a variable (`o instanceof String s`): on a chain of plain
`instanceof` tests give `instanceofPatternMatch` first, then this one, in that order, in one call. The result has a
`case null`, which the chain did not have: a `null` that used to fall in the last `else` still does.

### A `switch` statement that only assigns or returns

Words: switch expression, break at the end of each case, arrow case, switch that sets a variable, old switch

Ask: `gabier cleanup --dry-run <file> switchExpression`, or for one `switch`: `gabier apply-action <file>
<line>:<column> <line>:<column> "Convert to switch expression"` with the position on the `switch` keyword.

Before:

```java
int r;
switch (d) {
    case 1:
        r = 10;
        break;
    case 2:
        r = 20;
        break;
    default:
        r = 0;
}
```

After:

```java
int r = switch (d) {
    case 1 -> 10;
    case 2 -> 20;
    default -> 0;
};
```

Limits: Java 14 or later. The cleanup rewrote the assignment shape above when this guide was written, and the action
also rewrote a `switch` whose cases all `return`; other shapes may be left alone, so read the `--dry-run` diff.

### An anonymous class with one method

Words: anonymous class to lambda, new Runnable, new Comparator, inner class with one method, functional interface

Ask: `gabier cleanup --dry-run <file> lambdaExpressionFromAnonymousClass`, or for one class: the action `Convert to
lambda expression`, with the position on the class name after `new`.

Before:

```java
Runnable r = new Runnable() {
    public void run() {
        System.out.println("x");
    }
};
```

After:

```java
Runnable r = () -> System.out.println("x");
```

Limits: only an interface with a single abstract method, and a body that does not need the anonymous class itself (a
`this`, a field or a second method of it). The opposite is the action `Convert to anonymous class creation`, with the
position on the arrow of a lambda.

### A lambda that only calls a method

Words: method reference, lambda to method ref, s -> s.length(), simplify lambda, return in a lambda block

Ask: `gabier cleanup --dry-run <file> lambdaExpression`

Before:

```java
Function<String, Integer> f = s -> {
    return s.length();
};
Function<String, Integer> g = s -> Integer.valueOf(s);
```

After:

```java
Function<String, Integer> f = String::length;
Function<String, Integer> g = Integer::valueOf;
```

Limits: a lambda that does more than call one method is left alone. Java 8 or later.

### A method that overrides and has no `@Override`

Words: missing override annotation, add @Override, implements without annotation

Ask: `gabier cleanup --dry-run <file> addOverride`

Before:

```java
public void run() {
```

After:

```java
@Override
public void run() {
```

Limits: it acts on methods that override or implement a method of a type jdtls can resolve.

### A javadoc `@deprecated` with no annotation

Words: deprecated tag, add @Deprecated, javadoc says deprecated

Ask: `gabier cleanup --dry-run <file> addDeprecated`

Before:

```java
/** @deprecated use other */
int old() {
```

After:

```java
/** @deprecated use other */
@Deprecated
int old() {
```

Limits: it acts on a declaration whose javadoc has the tag and which has no annotation.

### A resource closed by hand in a `finally`

Words: try with resources, close in finally, reader close, BufferedReader, stream not closed, auto closeable

Ask: `gabier cleanup --dry-run <file> tryWithResource`

Before:

```java
BufferedReader reader = new BufferedReader(new FileReader(path));
try {
    return reader.readLine();
} finally {
    reader.close();
}
```

After:

```java
BufferedReader reader = new BufferedReader(new FileReader(path));
try (reader) {
    return reader.readLine();
}
```

Limits: Java 9 or later for the `try (reader)` form, which needs a variable that is not assigned again. Read the diff of
a `finally` that does more than close the resource.

### A constant compared with `equals` on the wrong side

Words: yoda equals, null pointer on equals, "x".equals(s), s.equals("x"), string comparison with null

Ask: `gabier cleanup --dry-run <file> invertEquals`

Before:

```java
return s.equals("x");
```

After:

```java
return "x".equals(s);
```

Limits: it acts on `equals` called on a variable with a literal argument. It changes what happens when `s` is `null`
(no exception any more): that is the point, and it can hide a bug that used to show.

### Parameters and locals that are never assigned again

Words: final parameters, add final, final locals, immutability, make variables final

Ask: `gabier cleanup --dry-run <file> addFinalModifier`

Before:

```java
boolean same(String s) {
    int count = 1;
```

After:

```java
boolean same(final String s) {
    final int count = 1;
```

Limits: every parameter and every local variable of the file that is never assigned again gets `final`: the diff is
wide, read it before writing.

### Fields used without `this.`

Words: qualify field access, add this, this dot field, unqualified field

Ask: `gabier cleanup --dry-run <file> qualifyMembers`

Before:

```java
return count + name.length();
```

After:

```java
return this.count + this.name.length();
```

Limits: instance fields and methods used by their simple name inside the class; static members are
`qualifyStaticMembers`.

### Static members used without the class name

Words: qualify static call, add class name, static import look, call rate() instead of D.rate()

Ask: `gabier cleanup --dry-run <file> qualifyStaticMembers`

Before:

```java
return D.rate() + rate();
```

After:

```java
return D.rate() + D.rate();
```

Limits: it acts on a static member used by its simple name.

### An unused lambda parameter

Words: unused parameter, underscore, unnamed variable, lambda argument never read

Ask: `gabier cleanup --dry-run <file> renameUnusedLocalVariables`

Before:

```java
l.forEach(s -> System.out.println("x"));
```

After:

```java
l.forEach(_ -> System.out.println("x"));
```

Limits: Java 22 or later (`_` is an unnamed variable only from there). It rewrites what the compiler reports as unused:
on a file with an unused local variable (`int x = 1;`) next to the lambda, only the lambda parameter was renamed when
this guide was written. To remove an unused local, use the fix that `gabier actions` lists on the warning.

### Modifiers that the language already gives

Words: redundant modifier, public in an interface, abstract in an interface, modifier the language gives

Ask: `gabier cleanup --dry-run <file> redundantModifiers`

Before:

```java
interface I {
    public abstract void m();
}
```

After:

```java
interface I {
    void m();
}
```

Limits: only modifiers that the language already implies, such as `public abstract` on the method of an interface.

### A `super()` call with no argument

Words: redundant super call, explicit super in a constructor, call the default parent constructor

Ask: `gabier cleanup --dry-run <file> redundantSuperCall`

Before:

```java
C() {
    super();
}
```

After:

```java
C() {
}
```

Limits: only a `super()` with no argument as the first statement of a constructor.

### An `else if` that repeats the opposite of the `if`

Words: redundant else if, else if not x, condition that is always true in the else, dead condition

Ask: `gabier cleanup --dry-run <file> redundantIfCondition`

Before:

```java
if (x) {
    one();
} else if (!x) {
    two();
}
```

After:

```java
if (x) {
    one();
} else {
    two();
}
```

Limits: the condition must be the exact opposite of the one tested before, with no side effect. The `else { if (!x) {`
form becomes `else { {`: tidy the empty block by hand.

### An `if` / `else` whose `else` repeats the tested constant

Words: redundant comparison, if not equal then else same value, return the variable or the constant, useless if else

Ask: `gabier cleanup --dry-run <file> redundantComparisonStatement`

Before:

```java
if (i != 123) {
    return i;
} else {
    return 123;
}
```

After:

```java
return i;
```

The same goes for two assignments of one variable, `if (i != 123) { r = i; } else { r = 123; }`, which becomes
`r = i;`: when the test fails, `i` is `123`, so the `else` branch gives what `i` gives anyway.

Limits: both branches are needed (a lone `if (i != 0) { i = 0; }` is left alone), and the `else` must give exactly the
constant that the test excludes. It was observed with `!=` and an integer constant; `gabier cleanup --dry-run` shows
what it does with another shape.

### The end of a block that repeats the code after it

Words: duplicated return, same return twice, redundant return at the end of an if, code repeated after the block

Ask: `gabier cleanup --dry-run <file> redundantFallingThroughBlockEnd`

Before:

```java
if (0 < i) {
    f();
    return i + 10;
}
return i + 10;
```

After:

```java
if (0 < i) {
    f();
}
return i + 10;
```

Limits: the last statement of the block must be the same as the code that follows the block, which the block would
reach anyway. A `break`, a `continue` or a bare `return` at the end of a block, on its own, is not what it removes:
those shapes answered `nothing to change`.

## The language: one place, with `gabier actions`

These rewrites of jdtls have no clean-up, or are better used on one place. Ask `gabier actions <file> <line>:<column>
<line>:<column>` with the position on the construct (the keyword `for`, `switch`, `new`, the name of a variable), then
apply the title with `gabier apply-action`.

### An index loop that only reads the elements

Words: for each, enhanced for, for loop with i, loop over a list, loop over an array, remove the index

Ask: the action `Convert to enhanced 'for' loop` on the `for` keyword.

Before:

```java
for (int i = 0; i < names.size(); i++) {
    System.out.println(names.get(i));
}
```

After:

```java
for (String name : names) {
    System.out.println(name);
}
```

Limits: the index must only be used to read the element; a loop that uses `i` for something else is not offered.

### A type the right side already says

Words: var, local variable type inference, repeated type, String text = "abc"

Ask: the action `Change type of '<name>' to 'var'` with the position on the name of the variable.

Before:

```java
String text = "abc";
```

After:

```java
var text = "abc";
```

Limits: Java 10 or later.

### A lambda whose body is a block, or an expression

Words: lambda body to block, lambda body to expression, add braces to a lambda

Ask: the action `Change body expression to block`, with the position on the arrow of the lambda.

Before:

```java
Runnable q = () -> System.out.println("q");
```

After `Change body expression to block`:

```java
Runnable q = () -> {
    System.out.println("q");
};
```

Limits: one place at a time. The way back, from a block body to an expression, is the clean-up `lambdaExpression`,
which also turns the lambda into a method reference when it can.

### A class that only holds values

Words: record, data class, immutable class with fields and accessors, convert class to record

Ask: not seen. jdtls 1.63.0 has a proposal for it in its source, but `gabier actions` listed nothing on the name of a
class with `private final` fields, a constructor that assigns them and one accessor each.

Before / After: not shown.

Limits: by hand: write the `record`, delete the class body that the record gives (fields, constructor, accessors,
`equals`, `hashCode`, `toString`), then `gabier diagnostics` on every file that used the class.

## The structure of the code

jdtls 1.63.0 wires these structure moves, as refactorings that `gabier actions` lists: extract (variable, field, method,
constant), inline, introduce parameter, make static, change signature, surround with `try/catch`, convert an anonymous
class and a lambda to each other, invert conditions, invert a local variable, `for` to enhanced `for`, and `var` to the
type and back. The moves of the next entries that say "by hand" are not offered by jdtls (see the last entry for why).

### An expression used twice, or too long to read

Words: extract variable, name an expression, repeated expression, long condition, magic value

Ask: `gabier extract-variable [--dry-run] <file> <line>:<column> <line>:<column> <name>`; for a field,
`extract-field`; for a value that depends on no local, `extract-constant`.

Before:

```java
if (names.size() > limit && names.size() < max) {
```

After `extract-variable` with the name `count` on the first `names.size()`:

```java
int count = names.size();
if (count > limit && names.size() < max) {
```

Limits: the selection must be an expression; `gabier help extract-variable` lists the refusals. The action `Extract to
local variable (replace all occurrences)` of `gabier actions` links to this command too.

### Statements that deserve a name

Words: extract method, long method, split a method, pull code out, move block to a method

Ask: `gabier extract-method [--dry-run] <file> <line>:<column> <line>:<column> <name>`

Limits: the selection must be whole statements or an expression; `gabier help extract-method` lists the refusals.

### A call that hides what it does

Words: inline method, inline variable, remove a wrapper, replace by its body, one use only

Ask: `gabier inline [--dry-run] <file> <line>:<column>`

Limits: on the declaration of a method or a constant every use is inlined; on one use, that use only. See `gabier help
inline`. `gabier actions` also lists `Make Static` with the command `inline`: that is the kind jdtls gives it, and `Make
Static` is not an inline; apply it with `gabier apply-action`.

### A condition to turn round

Words: invert condition, negate, not, de morgan, a and b to not a or not b, flip a boolean

Ask: the action `Invert conditions` with the position on the condition (the `if (...)` parentheses or the operands).

Before:

```java
if (a && b) {
    return true;
}
```

After:

```java
if (!a || !b) {
    return true;
}
```

Limits: it negates the expression and does nothing else. The two branches of the `if` are not swapped, so the meaning
of the program changes: turn the branches round by hand, or negate an expression you meant to negate (a return value, a
variable). To flip a boolean variable instead, see the next entry.

### A boolean variable that reads the wrong way

Words: invert a boolean variable, rename and negate a flag, ready becomes notReady, flip the meaning of a boolean

Ask: the action `Invert local variable`, with the position on the name of the variable at its declaration.

Before:

```java
boolean ready = true;
if (ready) {
    System.out.println(text);
}
```

After:

```java
boolean notReady = false;
if (!notReady) {
    System.out.println(text);
}
```

Limits: the declaration, the value and every use in the method change together, so the program means the same. The new
name is the one jdtls chooses (`notReady`): rename it afterwards with `gabier rename`.

### An `if` whose branches must trade places

Words: invert if, swap then and else, flip the branches, guard clause, early return

By hand: invert the condition with `Invert conditions` (previous entry), then swap the two blocks, and run `gabier
diagnostics`. jdtls does not offer the JDT UI quick assist that does both in one step.

Limits: see "What jdtls does not offer".

### `if (c) return a; else return b;`

Words: return conditional, return a boolean directly, if return else return, ternary

By hand: `return c ? a : b;`, or for booleans `return c;`.

Limits: see "What jdtls does not offer".

### Conditions to join or to split

Words: join conditions, split conditions, merge nested ifs, if and and, if or or, if else chain to switch

By hand: `if (a) { if (b) { x(); } }` is `if (a && b) { x(); }`, and the reverse; an `if` / `else if` chain on one
value is a `switch` (`useSwitchForInstanceofPattern` does it for a chain of `instanceof` tests only).

Limits: see "What jdtls does not offer". The one exception: after a dead-code fix on `a && false`, jdtls also offers the
split of the `&&` or `||`; it is not offered anywhere else.

### An `else` after a `return`

Words: unnecessary else, else after return, else after throw, flatten

By hand: remove the `else` and its braces, move the block after the `if`.

Limits: jdtls has a fix for it, but only as a compiler problem (`unnecessaryElse`), and the compiler preference that
turns the problem on is off by default, so `gabier diagnostics` does not report it and `gabier actions` does not list a
fix.

## What jdtls does not offer

jdtls 1.63.0 builds its code actions in two places (`RefactorProcessor` and `QuickAssistProcessor`) and wires only a
part of the quick assists of Eclipse JDT UI into them. The texts of the others (invert an `if`, `if return else
return` to a return of a conditional, join and split conditions, `if` / `else` to `switch`, join the sequence of
`if`s) are in its `CorrectionMessages`, and no code uses them. They are not hidden behind a setting or a client
capability: they are not called, so no request can show them. These are the "by hand" entries above; gabier does not
stand in for them.
