package dev.logicojp.reviewer.application.probe;

import dev.logicojp.reviewer.ReviewPortFactory;

/**
 * Permanent negative-control fixture for the layer-to-root architecture rule.
 *
 * <p>This class deliberately reproduces the one-way mutant from t17. It lives in the test tree,
 * while the architecture analyzer inspects {@code target/classes} only, so the violation is
 * detectable without making the production graph invalid.</p>
 */
public final class T17RootReferenceViolation {

    private final ReviewPortFactory rootFactory;

    public T17RootReferenceViolation(ReviewPortFactory rootFactory) {
        this.rootFactory = rootFactory;
    }

    public ReviewPortFactory rootFactory() {
        return rootFactory;
    }
}
