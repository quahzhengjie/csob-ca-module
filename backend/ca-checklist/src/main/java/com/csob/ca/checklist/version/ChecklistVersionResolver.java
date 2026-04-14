package com.csob.ca.checklist.version;

import com.csob.ca.checklist.rules.Rule;

import java.util.List;

/**
 * Resolves a checklistVersion string (e.g. "v1.0") to the frozen set of
 * rules defined for that version. Versions are additive; rules are never
 * modified in place.
 */
public interface ChecklistVersionResolver {
    List<Rule> resolve(String checklistVersion);
}
