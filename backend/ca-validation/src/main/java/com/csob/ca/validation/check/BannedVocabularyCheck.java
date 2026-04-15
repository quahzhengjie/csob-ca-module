package com.csob.ca.validation.check;

import com.csob.ca.shared.dto.AiSummaryPayloadDto;
import com.csob.ca.shared.dto.SectionDto;
import com.csob.ca.shared.dto.SentenceDto;
import com.csob.ca.shared.dto.ValidationCheckResultDto;
import com.csob.ca.shared.dto.ValidationFailureDto;
import com.csob.ca.shared.enums.CheckStatus;
import com.csob.ca.shared.enums.ValidationCheckId;
import com.csob.ca.validation.ValidationContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Detects speculative, interpretive, or decision-like language in sentence
 * text. Deterministic — no model calls, no re-parsing of rawJsonText.
 *
 * Matching rules:
 *   - case-insensitive
 *   - single-word terms match with word boundaries: \bterm\b
 *   - multi-word terms match as phrases with flexible whitespace between
 *     words: \bword1\s+word2\b
 *   - patterns are precompiled at construction for performance
 *
 * One FAIL entry is emitted per (sentence, banned-term) pair — duplicate
 * occurrences of the same term within one sentence are deduplicated.
 *
 * Known v1 limitation: case-insensitive matching can produce false positives
 * for terms that are also proper nouns (e.g. "May" as a month vs "may" as
 * speculation). Documented for the compliance reviewer; refinement deferred.
 */
public final class BannedVocabularyCheck implements ValidationCheck {

    private static final Logger log = LoggerFactory.getLogger(BannedVocabularyCheck.class);

    public static final String POLICY_RESOURCE = "/policy/banned-vocabulary.txt";

    static final String CODE_SCHEMA_NOT_PASSED = "SCHEMA_NOT_PASSED";
    static final String CODE_BANNED_LANGUAGE   = "BANNED_LANGUAGE";

    private static final String DETAIL_PREFIX = "Banned term detected: ";

    /** Canonical (lowercased, trimmed) banned terms, 1:1 with {@link #patterns}. */
    private final List<String> bannedTerms;
    private final List<Pattern> patterns;

    public BannedVocabularyCheck(List<String> bannedTerms) {
        List<String> canonicals = new ArrayList<>();
        List<Pattern> compiled = new ArrayList<>();
        if (bannedTerms != null) {
            for (String term : bannedTerms) {
                if (term == null) continue;
                String canonical = term.trim().toLowerCase(Locale.ROOT);
                if (canonical.isEmpty()) continue;
                canonicals.add(canonical);
                compiled.add(compileTermPattern(canonical));
            }
        }
        this.bannedTerms = List.copyOf(canonicals);
        this.patterns = List.copyOf(compiled);
        if (canonicals.isEmpty()) {
            log.warn("BannedVocabularyCheck constructed with an empty term list — check will always PASS");
        }
    }

    @Override
    public ValidationCheckId id() {
        return ValidationCheckId.BANNED_VOCABULARY;
    }

    @Override
    public ValidationCheckResultDto check(ValidationContext context) {
        AiSummaryPayloadDto payload = (context == null) ? null : context.parsedPayload();
        if (payload == null) {
            return singleFail(CODE_SCHEMA_NOT_PASSED, "/",
                    "parsedPayload is null — SCHEMA must pass before BANNED_VOCABULARY runs");
        }
        if (patterns.isEmpty()) {
            // No terms to enforce → vacuously PASS. Already logged at construction.
            return new ValidationCheckResultDto(
                    ValidationCheckId.BANNED_VOCABULARY, CheckStatus.PASS, List.of());
        }

        List<SectionDto> sections = payload.sections();
        if (sections == null) {
            return new ValidationCheckResultDto(
                    ValidationCheckId.BANNED_VOCABULARY, CheckStatus.PASS, List.of());
        }

        List<ValidationFailureDto> failures = new ArrayList<>();

        for (int si = 0; si < sections.size(); si++) {
            SectionDto section = sections.get(si);
            if (section == null || section.sentences() == null) continue;
            List<SentenceDto> sentences = section.sentences();
            for (int ti = 0; ti < sentences.size(); ti++) {
                SentenceDto sentence = sentences.get(ti);
                if (sentence == null) continue;
                String text = sentence.text();
                if (text == null || text.isEmpty()) continue;

                String locator = "/sections/" + si + "/sentences/" + ti;
                for (int pi = 0; pi < patterns.size(); pi++) {
                    if (patterns.get(pi).matcher(text).find()) {
                        failures.add(new ValidationFailureDto(
                                CODE_BANNED_LANGUAGE,
                                locator,
                                DETAIL_PREFIX + bannedTerms.get(pi)));
                    }
                }
            }
        }

        if (failures.isEmpty()) {
            return new ValidationCheckResultDto(
                    ValidationCheckId.BANNED_VOCABULARY, CheckStatus.PASS, List.of());
        }
        return new ValidationCheckResultDto(
                ValidationCheckId.BANNED_VOCABULARY, CheckStatus.FAIL, failures);
    }

    // ---- pattern compilation ----

    /**
     * Compiles a canonical banned term into a case-insensitive regex that
     * matches whole words or whole phrases with flexible internal whitespace:
     *   "may"       → \bmay\b
     *   "high risk" → \bhigh\s+risk\b
     * Each word is Pattern.quote'd so regex metacharacters in a term are literal.
     */
    static Pattern compileTermPattern(String canonicalTerm) {
        String[] words = canonicalTerm.split("\\s+");
        StringBuilder sb = new StringBuilder("\\b");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append("\\s+");
            sb.append(Pattern.quote(words[i]));
        }
        sb.append("\\b");
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    // ---- resource loading ----

    /**
     * Loads the default banned terms list from {@link #POLICY_RESOURCE} on
     * the classpath. Lines starting with '#' and blank lines are ignored.
     * Returns an empty list if the resource is missing or unreadable (this
     * is logged loudly — production deployments MUST ensure the resource
     * is present on the classpath).
     */
    public static List<String> loadDefaultTerms() {
        try (InputStream in = BannedVocabularyCheck.class.getResourceAsStream(POLICY_RESOURCE)) {
            if (in == null) {
                log.error("Banned vocabulary resource missing from classpath: {}", POLICY_RESOURCE);
                return List.of();
            }
            List<String> terms = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    terms.add(trimmed);
                }
            }
            return terms;
        } catch (IOException e) {
            log.error("Failed to load banned vocabulary from {}", POLICY_RESOURCE, e);
            return List.of();
        }
    }

    private static ValidationCheckResultDto singleFail(String code, String locator, String detail) {
        return new ValidationCheckResultDto(
                ValidationCheckId.BANNED_VOCABULARY,
                CheckStatus.FAIL,
                List.of(new ValidationFailureDto(code, locator, detail)));
    }
}
