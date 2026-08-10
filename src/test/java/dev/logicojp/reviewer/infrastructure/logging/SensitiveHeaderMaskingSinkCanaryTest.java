package dev.logicojp.reviewer.infrastructure.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import dev.logicojp.reviewer.application.port.outbound.McpServerSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Canary control for ADR-0007 **D6** — masking is the sink's responsibility.
///
/// ADR-0007 D5 removed `SensitiveHeaderMasking.wrapHeaders` from [McpServerSpec]. That wrapper was
/// the only thing masking an `Authorization` header when a spec reached a log line, so removing it
/// **would be a straight regression** unless the sink masks instead. ADR-0007 says so explicitly:
/// D5 must not land before D6. This test is the evidence that it did not.
///
/// ## Why this test reads the shipped XML instead of its own fixture
///
/// A canary that defines its own patterns proves only that the canary works. The control has to
/// fail when **production** config is weakened, so it parses `src/main/resources/logback.xml` and
/// `logback-json.xml` and drives a real Logback [PatternLayout] with the pattern those files ship.
/// Deleting a property, dropping an alternative from either regex, unnesting the two `%replace`
/// layers, or letting the two profiles drift apart all turn this test red.
///
/// ## What it does not claim
///
/// Sink masking is text-shaped, so it protects **log and diagnostic output** only. It does not and
/// cannot protect a value that leaves the process by another route — a JSON request body, an
/// exception message forwarded to a remote service, a heap dump. That was equally true of the
/// wrapper it replaces (`MaskedHeadersMap.get()` returned the raw value by design), so this is not
/// a coverage loss; it is the boundary ADR-0007 draws, restated where someone will read it.
@DisplayName("ADR-0007 D6: the log sink masks secrets, not the objects being logged")
class SensitiveHeaderMaskingSinkCanaryTest {

    private static final Path LOGBACK = Path.of("src", "main", "resources", "logback.xml");
    private static final Path LOGBACK_JSON = Path.of("src", "main", "resources", "logback-json.xml");

    /// A value no production code path can produce, so its presence in rendered output is
    /// unambiguous evidence of a leak rather than a coincidence.
    private static final String CANARY = "CANARY7f3a9c2eLEAKED";

