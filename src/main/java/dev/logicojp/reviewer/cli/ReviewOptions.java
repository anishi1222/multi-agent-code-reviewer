package dev.logicojp.reviewer.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// Parsed CLI options for a review run.
record ReviewOptions(
    ReviewTargetSelection target,
    ReviewAgentSelection agents,
    OutputOptions output,
    ModelOptions models,
    String githubToken,
    boolean trustTarget,
    RubberDuckOptions rubberDuckOptions
) {
    record OutputOptions(
        Path outputDirectory,
        List<Path> additionalAgentDirs,
        int parallelism,
        boolean noSummary
    ) {
        OutputOptions {
            outputDirectory = outputDirectory != null ? outputDirectory : Path.of("./reports");
            additionalAgentDirs = additionalAgentDirs != null ? List.copyOf(additionalAgentDirs) : List.of();
            parallelism = parallelism > 0 ? parallelism : 1;
        }
    }

    record ModelOptions(
        String reviewModel,
        String reportModel,
        String summaryModel,
        String defaultModel
    ) {
    }

    record RubberDuckOptions(
        boolean enabled,
        boolean disabled,
        boolean compactPrompts,
        int dialogueRounds,
        String peerModel
    ) {
    }

    ReviewOptions {
        output = output != null ? output : new OutputOptions(Path.of("./reports"), List.of(), 1, false);
        models = models != null ? models : new ModelOptions(null, null, null, null);
        rubberDuckOptions = rubberDuckOptions != null
            ? rubberDuckOptions
            : new RubberDuckOptions(false, false, false, 0, null);
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(agents, "agents must not be null");
    }

    public Path outputDirectory() {
        return output.outputDirectory();
    }

    public List<Path> additionalAgentDirs() {
        return output.additionalAgentDirs();
    }

    public int parallelism() {
        return output.parallelism();
    }

    public boolean noSummary() {
        return output.noSummary();
    }

    public String reviewModel() {
        return models.reviewModel();
    }

    public String reportModel() {
        return models.reportModel();
    }

    public String summaryModel() {
        return models.summaryModel();
    }

    public String defaultModel() {
        return models.defaultModel();
    }

    public boolean rubberDuck() {
        return rubberDuckOptions.enabled();
    }

    public boolean noRubberDuck() {
        return rubberDuckOptions.disabled();
    }

    public boolean compactPrompts() {
        return rubberDuckOptions.compactPrompts();
    }

    public int dialogueRounds() {
        return rubberDuckOptions.dialogueRounds();
    }

    public String peerModel() {
        return rubberDuckOptions.peerModel();
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private ReviewTargetSelection target;
        private ReviewAgentSelection agents;
        private Path outputDirectory = Path.of("./reports");
        private List<Path> additionalAgentDirs = List.of();
        private int parallelism = 1;
        private boolean noSummary;
        private String reviewModel;
        private String reportModel;
        private String summaryModel;
        private String defaultModel;
        private String githubToken;
        private boolean trustTarget;
        private boolean rubberDuck;
        private boolean noRubberDuck;
        private boolean compactPrompts;
        private int dialogueRounds;
        private String peerModel;

        Builder target(ReviewTargetSelection target) {
            this.target = target;
            return this;
        }

        Builder agents(ReviewAgentSelection agents) {
            this.agents = agents;
            return this;
        }

        Builder outputDirectory(Path outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        Builder additionalAgentDirs(List<Path> additionalAgentDirs) {
            this.additionalAgentDirs = additionalAgentDirs;
            return this;
        }

        Builder githubToken(String githubToken) {
            this.githubToken = githubToken;
            return this;
        }

        Builder parallelism(int parallelism) {
            this.parallelism = parallelism;
            return this;
        }

        Builder noSummary(boolean noSummary) {
            this.noSummary = noSummary;
            return this;
        }

        Builder reviewModel(String reviewModel) {
            this.reviewModel = reviewModel;
            return this;
        }

        Builder reportModel(String reportModel) {
            this.reportModel = reportModel;
            return this;
        }

        Builder summaryModel(String summaryModel) {
            this.summaryModel = summaryModel;
            return this;
        }

        Builder defaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
            return this;
        }

        Builder trustTarget(boolean trustTarget) {
            this.trustTarget = trustTarget;
            return this;
        }

        Builder rubberDuck(boolean rubberDuck) {
            this.rubberDuck = rubberDuck;
            return this;
        }

        Builder noRubberDuck(boolean noRubberDuck) {
            this.noRubberDuck = noRubberDuck;
            return this;
        }

        Builder compactPrompts(boolean compactPrompts) {
            this.compactPrompts = compactPrompts;
            return this;
        }

        Builder dialogueRounds(int dialogueRounds) {
            this.dialogueRounds = dialogueRounds;
            return this;
        }

        Builder peerModel(String peerModel) {
            this.peerModel = peerModel;
            return this;
        }

        ReviewOptions build() {
            return new ReviewOptions(
                target,
                agents,
                new OutputOptions(outputDirectory, additionalAgentDirs, parallelism, noSummary),
                new ModelOptions(reviewModel, reportModel, summaryModel, defaultModel),
                githubToken,
                trustTarget,
                new RubberDuckOptions(rubberDuck, noRubberDuck, compactPrompts, dialogueRounds, peerModel)
            );
        }
    }
}
