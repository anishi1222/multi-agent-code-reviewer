package dev.logicojp.reviewer.cli;

import dev.logicojp.reviewer.target.ReviewTarget;
import dev.logicojp.reviewer.util.GitHubTokenResolver;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
class ReviewTargetResolver {

    public record TargetAndToken(ReviewTarget target, @Nullable String resolvedToken) {
        @Override
        public String toString() {
            return "TargetAndToken{target=%s, resolvedToken=***}".formatted(target);
        }
    }

    private final GitHubTokenResolver tokenResolver;

    @Inject
    public ReviewTargetResolver(GitHubTokenResolver tokenResolver) {
        this.tokenResolver = tokenResolver;
    }

    public TargetAndToken resolve(ReviewCommand.TargetSelection targetSelection, @Nullable String githubToken) {
        return switch (targetSelection) {
            case ReviewCommand.TargetSelection.Repository(String repository) ->
                resolveRepositoryTarget(repository, githubToken);
            case ReviewCommand.TargetSelection.LocalDirectory(Path localDir) ->
                resolveLocalTarget(localDir);
        };
    }

    private TargetAndToken resolveRepositoryTarget(String repository, @Nullable String githubToken) {
        String resolvedToken = requireRepositoryToken(githubToken);
        return new TargetAndToken(ReviewTarget.gitHub(repository), resolvedToken);
    }

    private String requireRepositoryToken(@Nullable String githubToken) {
        String resolvedToken = resolveToken(githubToken);
        if (resolvedToken == null || resolvedToken.isBlank()) {
            throw new CliValidationException(
                "GitHub token is required for repository review. Use --token or login with `gh auth login`.",
                true);
        }
        return resolvedToken;
    }

    private @Nullable String resolveToken(@Nullable String githubToken) {
        return tokenResolver.resolve(githubToken).orElse(null);
    }

    private TargetAndToken resolveLocalTarget(Path localDir) {
        Path localPath = localDir.toAbsolutePath();
        validateLocalDirectory(localPath);
        return new TargetAndToken(ReviewTarget.local(localPath), null);
    }

    private void validateLocalDirectory(Path localPath) {
        if (!Files.exists(localPath)) {
            throw new CliValidationException("Local directory does not exist: " + localPath, true);
        }
        if (!Files.isDirectory(localPath)) {
            throw new CliValidationException("Path is not a directory: " + localPath, true);
        }
    }
}
