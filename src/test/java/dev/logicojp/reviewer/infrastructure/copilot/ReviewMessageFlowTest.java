package dev.logicojp.reviewer.infrastructure.copilot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewMessageFlow")
class ReviewMessageFlowTest {

    private static ReviewMessageFlow newFlow() {
        return new ReviewMessageFlow(
            "security",
            "FOLLOWUP",
            "LOCAL_HEADER",
            "LOCAL_RESULT",
            32
        );
    }

    @Test
    @DisplayName("リモートレビューで初回応答があれば1回送信で終了する")
    void remoteSuccessOnFirstSend() throws Exception {
        ReviewMessageFlow flow = newFlow();
        List<String> prompts = new ArrayList<>();

        String result = flow.execute("INSTRUCTION", null, prompt -> {
            prompts.add(prompt);
            return "REMOTE_OK";
        });

        assertThat(result).isEqualTo("REMOTE_OK");
        assertThat(prompts).containsExactly("INSTRUCTION");
    }

    @Test
    @DisplayName("リモートレビューで空応答ならフォローアップを送信する")
    void remoteFallsBackToFollowUp() throws Exception {
        ReviewMessageFlow flow = newFlow();
        List<String> prompts = new ArrayList<>();
        AtomicInteger count = new AtomicInteger();

        String result = flow.execute("INSTRUCTION", null, prompt -> {
            prompts.add(prompt);
            return count.getAndIncrement() == 0 ? "" : "FOLLOWUP_OK";
        });

        assertThat(result).isEqualTo("FOLLOWUP_OK");
        assertThat(prompts).containsExactly("INSTRUCTION", "FOLLOWUP");
    }

    @Test
    @DisplayName("ローカルレビューでは instruction/header/source/result要求を1回で送信する")
    void localSendsCombinedPromptOnce() throws Exception {
        ReviewMessageFlow flow = newFlow();
        List<String> prompts = new ArrayList<>();

        String result = flow.execute("INSTRUCTION", "SOURCE", prompt -> {
            prompts.add(prompt);
            return "LOCAL_OK";
        });

        assertThat(result).isEqualTo("LOCAL_OK");
        assertThat(prompts).hasSize(1);
        assertThat(prompts.getFirst()).contains("INSTRUCTION");
        assertThat(prompts.getFirst()).contains("LOCAL_HEADER");
        assertThat(prompts.getFirst()).contains("SOURCE");
        assertThat(prompts.getFirst()).contains("LOCAL_RESULT");
    }

    @Test
    @DisplayName("ローカルで結合プロンプトが空ならフォローアップを送る")
    void localFallsBackToFollowUpWhenCombinedPromptIsEmpty() throws Exception {
        ReviewMessageFlow flow = newFlow();
        List<String> prompts = new ArrayList<>();
        AtomicInteger count = new AtomicInteger();

        String result = flow.execute("INSTRUCTION", "SOURCE", prompt -> {
            prompts.add(prompt);
            return count.getAndIncrement() == 0 ? "" : "FOLLOWUP_OK";
        });

        assertThat(result).isEqualTo("FOLLOWUP_OK");
        assertThat(prompts).hasSize(2);
        assertThat(prompts.getFirst()).contains("INSTRUCTION");
        assertThat(prompts.getFirst()).contains("SOURCE");
        assertThat(prompts.get(1)).isEqualTo("FOLLOWUP");
    }

    @Test
    @DisplayName("ローカルで結合プロンプトとフォローアップが空ならnullを返す")
    void localFallsBackToFollowUpWhenStillEmpty() throws Exception {
        ReviewMessageFlow flow = newFlow();
        List<String> prompts = new ArrayList<>();

        String result = flow.execute("INSTRUCTION", "SOURCE", prompt -> {
            prompts.add(prompt);
            return " ";
        });

        assertThat(result).isNull();
        assertThat(prompts).hasSize(2);
        assertThat(prompts.getFirst()).contains("INSTRUCTION");
        assertThat(prompts.getFirst()).contains("SOURCE");
        assertThat(prompts.get(1)).isEqualTo("FOLLOWUP");
    }

    @Test
    @DisplayName("差し替えた応答判定戦略を利用する")
    void usesInjectedResponseEvaluator() throws Exception {
        ReviewMessageFlow flow = newFlow();
        AtomicBoolean evaluatorCalled = new AtomicBoolean(false);

        String result = flow.execute(
            "INSTRUCTION",
            null,
            _ -> "   ",
            content -> {
                evaluatorCalled.set(true);
                return true;
            }
        );

        assertThat(evaluatorCalled).isTrue();
        assertThat(result).isEqualTo("   ");
    }
}
