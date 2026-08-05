package dev.logicojp.reviewer.domain.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SkillResult")
class SkillResultTest {

    @Nested
    @DisplayName("ファクトリメソッド")
    class FactoryMethods {

        @Test
        @DisplayName("successは成功結果を生成する")
        void successCreatesSuccessResult() {
            SkillResult result = SkillResult.success("skill-1", "content output");
            assertThat(result.success()).isTrue();
            assertThat(result.skillId()).isEqualTo("skill-1");
            assertThat(result.content()).isEqualTo("content output");
            assertThat(result.errorMessage()).isNull();
            assertThat(result.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("failureは失敗結果を生成する")
        void failureCreatesFailureResult() {
            SkillResult result = SkillResult.failure("skill-2", "timeout");
            assertThat(result.success()).isFalse();
            assertThat(result.skillId()).isEqualTo("skill-2");
            assertThat(result.content()).isNull();
            assertThat(result.errorMessage()).isEqualTo("timeout");
        }

        @Test
        @DisplayName("clock指定のsuccessは固定時刻を使う")
        void successWithClockUsesGivenTime() {
            Instant fixed = Instant.parse("2025-01-01T00:00:00Z");
            Clock clock = Clock.fixed(fixed, ZoneOffset.UTC);

            SkillResult result = SkillResult.success("skill-1", "content", clock);

            assertThat(result.timestamp()).isEqualTo(fixed);
        }

        @Test
        @DisplayName("clock指定のfailureは固定時刻を使う")
        void failureWithClockUsesGivenTime() {
            Instant fixed = Instant.parse("2025-01-01T00:00:00Z");
            Clock clock = Clock.fixed(fixed, ZoneOffset.UTC);

            SkillResult result = SkillResult.failure("skill-1", "error", clock);

            assertThat(result.timestamp()).isEqualTo(fixed);
        }
    }

    @Nested
    @DisplayName("コンストラクタ")
    class Constructor {

        @Test
        @DisplayName("timestampがnullの場合は例外を投げる")
        void nullTimestampThrows() {
            assertThatThrownBy(() -> new SkillResult("s1", true, "c", null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("timestamp must not be null");
        }

        @Test
        @DisplayName("timestampが指定された場合はそのまま保持される")
        void specifiedTimestampIsPreserved() {
            Instant ts = Instant.parse("2025-01-01T00:00:00Z");
            SkillResult result = new SkillResult("s1", true, "c", null, ts);
            assertThat(result.timestamp()).isEqualTo(ts);
        }
    }
}
