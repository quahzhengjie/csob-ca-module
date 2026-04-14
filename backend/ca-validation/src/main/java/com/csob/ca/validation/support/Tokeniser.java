package com.csob.ca.validation.support;

import java.util.List;

/**
 * v1 tokeniser used by CoverageChecker. Detects:
 *   - ISO dates      : \b\d{4}-\d{2}-\d{2}\b
 *   - numeric tokens : \b\d+(\.\d+)?\b (not overlapping detected dates)
 *   - status tokens  : case-insensitive match against a fixed STATUS_WORDS list
 *
 * ENTITY / IDENTIFIER coverage is best-effort in v1 — declared mentions of
 * those kinds are grounded by FactGroundingChecker, but undeclared
 * occurrences are not flagged.
 */
public interface Tokeniser {

    record DetectedToken(String value, String kind, int startIndex, int endIndex) {}

    List<DetectedToken> tokenise(String text);
}
