package dev.logicojp.reviewer.presentation.command;

import java.util.function.IntSupplier;

final class LifecycleRunner {

    private LifecycleRunner() {}

    static int executeWithLifecycle(Runnable initializer, IntSupplier executor, Runnable shutdowner) {
        try {
            initializer.run();
            return executor.getAsInt();
        } finally {
            shutdowner.run();
        }
    }
}
