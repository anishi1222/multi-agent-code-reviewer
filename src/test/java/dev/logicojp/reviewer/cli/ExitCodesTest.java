package dev.logicojp.reviewer.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExitCodes")
class ExitCodesTest {

    @Test
    @DisplayName("OKは0である")
    void okIsZero() {
        assertThat(ExitCodes.OK).isZero();
    }

    @Test
    @DisplayName("SOFTWAREは1である")
    void softwareIsOne() {
        assertThat(ExitCodes.SOFTWARE).isEqualTo(1);
    }

    @Test
    @DisplayName("USAGEは2である")
    void usageIsTwo() {
        assertThat(ExitCodes.USAGE).isEqualTo(2);
    }

    @Test
    @DisplayName("CONFIGは3である")
    void configIsThree() {
        assertThat(ExitCodes.CONFIG).isEqualTo(3);
    }

    @Test
    @DisplayName("UNAVAILABLEは4である")
    void unavailableIsFour() {
        assertThat(ExitCodes.UNAVAILABLE).isEqualTo(4);
    }

    @Test
    @DisplayName("全てのコードが異なる値である")
    void allCodesAreDistinct() {
        Set<Integer> codes = Set.of(
            ExitCodes.OK,
            ExitCodes.SOFTWARE,
            ExitCodes.USAGE,
            ExitCodes.CONFIG,
            ExitCodes.UNAVAILABLE
        );
        assertThat(codes).hasSize(5);
    }
}
