package dev.logicojp.reviewer.domain.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/// Pins the observable boundary of the model-family allowlist.
///
/// SEC-L10 found that `ALLOWED_MODEL_PREFIXES` was live in production but named by no test.
/// Sampling one model from each family is not enough: it does not prove where each prefix starts
/// or ends, whether matching stays anchored at the beginning, or whether case folding remains
/// part of the contract. These matched acceptance/rejection pairs cover every configured family.
@DisplayName("モデルプレフィックス許可ポリシー")
class AgentModelPrefixPolicyTest {

    private static final List<String> EXPECTED_PREFIXES = List.of(
        "claude-", "gpt-", "o3", "o4-mini", "gemini-"
    );

    @Test
    @DisplayName("許可対象は定義済みの5モデルファミリーだけ")
    void configuredFamiliesArePinnedExactly() throws ReflectiveOperationException {
        assertThat(configuredPrefixes())
            .containsExactlyInAnyOrderElementsOf(EXPECTED_PREFIXES);
    }

    @Test
    @DisplayName("各プレフィックスは境界値・接尾辞付き・大文字でも受け入れる")
    void everyConfiguredPrefixAdmitsItsBoundaryAndExtensions() {
        for (String prefix : EXPECTED_PREFIXES) {
            assertAccepted(prefix);
            assertAccepted(prefix + "contract-suffix");
            assertAccepted(prefix.toUpperCase(Locale.ROOT) + "CONTRACT-SUFFIX");
        }
    }

    @Test
    @DisplayName("各プレフィックスの短縮形・先頭以外の一致・先頭空白は拒否する")
    void everyConfiguredPrefixRejectsNearMisses() {
        for (String prefix : EXPECTED_PREFIXES) {
            assertRejected(prefix.substring(0, prefix.length() - 1));
            assertRejected("vendor-" + prefix + "contract-suffix");
            assertRejected(" " + prefix + "contract-suffix");
        }
    }

    private static void assertAccepted(String model) {
        AgentDefinitionPolicy.PolicyResult result =
            AgentDefinitionPolicy.validateModel(model, "model");

        assertThat(result.accepted())
            .as("model '%s' should be admitted by its configured prefix", model)
            .isTrue();
        assertThat(result.ruleId())
            .as("accepted model '%s' must not report a rejection rule", model)
            .isNull();
    }

    private static void assertRejected(String model) {
        AgentDefinitionPolicy.PolicyResult result =
            AgentDefinitionPolicy.validateModel(model, "model");

        assertThat(result.accepted())
            .as("near-miss model '%s' must not satisfy a prefix match", model)
            .isFalse();
        assertThat(result.ruleId()).isEqualTo(AgentDefinitionPolicy.RULE_MODEL);
        assertThat(result.reason())
            .contains("model")
            .contains(model)
            .contains("not in the allowed model list");
    }

    private static List<String> configuredPrefixes() throws ReflectiveOperationException {
        Field field = AgentDefinitionPolicy.class.getDeclaredField("ALLOWED_MODEL_PREFIXES");
        field.setAccessible(true);

        Object value = field.get(null);
        assertThat(value).isInstanceOf(List.class);
        return ((List<?>) value).stream().map(String.class::cast).toList();
    }
}
