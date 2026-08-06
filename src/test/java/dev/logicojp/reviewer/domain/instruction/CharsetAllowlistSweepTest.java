package dev.logicojp.reviewer.domain.instruction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/// Exhaustively enumerates what the character allowlist admits, and asserts nothing invisible
/// survives.
///
/// ## The defect this exists to prevent (SEC-H3)
///
/// [CustomInstructionSafetyValidator#containsOnlyAllowedCharacters] guards repository-supplied
/// agent definitions — untrusted markdown that becomes LLM instructions. It was written as a
/// whitelist of Unicode *block ranges*. A block is named for what its author wanted from it and
/// admits **everything else in it**.
///
/// `\uFF00-\uFFEF` was included for fullwidth ASCII and halfwidth katakana. It also carries
/// **U+FFA0 HALFWIDTH HANGUL FILLER**, which renders as blank and is general category `Lo`
/// (OTHER_LETTER). That single property defeated both layers of defence at once:
///
/// - the charset allowlist admitted it, because it is in the block;
/// - the prompt-injection denylist never saw it, because `ig<U+FFA0>nore all previous
///   instructions` does not match `ignore\s+(all\s+)?previous\s+instructions?`, and the
///   normalisation strip is `[\p{Cf}\p{Cc}]` while U+FFA0 is a *letter*.
///
/// Two consecutive security reviews passed with a green suite, because the constant's only
/// behavioural pin asserted **one** codepoint (U+202E) against a 33,478-codepoint allowlist.
/// Asking "is the test green" was the wrong question; the right one is "what does the test
/// actually range over".
///
/// ## Why this test enumerates rather than samples
///
/// The input domain is finite — 1,114,112 codepoints — so it can be enumerated outright.
/// Reasoning about which blocks "look risky" is what produced the defect twice: F1 narrowed
/// `\u2000-\u206F` by hand and got it exactly right, and the identical mistake survived
/// untouched in three other ranges of the same constant. Hand-enumeration loses; the sweep is
/// the evidence.
@DisplayName("character allowlist admits nothing invisible (SEC-H3)")
class CharsetAllowlistSweepTest {

    private static final int MAX_CODE_POINT = 0x10FFFF;

    /// Admitted by the shipped predicate, whatever it currently is.
    private static List<Integer> admittedCodePoints() {
        List<Integer> admitted = new ArrayList<>(40_000);
        for (int cp = 0; cp <= MAX_CODE_POINT; cp++) {
            if (CustomInstructionSafetyValidator.containsOnlyAllowedCharacters(
                new String(Character.toChars(cp)))) {
                admitted.add(cp);
            }
        }
        return admitted;
    }

    /// Deliberately admitted despite being category `Cc`. An instruction is multi-line text.
    private static final Set<Integer> DELIBERATE_WHITESPACE = Set.of((int) '\t', (int) '\n', (int) '\r');

    /// Categories whose members cannot legitimately appear in an agent definition:
    /// formatting and control characters, unassigned codepoints (a future Unicode version
    /// decides what they become), private use, surrogates, line/paragraph separators, and
    /// combining marks — which render *on top of* the preceding glyph rather than as
    /// themselves, and so break a denylist keyword without being visible.
    private static boolean inBlockedCategory(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.FORMAT
            || type == Character.CONTROL
            || type == Character.UNASSIGNED
            || type == Character.PRIVATE_USE
            || type == Character.SURROGATE
            || type == Character.LINE_SEPARATOR
            || type == Character.PARAGRAPH_SEPARATOR
            || type == Character.NON_SPACING_MARK
            || type == Character.ENCLOSING_MARK;
    }

    /// Independent oracle for "renders as nothing but is not a `Cf`/`Cc` format character".
    ///
    /// This reads the **official Unicode name** from the JDK's own character tables rather
    /// than from anyone's memory of which codepoints are blank. It is deliberately *not* the
    /// mechanism the production code uses — production pins an explicit set — so this
    /// assertion can genuinely disagree with production rather than restating it.
    private static boolean isNamedInvisible(int codePoint) {
        String name;
        try {
            name = Character.getName(codePoint);
        } catch (RuntimeException e) {
            return false;
        }
        if (name == null) {
            return false;
        }
        return name.contains("FILLER")
            || name.endsWith("BLANK")
            || name.contains("ZERO WIDTH")
            || name.contains("INVISIBLE")
            || name.contains("WORD JOINER");
    }

    private static String describe(int codePoint) {
        String name = Character.getName(codePoint);
        return "U+%04X (type=%d, %s)".formatted(
            codePoint, Character.getType(codePoint), name == null ? "<unassigned>" : name);
    }

    @Nested
    @DisplayName("the sweep")
    class TheSweep {

        @Test
        @DisplayName("no admitted codepoint is invisible, unassigned or a combining mark")
        void admittedSetContainsNothingInvisible() {
            List<String> offenders = new ArrayList<>();
            for (int codePoint : admittedCodePoints()) {
                if (DELIBERATE_WHITESPACE.contains(codePoint)) {
                    continue;
                }
                if (inBlockedCategory(codePoint) || isNamedInvisible(codePoint)) {
                    offenders.add(describe(codePoint));
                }
            }

            assertThat(offenders)
                .as("""
                    Each of these is admitted by the character allowlist and renders as nothing, \
                    renders on top of the previous glyph, or is unassigned. Any one of them \
                    splits a denylist keyword while leaving the text visually unchanged, which \
                    is SEC-H3 verbatim. Do not fix by excluding the individual codepoints \
                    listed here - that is the mistake this test exists to stop, one codepoint \
                    later. Subtract the category.""")
                .isEmpty();
        }
    }

