package dev.logicojp.reviewer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Security audit logger helper.
/// Writes audit events to dedicated SECURITY_AUDIT logger with MDC fields.
public final class SecurityAuditLogger {

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("SECURITY_AUDIT");

    private SecurityAuditLogger() {
    }

    public static void log(String eventCategory, String eventAction, String message) {
        log(eventCategory, eventAction, message, Map.of());
    }

    public static void log(String eventCategory,
                           String eventAction,
                           String message,
                           Map<String, String> attributes) {
        List<MDC.MDCCloseable> closeables = new ArrayList<>();
        try {
            closeables.add(MDC.putCloseable("event.category", sanitizeForLogValue(eventCategory)));
            closeables.add(MDC.putCloseable("event.action", sanitizeForLogValue(eventAction)));
            if (attributes != null) {
                attributes.forEach((key, value) -> {
                    if (key != null && !key.isBlank()) {
                        closeables.add(MDC.putCloseable("audit." + key, sanitizeForLogValue(value)));
                    }
                });
            }
            AUDIT_LOGGER.info(sanitizeForLogValue(message));
        } finally {
            closeables.forEach(MDC.MDCCloseable::close);
        }
    }

    static String sanitizeForLogValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ');
    }
}
