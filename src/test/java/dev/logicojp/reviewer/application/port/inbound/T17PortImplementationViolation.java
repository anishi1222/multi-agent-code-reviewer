package dev.logicojp.reviewer.application.port.inbound;

import dev.logicojp.reviewer.application.policy.T17ApplicationPolicy;

/**
 * Permanent negative-control fixture for the port-to-implementation architecture rule.
 *
 * <p>This reproduces t17's one-way {@code application.port.inbound -> application.policy} mutant.
 * The fixture remains outside the production analyzer's subject set.</p>
 */
public final class T17PortImplementationViolation {

    private final T17ApplicationPolicy policy;

    public T17PortImplementationViolation(T17ApplicationPolicy policy) {
        this.policy = policy;
    }

    public T17ApplicationPolicy policy() {
        return policy;
    }
}