    private static String read(Path p) throws IOException {
        assertTrue(Files.exists(p), () -> "Logging config not found: " + p.toAbsolutePath());
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /// Extracts `<property name="..." value="..."/>` from a Logback config, undoing the one XML
    /// entity our patterns need (`&quot;`).
    private static String property(String xml, String name) {
        Matcher m = Pattern.compile(
            "<property\\s+name=\"" + Pattern.quote(name) + "\"\\s+value=\"([^\"]*)\"\\s*/>").matcher(xml);
        assertTrue(m.find(), () -> """
            Logging config no longer declares the `%s` property.

            ADR-0007 D6 makes the sink responsible for masking; deleting this property removes that \
            responsibility and silently unmasks every header that reaches a log line. Restore it.
            """.formatted(name));
        return m.group(1).replace("&quot;", "\"");
    }

    private static String consolePattern(String xml) {
        Matcher m = Pattern.compile("<pattern>(.*?)</pattern>", Pattern.DOTALL).matcher(xml);
        assertTrue(m.find(), "No <pattern> element found in the logging config");
        return m.group(1);
    }

    /// Renders `message` through a real Logback [PatternLayout] driven by the **shipped** console
    /// pattern, with the shipped mask patterns substituted in — i.e. through the same code path
    /// that formats production output. Joran performs `${...}` substitution when it reads the XML;
    /// [PatternLayout] does not do it itself, so the test does it here, from the same file.
    private static String renderThroughShippedSink(String message) throws IOException {
        String xml = read(LOGBACK);
        String pattern = consolePattern(xml)
            .replace("${MASK_PATTERN}", property(xml, "MASK_PATTERN"))
            .replace("${HEADER_MASK_PATTERN}", property(xml, "HEADER_MASK_PATTERN"));

        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        PatternLayout layout = new PatternLayout();
        layout.setContext(context);
        layout.setPattern(pattern);
        layout.start();
        try {
            assertTrue(layout.isStarted(), () -> """
                Logback refused to start the shipped console pattern.

                A mask pattern contains a character Logback's option parser cannot carry (an \
                unquoted `}` or `,` inside %%replace, most likely), so production logging would \
                fall back to an unmasked or broken layout.

                Resolved pattern: %s
                """.formatted(pattern));

            Logger logger = context.getLogger(SensitiveHeaderMaskingSinkCanaryTest.class);
            return layout.doLayout(
                new LoggingEvent(Logger.FQCN, logger, Level.INFO, message, null, null));
        } finally {
            layout.stop();
        }
    }

    @Nested
    @DisplayName("the same inputs the removed wrapper used to guard")
    class SameInputsTheWrapperGuarded {

        /// This is the exact regression ADR-0007 D5 risks: before D5, `McpServerSpec.headers` was a
        /// `MaskedHeadersMap` whose `toString()` masked. After D5 it is a plain `Map.copyOf`, so the
        /// record renders the raw token — and the sink is the only thing standing between that
        /// string and the terminal.
        @Test
        @DisplayName("an McpServerSpec carrying a bearer token renders masked")
        void bearerTokenInAnMcpServerSpecIsMasked() throws IOException {
            McpServerSpec spec = new McpServerSpec(
                "github",
                "https://api.githubcopilot.com/mcp/",
                Map.of("Authorization", "Bearer " + CANARY),
                List.of());

            assertTrue(spec.toString().contains(CANARY),
                "Precondition: the record must render the raw token, otherwise this test proves "
                    + "nothing about the sink. If this fails, object-level masking has come back — "
                    + "see ADR-0007 D5 before 'fixing' it.");

            assertMasked(spec.toString());
        }

        /// The branch `MASK_PATTERN` alone cannot cover, and the reason D6 needed a second pattern:
        /// a custom header from `reviewer.mcp.github.headers` whose **value** has no recognizable
        /// token prefix. Value-shape masking is blind to it; only header-*name* masking catches it.
        /// The removed wrapper was name-based, so without `HEADER_MASK_PATTERN` this is precisely
        /// where D5 would have lost coverage.
        @Test
        @DisplayName("an opaque custom header value is masked by name, not by shape")
        void opaqueCustomHeaderValueIsMasked() throws IOException {
            McpServerSpec spec = new McpServerSpec(
                "github",
                "https://api.githubcopilot.com/mcp/",
                Map.of("X-API-Key", CANARY + "!v@lue#"),
                List.of());

            assertMasked(spec.toString());
        }

        @Test
        @DisplayName("every header name the helper calls sensitive is masked at the sink")
        void everySensitiveHeaderNameIsMasked() throws IOException {
            // Mirrors SensitiveHeaderMasking.SENSITIVE_PATTERNS. If a name is added there but not to
            // HEADER_MASK_PATTERN, the two halves of the masking story disagree — and this fails.
            for (String header : List.of("Authorization", "X-Auth-Token", "api-key", "apikey",
                "X-Api-Key", "X-Client-Secret", "password", "X-Credential", "Cookie")) {
                assertMasked("mcp headers={" + header + "=" + CANARY + "}",
                    "header name `" + header + "` is not masked at the sink");
            }
        }

        @Test
        @DisplayName("a raw token is masked even outside a header, by value shape")
        void rawTokenIsMaskedByShape() throws IOException {
            for (String token : List.of("ghp_" + CANARY, "github_pat_" + CANARY, "sk-" + CANARY,
                "xoxb-" + CANARY)) {
                assertMasked("resolved credential " + token,
                    "token shape `" + token.substring(0, 5) + "…` is not masked at the sink");
            }
        }

        @Test
        @DisplayName("non-sensitive content survives untouched")
        void benignContentIsNotMangled() throws IOException {
            String rendered = renderThroughShippedSink("review completed files=12 duration=1500ms");
            assertTrue(rendered.contains("review completed files=12 duration=1500ms"),
                () -> "Masking damaged an ordinary log message: " + rendered);
        }

        private void assertMasked(String message) throws IOException {
            assertMasked(message, "the sink did not mask a secret it is responsible for");
        }

        private void assertMasked(String message, String why) throws IOException {
            String rendered = renderThroughShippedSink(message);
            assertFalse(rendered.contains(CANARY), () -> """
                SECRET LEAKED THROUGH THE LOG SINK — %s.

                ADR-0007 D5 removed object-level masking from the port on the explicit promise \
                (D6) that the sink masks instead. This assertion is that promise. A red here means \
                secrets are reaching real log output, not that the test is too strict.

                Rendered: %s
                """.formatted(why, rendered));
        }
    }

    @Nested
    @DisplayName("the shipped configuration keeps its shape")
    class ShippedConfigurationKeepsItsShape {

        @Test
        @DisplayName("both profiles declare identical mask patterns")
        void bothProfilesDeclareIdenticalPatterns() throws IOException {
            String plain = read(LOGBACK);
            String json = read(LOGBACK_JSON);

            assertEquals(property(plain, "MASK_PATTERN"), property(json, "MASK_PATTERN"),
                "logback.xml and logback-json.xml disagree on MASK_PATTERN — one profile masks "
                    + "less than the other, and which one you get depends on a system property.");
            assertEquals(property(plain, "HEADER_MASK_PATTERN"), property(json, "HEADER_MASK_PATTERN"),
                "logback.xml and logback-json.xml disagree on HEADER_MASK_PATTERN — see above.");
        }

        /// Order is load-bearing, not cosmetic. `HEADER_MASK_PATTERN`'s value class stops at
        /// whitespace, so on `Authorization=Bearer <token>` it would consume only the word `Bearer`
        /// and leave the token in the output. `MASK_PATTERN` must run first (innermost) and collapse
        /// `Bearer <token>` before the name-based pass sees it.
        @Test
        @DisplayName("value-shape masking is nested inside header-name masking")
        void maskPatternIsAppliedBeforeHeaderMaskPattern() throws IOException {
            for (Path config : List.of(LOGBACK, LOGBACK_JSON)) {
                String xml = read(config);
                for (String pattern : patterns(xml)) {
                    int mask = pattern.indexOf("${MASK_PATTERN}");
                    int header = pattern.indexOf("${HEADER_MASK_PATTERN}");
                    assertTrue(mask >= 0, () -> config + ": an appender pattern applies no MASK_PATTERN");
                    assertTrue(header >= 0,
                        () -> config + ": an appender pattern applies no HEADER_MASK_PATTERN");
                    assertTrue(mask < header, () -> """
                        %s: HEADER_MASK_PATTERN is applied before MASK_PATTERN.

                        The name-based pass stops at whitespace, so it masks only the word `Bearer` \
                        and leaves the token behind. Nest MASK_PATTERN innermost.

                        Pattern: %s
                        """.formatted(config, pattern));
                }
            }
        }

        @Test
        @DisplayName("every appender masks — an unmasked appender is a hole in the sink")
        void everyAppenderMasks() throws IOException {
            for (Path config : List.of(LOGBACK, LOGBACK_JSON)) {
                List<String> patterns = patterns(read(config));
                assertFalse(patterns.isEmpty(), () -> config + ": no <pattern> elements found");
                for (String pattern : patterns) {
                    assertTrue(pattern.contains("${MASK_PATTERN}")
                            && pattern.contains("${HEADER_MASK_PATTERN}"),
                        () -> """
                            %s ships an appender whose pattern does not mask.

                            Masking is per-appender in Logback: adding an appender without both \
                            %%replace layers routes unmasked output straight to its destination.

                            Pattern: %s
                            """.formatted(config, pattern));
                }
            }
        }

        private List<String> patterns(String xml) {
            return Pattern.compile("<pattern>(.*?)</pattern>", Pattern.DOTALL)
                .matcher(xml).results().map(r -> r.group(1)).toList();
        }
    }

    @Test
    @DisplayName("the port no longer masks its own fields (ADR-0007 D5 stays done)")
    void portDoesNotMaskItsOwnFields() {
        // Rule 4b in LayerDependencyRulesTest enforces the *dependency*; this pins the *behaviour*,
        // so re-introducing masking by any other route — a hand-rolled wrapper, an overridden
        // accessor — is caught here even though it would not name `SensitiveHeaderMasking`.
        Map<String, String> headers = Map.of("Authorization", "Bearer " + CANARY);
        McpServerSpec spec = new McpServerSpec("github", "https://x", headers, List.of());

        assertEquals("Bearer " + CANARY, spec.headers().get("Authorization"),
            "The port must expose the raw value; masking belongs to the sink (ADR-0007 D5/D6).");
        assertNotNull(spec.headers());
        assertTrue(spec.toString().contains(CANARY),
            "Object-level masking has returned to McpServerSpec. It cannot work — the SDK stores "
                + "this map without a defensive copy and overrides no toString() — and ADR-0007 D5 "
                + "removed it deliberately. Mask at the sink instead.");
    }
}
