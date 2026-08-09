package dev.logicojp.reviewer.presentation;

import io.micronaut.context.annotation.Value;

/// Permanent Rule 5c negative-control fixture.
///
/// The architecture analyzer reads production classes from `target/classes`; this test-tree class
/// remains outside that subject set while proving the forbidden annotation is detectable.
final class T32ConfigurationBindingViolation {

    private final String value;

    T32ConfigurationBindingViolation(@Value("${t32.rule5c.control}") String value) {
        this.value = value;
    }

    String value() {
        return value;
    }
}
