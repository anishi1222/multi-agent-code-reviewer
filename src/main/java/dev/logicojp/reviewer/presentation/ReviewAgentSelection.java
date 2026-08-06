package dev.logicojp.reviewer.presentation;

import java.util.List;

/// Mutually exclusive agent selection parsed from CLI options.
public sealed interface ReviewAgentSelection
    permits ReviewAgentSelection.All,
            ReviewAgentSelection.Named {

    record All() implements ReviewAgentSelection {}

    record Named(List<String> agents) implements ReviewAgentSelection {}
}
