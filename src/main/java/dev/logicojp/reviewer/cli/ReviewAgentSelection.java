package dev.logicojp.reviewer.cli;

import java.util.List;

/// Mutually exclusive agent selection parsed from CLI options.
sealed interface ReviewAgentSelection
    permits ReviewAgentSelection.All,
            ReviewAgentSelection.Named {

    record All() implements ReviewAgentSelection {}

    record Named(List<String> agents) implements ReviewAgentSelection {}
}
