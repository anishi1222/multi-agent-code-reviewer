package dev.logicojp.reviewer.presentation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// Parsed CLI options for a review run.
public record ReviewOptions(
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
        boolean noSummary,
        boolean noSharedSession
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
        String defaultModel,
        String reasoningEffort
    ) {}

    record RubberDuckOptions(
        boolean enabled,
        int dialogueRounds,
        String peerModel
    ) {}

    public ReviewOptions {
        output = output != null ? output : new OutputOptions(Path.of("./reports"), List.of(), 1, false, false);
        models = models != null ? models : new ModelOptions(null, null, null, null, null);
        rubberDuckOptions = rubberDuckOptions != null ? rubberDuckOptions : new RubberDuckOptions(false, 0, null);
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(agents, "agents must not be null");
    }

    public Path outputDirectory() { return output.outputDirectory(); }
    public List<Path> additionalAgentDirs() { return output.additionalAgentDirs(); }
    public int parallelism() { return output.parallelism(); }
    public boolean noSummary() { return output.noSummary(); }
    public boolean noSharedSession() { return output.noSharedSession(); }
    public String reviewModel() { return models.reviewModel(); }
    public String reportModel() { return models.reportModel(); }
    public String summaryModel() { return models.summaryModel(); }
    public String defaultModel() { return models.defaultModel(); }
    public String reasoningEffort() { return models.reasoningEffort(); }
    public boolean rubberDuck() { return rubberDuckOptions.enabled(); }
    public int dialogueRounds() { return rubberDuckOptions.dialogueRounds(); }

    public String peerModel() { return rubberDuckOptions.peerModel(); }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private ReviewTargetSelection target;
        private ReviewAgentSelection agents;
        private Path outputDirectory = Path.of("./reports");
        private List<Path> additionalAgentDirs = List.of();
        private int parallelism = 1;
        private boolean noSummary;
        private boolean noSharedSession;
        private String reviewModel;
        private String reportModel;
        private String summaryModel;
        private String defaultModel;
        private String reasoningEffort;
        private String githubToken;
        private boolean trustTarget;
        private boolean rubberDuck;
        private int dialogueRounds;
        private String peerModel;

        public Builder target(ReviewTargetSelection t) { this.target = t; return this; }
        public Builder agents(ReviewAgentSelection a) { this.agents = a; return this; }
        public Builder outputDirectory(Path p) { this.outputDirectory = p; return this; }
        public Builder additionalAgentDirs(List<Path> d) { this.additionalAgentDirs = d; return this; }
        public Builder githubToken(String t) { this.githubToken = t; return this; }
        public Builder parallelism(int p) { this.parallelism = p; return this; }
        public Builder noSummary(boolean b) { this.noSummary = b; return this; }
        public Builder noSharedSession(boolean b) { this.noSharedSession = b; return this; }
        public Builder reviewModel(String m) { this.reviewModel = m; return this; }
        public Builder reportModel(String m) { this.reportModel = m; return this; }
        public Builder summaryModel(String m) { this.summaryModel = m; return this; }
        public Builder defaultModel(String m) { this.defaultModel = m; return this; }
        public Builder reasoningEffort(String r) { this.reasoningEffort = r; return this; }
        public Builder trustTarget(boolean b) { this.trustTarget = b; return this; }
        public Builder rubberDuck(boolean b) { this.rubberDuck = b; return this; }
        public Builder dialogueRounds(int r) { this.dialogueRounds = r; return this; }
        public Builder peerModel(String m) { this.peerModel = m; return this; }

        public ReviewOptions build() {
            return new ReviewOptions(
                target, agents,
                new OutputOptions(outputDirectory, additionalAgentDirs, parallelism, noSummary, noSharedSession),
                new ModelOptions(reviewModel, reportModel, summaryModel, defaultModel, reasoningEffort),
                githubToken, trustTarget,
                new RubberDuckOptions(rubberDuck, dialogueRounds, peerModel)
            );
        }
    }
}
