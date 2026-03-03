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
        List<String> addedKeys = new ArrayList<>();
        addedKeys.add("event.category");
        MDC.put("event.category", sanitizeForLogValue(eventCategory));
        addedKeys.add("event.action");
        MDC.put("event.action", sanitizeForLogValue(eventAction));
        if (attributes != null) {
            attributes.forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    String mdcKey = "audit." + key;
                    MDC.put(mdcKey, sanitizeForLogValue(value));
                    addedKeys.add(mdcKey);
                }
            });
        }
        try {
            AUDIT_LOGGER.info(sanitizeForLogValue(message));
        } finally {
            addedKeys.forEach(MDC::remove);
        }
    }

    static String sanitizeForLogValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ');
    }
}
