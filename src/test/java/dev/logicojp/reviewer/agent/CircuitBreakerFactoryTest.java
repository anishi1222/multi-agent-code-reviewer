package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.config.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CircuitBreakerFactory")
class CircuitBreakerFactoryTest {

    @Test
    @DisplayName("review/skill/summary 向けに独立したインスタンスを返す")
    void providesDedicatedCircuitBreakers() {
        var factory = new CircuitBreakerFactory(new CircuitBreakerConfig(3, 500));

        assertThat(factory.forReview()).isNotSameAs(factory.forSkill());
        assertThat(factory.forReview()).isNotSameAs(factory.forSummary());
        assertThat(factory.forSkill()).isNotSameAs(factory.forSummary());
    }
}
