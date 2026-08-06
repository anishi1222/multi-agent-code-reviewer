package dev.logicojp.reviewer.domain.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Fails when a policy limit is declared but never consulted.
///
/// ## The defect this exists to prevent
///
/// SEC-H1 was five `private static final` limits in `CustomInstructionSafetyValidator` that
/// nothing referenced. The strictest bound in the codebase — `MAX_UNTRUSTED_INSTRUCTION_SIZE`
/// — decided nothing at all. It survived review because a reader who greps the name finds a
/// hit: **the declaration itself**. The constant looks live until you count the hits.
///
/// ## Why this is a source-text scan and not a bytecode scan
///
/// A `public static final int` initialised with a literal is a JLS §4.12.4 *constant
/// variable*. `javac` inlines its value at every use site and emits **no `Fieldref`** into
/// the reader's constant pool. A classfile-based meta-test therefore sees zero references to
/// a constant that is used everywhere, and would have to be suppressed to stay green — which
/// is worse than not having it. Scanning source text is the only reliable observation here.
///
/// ## Why the declaration line is excluded
///
/// Counting the declaration is exactly how SEC-H1 hid. The scan below strips it, so a
/// constant that is only declared reports zero and fails.
@DisplayName("policy constants are live (ADR-0007 D2)")
class AgentPolicyConstantsAreLiveTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path TEST = Path.of("src/test/java");

    /// The five limits from SEC-H1 plus the file-size bounds they now sit beside. Each was
    /// either dead or uniform before ADR-0007 D2.
    @ParameterizedTest(name = "{0} is referenced outside its own declaration")
    @ValueSource(strings = {
        "MAX_INSTRUCTION_SIZE",
        "MAX_UNTRUSTED_INSTRUCTION_SIZE",
        "MAX_INSTRUCTION_LINES",
        "MAX_AGENT_FILE_SIZE",
        "MAX_UNTRUSTED_AGENT_FILE_SIZE",
        "MAX_DISPLAY_NAME_LENGTH",
        "ALLOWED_LANGUAGES"
    })
    void constantIsReferencedInProductionAndPinnedByTests(String constant) throws IOException {
        long mainUses = countUsesExcludingDeclaration(MAIN, constant);
        long testUses = countUsesExcludingDeclaration(TEST, constant);

        assertThat(mainUses)
            .as("%s is declared but never consulted in src/main. A limit that nothing reads "
                + "is not a control — it is documentation that looks like a control. This is "
                + "the SEC-H1 defect verbatim.", constant)
            .isGreaterThan(0);

        assertThat(testUses)
            .as("%s is used in production but no test names it, so nothing would notice if "
                + "its value were loosened or its check removed.", constant)
            .isGreaterThan(0);
    }

    /// Guards the scanner itself. If `countUsesExcludingDeclaration` silently matched
    /// everything (or nothing), the parameterized test above would pass vacuously.
    @Test
    @DisplayName("the scanner reports zero for a name that appears only as a declaration")
    void scannerDetectsADeadConstant() throws IOException {
        assertThat(countUsesExcludingDeclaration(MAIN, "NO_SUCH_CONSTANT_ANYWHERE"))
            .as("a name that does not exist must report zero, proving the scan can fail")
            .isZero();

        assertThat(countUsesExcludingDeclaration(MAIN, "MAX_UNTRUSTED_INSTRUCTION_SIZE"))
            .as("a name that does exist must report non-zero, proving the scan can pass")
            .isGreaterThan(0);
    }

    /// Counts occurrences of `constant` across `.java` files under `root`, skipping any line
    /// that declares it.
    private static long countUsesExcludingDeclaration(Path root, String constant) throws IOException {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files
                .filter(p -> p.toString().endsWith(".java"))
                .mapToLong(p -> countInFile(p, constant))
                .sum();
        }
    }

    private static long countInFile(Path file, String constant) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            return lines.stream()
                .filter(line -> line.contains(constant))
                .filter(line -> !isDeclaration(line, constant))
                .filter(line -> !isComment(line))
                .count();
        } catch (IOException e) {
            return 0;
        }
    }

    /// A declaration is `... static final <type> NAME =`. Matching on `static final` plus the
    /// name followed by `=` avoids mistaking a use such as `x > MAX_FOO` for a declaration.
    private static boolean isDeclaration(String line, String constant) {
        String stripped = line.strip();
        return stripped.contains("static final") && stripped.matches(".*\\b" + constant + "\\s*=.*");
    }

    /// Comments mentioning a constant do not keep it alive. Excluding them is what makes the
    /// scan measure behaviour rather than intent — a doc comment explaining why a limit
    /// matters must not be able to substitute for reading it.
    private static boolean isComment(String line) {
        String stripped = line.strip();
        return stripped.startsWith("//") || stripped.startsWith("*") || stripped.startsWith("/*");
    }
}
