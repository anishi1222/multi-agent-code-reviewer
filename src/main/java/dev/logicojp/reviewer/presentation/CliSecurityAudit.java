package dev.logicojp.reviewer.presentation;

import dev.logicojp.reviewer.shared.LogValueSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Emits security audit events raised by the CLI adapter itself (PM behaviour AUTH-11).
///
/// This is deliberately *not* `infrastructure.logging.SecurityAuditLogger`: t4 §2 forbids
/// `presentation -> infrastructure`, so the CLI cannot call the infrastructure emitter directly.
/// Both emitters write to the same `SECURITY_AUDIT` logger with the same MDC field names, and
/// both delegate value sanitisation to [LogValueSanitizer] so the log-injection defence cannot
/// diverge between them.
///
/// See ADR-0006: the alternative — routing a CLI-originated audit record through an application
/// outbound port — was judged disproportionate for a single structured log statement.
public final class CliSecurityAudit {

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("SECURITY_AUDIT");

    private CliSecurityAudit() {
    }

    /// Writes one audit record with `event.category`, `event.action` and `audit.*` MDC fields.
    public static void log(String eventCategory,
                           String eventAction,
                           String message,
                           Map<String, String> attributes) {
        List<MDC.MDCCloseable> closeables = new ArrayList<>();
        try {
            closeables.add(MDC.putCloseable("event.category", LogValueSanitizer.sanitize(eventCategory)));
            closeables.add(MDC.putCloseable("event.action", LogValueSanitizer.sanitize(eventAction)));
            if (attributes != null) {
                attributes.forEach((key, value) -> {
                    if (key != null && !key.isBlank()) {
                        closeables.add(MDC.putCloseable("audit." + key, LogValueSanitizer.sanitize(value)));
                    }
                });
            }
            AUDIT_LOGGER.info(LogValueSanitizer.sanitize(message));
        } finally {
            closeables.forEach(MDC.MDCCloseable::close);
        }
    }
}
