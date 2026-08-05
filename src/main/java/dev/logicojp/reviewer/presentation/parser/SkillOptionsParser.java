package dev.logicojp.reviewer.presentation.parser;

import dev.logicojp.reviewer.presentation.CliParsing;
import dev.logicojp.reviewer.presentation.CliValidationException;
import dev.logicojp.reviewer.presentation.SkillOptions;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Singleton
public class SkillOptionsParser {

    public Optional<SkillOptions> parse(String[] args) {
        args = Objects.requireNonNullElse(args, new String[0]);
        var state = new SkillParseState();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            i = applyOption(state, arg, args, i);
            if (state.helpRequested) {
                return Optional.empty();
            }
        }

        return Optional.of(new SkillOptions(
            state.skillId,
            List.copyOf(state.paramStrings),
            state.githubToken,
            state.model,
            List.copyOf(state.additionalAgentDirs),
            state.listSkills
        ));
    }

    private static class SkillParseState {
        String skillId;
        final List<String> paramStrings = new ArrayList<>();
        String githubToken;
        String model = null; // null means use configured default
        final List<Path> additionalAgentDirs = new ArrayList<>();
        boolean listSkills;
        boolean helpRequested;
    }

    private int applyOption(SkillParseState state, String arg, String[] args, int i) {
        return switch (arg) {
            case "-h", "--help" -> {
                state.helpRequested = true;
                yield i;
            }
            case "-p", "--param" -> {
                CliParsing.OptionValue value = CliParsing.readSingleValue(arg, args, i, "--param");
                state.paramStrings.addAll(CliParsing.splitComma(value.value()));
                yield value.newIndex();
            }
            case "--token" -> CliParsing.readTokenInto(args, i, "--token", v -> state.githubToken = v);
            case "--model" -> CliParsing.readInto(args, i, "--model", v -> state.model = v);
            case "--agents-dir" -> CliParsing.readMultiInto(args, i, "--agents-dir",
                v -> state.additionalAgentDirs.add(Path.of(v)));
            case "--list" -> {
                state.listSkills = true;
                yield i;
            }
            default -> handlePositionalArgument(state, arg, i);
        };
    }

    private int handlePositionalArgument(SkillParseState state, String arg, int index) {
        if (arg.startsWith("-")) {
            throw new CliValidationException("Unknown option: " + arg, true);
        }
        if (state.skillId == null) {
            state.skillId = arg;
            return index;
        }
        throw new CliValidationException("Unexpected argument: " + arg, true);
    }
}
