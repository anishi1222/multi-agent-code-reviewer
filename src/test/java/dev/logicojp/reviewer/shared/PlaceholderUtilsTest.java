package dev.logicojp.reviewer.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlaceholderUtils")
class PlaceholderUtilsTest {

    @Test
    @DisplayName("既知プレースホルダーを置換し未知キーは維持する")
    void replacesKnownKeysAndKeepsUnknown() {
        String template = "repo=${repository}, user=${user}, keep=${unknown}";

        String result = PlaceholderUtils.replaceDollarPlaceholders(template, Map.of(
            "repository", "owner/repo",
            "user", "alice"
        ));

        assertThat(result).isEqualTo("repo=owner/repo, user=alice, keep=${unknown}");
    }

    @Test
    @DisplayName("置換値に正規表現特殊文字が含まれていても安全に置換する")
    void safelyReplacesSpecialCharacters() {
        String template = "value=${token}";

        String result = PlaceholderUtils.replaceDollarPlaceholders(template, Map.of(
            "token", "$1\\path\\name"
        ));

        assertThat(result).isEqualTo("value=$1\\path\\name");
    }
}