    @Nested
    @DisplayName("non-vacuity controls")
    class NonVacuityControls {

        /// A predicate that rejected *everything* would satisfy the sweep above trivially.
        @Test
        @DisplayName("the allowlist still admits the text this project is written in")
        void allowlistStillAdmitsLegitimateText() {
            assertThat(CustomInstructionSafetyValidator.containsOnlyAllowedCharacters(
                "Review the code.\nDon't skip tests\t- see line 12 (99%).")).isTrue();
            assertThat(CustomInstructionSafetyValidator.containsOnlyAllowedCharacters(
                "コードを日本語でレビューしてください。「重要」※注意 — 詳細は…"))
                .as("Japanese typography: corner brackets, kome-jirushi, em dash, ellipsis")
                .isTrue();
            assertThat(CustomInstructionSafetyValidator.containsOnlyAllowedCharacters(
                "全角ＡＢＣ／半角ｶﾅ／한글／中文／→←│■□●"))
                .as("fullwidth forms, halfwidth katakana, Hangul syllables, box and geometric marks")
                .isTrue();
        }

        /// If the subtraction were deleted, this count would collapse to zero. It is the
        /// direct measure of "the fix is doing something", and it names the range constant so
        /// the liveness scan can see that the range itself is exercised by a test.
        @Test
        @DisplayName("the category subtraction removes codepoints the raw range admits")
        void subtractionIsNotANoOp() {
            Set<Integer> removedBySubtraction = new TreeSet<>();
            for (int cp = 0; cp <= MAX_CODE_POINT; cp++) {
                String single = new String(Character.toChars(cp));
                boolean rangeAdmits = CustomInstructionSafetyValidator.ALLOWED_CHAR_RANGE
                    .matcher(single).matches();
                boolean predicateAdmits =
                    CustomInstructionSafetyValidator.containsOnlyAllowedCharacters(single);
                if (rangeAdmits && !predicateAdmits) {
                    removedBySubtraction.add(cp);
                }
            }

            assertThat(removedBySubtraction)
                .as("the block ranges admit these, and only the category subtraction stops "
                    + "them; an empty set here means the subtraction has been removed or "
                    + "bypassed and the sweep above is passing vacuously")
                .isNotEmpty()
                .contains(0xFFA0)
                .as("U+FFA0 HALFWIDTH HANGUL FILLER is the codepoint SEC-H3 was reported for")
                .contains(0xFFA0);
        }

        /// Guards the oracle used by the sweep. If `isNamedInvisible` matched everything or
        /// nothing, `admittedSetContainsNothingInvisible` would pass without meaning anything.
        @Test
        @DisplayName("the invisibility oracle can both fire and stay silent")
        void invisibilityOracleIsDiscriminating() {
            assertThat(isNamedInvisible(0xFFA0))
                .as("HALFWIDTH HANGUL FILLER must be recognised, or the sweep proves nothing")
                .isTrue();
            assertThat(isNamedInvisible('A'))
                .as("an ordinary letter must not be recognised, or the sweep flags everything")
                .isFalse();
            assertThat(isNamedInvisible(0x3042))
                .as("HIRAGANA LETTER A must not be recognised")
                .isFalse();
        }
    }

    @Nested
    @DisplayName("the pinned invisible-codepoint set is derived, not remembered")
    class PinnedSetIsDerived {

        /// The production set is asserted **equal** to the derived one, not a superset of it.
        ///
        /// A subset assertion would let the set rot in both directions: a codepoint could be
        /// dropped without notice, and a stale entry could linger after a range change made it
        /// unreachable. Exact equality means a JDK Unicode upgrade that introduces a new
        /// blank-rendering letter fails this build **naming it**, instead of silently changing
        /// what the security control admits.
        @Test
        @DisplayName("it equals exactly the blank-rendering codepoints the category mask cannot see")
        void pinnedSetEqualsUnicodeDerivedSet() {
            Set<Integer> derived = new LinkedHashSet<>();
            for (int cp = 0; cp <= 0xFFFF; cp++) {
                if (isNamedInvisible(cp) && !inBlockedCategory(cp)) {
                    derived.add(cp);
                }
            }

            assertThat(CustomInstructionSafetyValidator.INVISIBLE_CODE_POINTS)
                .as("""
                    Derived from the JDK's own Unicode name tables. A mismatch means either the \
                    JDK's Unicode version has changed what exists, or someone edited the set by \
                    hand. Both need a human decision - do not silence this by copying the \
                    derived value in without reading what changed.""")
                .containsExactlyInAnyOrderElementsOf(derived);

            assertThat(derived)
                .as("the derivation itself must find something, or the equality above is "
                    + "satisfied by two empty sets")
                .isNotEmpty();
        }
    }
}
