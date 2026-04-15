package com.csob.ca.checklist.version;

import com.csob.ca.checklist.rules.Rule;
import com.csob.ca.checklist.rules.v1.DocumentExpiryRule;
import com.csob.ca.checklist.rules.v1.RequiredDocumentMissingRule;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Resolves checklist version identifiers to their pinned rule lists.
 *
 * Versions are additive and never mutated in place: once a pack has been
 * evaluated against "v1.0" its findings replay against the same rule set
 * forever. Adding a rule → new version key ("v1.1"). Changing a rule's
 * semantics → new version key. Rule order within a version MUST be stable
 * — the engine surfaces findings in that order.
 *
 * Current versions:
 *   v1.0 → [ DocumentExpiryRule, RequiredDocumentMissingRule ]
 *
 * The resolver injects a {@link Clock} into every rule that needs one, so
 * downstream evaluation is deterministic for tests.
 */
public final class DefaultChecklistVersionResolver implements ChecklistVersionResolver {

    public static final String VERSION_V1_0 = "v1.0";

    private final Clock clock;

    public DefaultChecklistVersionResolver() {
        this(Clock.systemUTC());
    }

    public DefaultChecklistVersionResolver(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public List<Rule> resolve(String checklistVersion) {
        Objects.requireNonNull(checklistVersion, "checklistVersion");
        if (VERSION_V1_0.equals(checklistVersion)) {
            // Rule order is part of the version contract: findings surface in
            // this order on every pack evaluated at v1.0 — do not reorder.
            return List.of(
                    new DocumentExpiryRule(clock),
                    new RequiredDocumentMissingRule()
            );
        }
        throw new IllegalArgumentException("Unknown checklistVersion: " + checklistVersion);
    }
}
