package dev.logicojp.reviewer.infrastructure.file;

import dev.logicojp.reviewer.application.port.outbound.WriteReportPort;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/// Infrastructure-owned construction of filesystem outbound adapters.
@Factory
public final class FilePortFactory {

    @Singleton
    WriteReportPort writeReportPort() {
        return new ReportFileWriter();
    }
}
