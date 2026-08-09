package dev.logicojp.reviewer;

import dev.logicojp.reviewer.infrastructure.startup.StartupEnvironment;
import dev.logicojp.reviewer.infrastructure.startup.SystemStartupEnvironment;
import dev.logicojp.reviewer.presentation.CliApplication;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

/// Stable layer-zero process entry point.
public final class ReviewApp {

    public static void main(String[] args) {
        StartupEnvironment startup = new SystemStartupEnvironment();
        startup.prepare();

        int exitCode;
        try (var context = ApplicationContext.builder()
            .mainClass(ReviewApp.class)
            .defaultEnvironments(Environment.CLI)
            .args(args)
            .start()) {
            var app = context.getBean(CliApplication.class);
            exitCode = app.execute(args);
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
