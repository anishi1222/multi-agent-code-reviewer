package dev.logicojp.reviewer.infrastructure.parsing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import dev.logicojp.reviewer.domain.skill.SkillDefinition;

@DisplayName("SkillRegistry")
class SkillRegistryTest {

    private SkillRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SkillRegistry();
    }

    private SkillDefinition createSkill(String id, String name) {
        return new SkillDefinition(id, name, "desc", "prompt", List.of(), Map.of());
    }

    @Nested
    @DisplayName("register / get")
    class RegisterAndGet {

        @Test
        @DisplayName("スキルを登録して取得できる")
        void registerAndRetrieve() {
            SkillDefinition skill = createSkill("test-skill", "Test Skill");
            registry.register(skill);
            assertThat(registry.get("test-skill")).isPresent().contains(skill);
        }

        @Test
        @DisplayName("未登録のスキルIDはemptyを返す")
        void unregisteredReturnsEmpty() {
            assertThat(registry.get("unknown")).isEmpty();
        }

        @Test
        @DisplayName("registerAllで複数スキルを一括登録できる")
        void registerAllAddsMultiple() {
            var skills = List.of(
                createSkill("a", "A"),
                createSkill("b", "B")
            );
            registry.registerAll(skills);
            assertThat(registry.getAll().size()).isEqualTo(2);
            assertThat(registry.hasSkill("a")).isTrue();
            assertThat(registry.hasSkill("b")).isTrue();
        }

        @Test
        @DisplayName("discovery snapshotの置換で古いスキルを残さない")
        void replaceAllPublishesOneCanonicalSnapshot() {
            registry.register(createSkill("stale", "Stale"));
            SkillDefinition discovered = createSkill("discovered", "Discovered");

            registry.replaceAll(List.of(discovered));

            assertThat(registry.listAll()).containsExactly(discovered);
            assertThat(registry.findById("stale")).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAll / getSkillIds")
    class GetAllTests {

        @Test
        @DisplayName("getAllは登録済みの全スキルを返す")
        void getAllReturnsAll() {
            registry.register(createSkill("s1", "S1"));
            registry.register(createSkill("s2", "S2"));
            assertThat(registry.getAll()).hasSize(2);
        }

        @Test
        @DisplayName("getSkillIdsは全スキルIDを返す")
        void getSkillIdsReturnsIds() {
            registry.register(createSkill("x", "X"));
            registry.register(createSkill("y", "Y"));
            assertThat(registry.getSkillIds()).containsExactlyInAnyOrder("x", "y");
        }
    }

    @Nested
    @DisplayName("unregister / clear")
    class RemovalTests {

        @Test
        @DisplayName("unregisterで個別スキルを削除できる")
        void unregisterRemovesSkill() {
            registry.register(createSkill("rm", "RM"));
            assertThat(registry.hasSkill("rm")).isTrue();
            registry.unregister("rm");
            assertThat(registry.hasSkill("rm")).isFalse();
        }

        @Test
        @DisplayName("clearで全スキルを削除できる")
        void clearRemovesAll() {
            registry.register(createSkill("a", "A"));
            registry.register(createSkill("b", "B"));
            registry.clearAll();
            assertThat(registry.getAll().size()).isZero();
        }
    }

    @Nested
    @DisplayName("hasSkill / size")
    class UtilityTests {

        @Test
        @DisplayName("空のレジストリのsizeは0")
        void emptyRegistryHasSizeZero() {
            assertThat(registry.getAll().size()).isZero();
        }

        @Test
        @DisplayName("hasSkillは正しく判定する")
        void hasSkillChecksPresence() {
            assertThat(registry.hasSkill("x")).isFalse();
            registry.register(createSkill("x", "X"));
            assertThat(registry.hasSkill("x")).isTrue();
        }
    }
}
