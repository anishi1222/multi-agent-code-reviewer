package dev.logicojp.reviewer.cli;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


@DisplayName("ExitCodes")
class ExitCodesTest {

    @Test
    @DisplayName("OKは0")
    void okIsZero() {
        Assertions.assertThat(ExitCodes.OK).isZero();
    }

    @Test
    @DisplayName("USAGEは2")
    void usageIsTwo() {
        Assertions.assertThat(ExitCodes.USAGE).isEqualTo(2);
    }

    @Test
    @DisplayName("SOFTWAREは1")
    void softwareIsOne() {
        Assertions.assertThat(ExitCodes.SOFTWARE).isEqualTo(1);
    }
}
