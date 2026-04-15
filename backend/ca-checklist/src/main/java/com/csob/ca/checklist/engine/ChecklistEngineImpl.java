package com.csob.ca.checklist.engine;

import com.csob.ca.checklist.rules.Rule;
import com.csob.ca.checklist.version.ChecklistVersionResolver;
import com.csob.ca.shared.dto.ChecklistCompletionDto;
import com.csob.ca.shared.dto.ChecklistFindingDto;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.enums.RuleStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic checklist engine: resolves the pinned rule set for a given
 * checklist version and evaluates each rule exactly once against the frozen
 * ToolOutput snapshot. Emits a {@link ChecklistResultDto} — the
 * system-of-record finding set the validation pipeline is entitled to trust.
 *
 * Contract (upheld):
 *   - Pure. No I/O beyond reading the injected clock. No model calls.
 *   - Deterministic ordering:
 *       * findings preserve rule order from the resolver
 *       * evidence within each finding preserves the rule's traversal order
 *   - Never throws from {@code evaluate(...)} other than for programmer
 *     errors (null required inputs, unknown checklistVersion). Rule
 *     exceptions would indicate a bug in a rule, not in input data.
 *
 * toolOutputsHashRoot is a SHA-256 over the concatenated payloadHashes of
 * the frozen ToolOutputs (in supplied order). Empty input set hashes to
 * the SHA-256 of the empty string so the field is always populated and
 * deterministic.
 */
public final class ChecklistEngineImpl implements ChecklistEngine {

    private final ChecklistVersionResolver versionResolver;
    private final Clock clock;

    public ChecklistEngineImpl(ChecklistVersionResolver versionResolver) {
        this(versionResolver, Clock.systemUTC());
    }

    public ChecklistEngineImpl(ChecklistVersionResolver versionResolver, Clock clock) {
        this.versionResolver = Objects.requireNonNull(versionResolver, "versionResolver");
        this.clock           = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ChecklistResultDto evaluate(String packId,
                                       String checklistVersion,
                                       List<ToolOutputDto> toolOutputs) {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(checklistVersion, "checklistVersion");
        List<ToolOutputDto> outputs = (toolOutputs == null) ? List.of() : toolOutputs;

        List<Rule> rules = versionResolver.resolve(checklistVersion);
        List<ChecklistFindingDto> findings = new ArrayList<>(rules.size());
        for (Rule rule : rules) {
            findings.add(rule.evaluate(outputs));
        }

        ChecklistCompletionDto completion = tally(findings);
        String hashRoot = hashToolOutputs(outputs);

        return new ChecklistResultDto(
                packId,
                checklistVersion,
                clock.instant(),
                hashRoot,
                List.copyOf(findings),
                completion);
    }

    // ---- helpers ----

    private static ChecklistCompletionDto tally(List<ChecklistFindingDto> findings) {
        int passed = 0, failed = 0, missing = 0, notApplicable = 0;
        for (ChecklistFindingDto f : findings) {
            switch (f.status()) {
                case PASS:           passed++;        break;
                case FAIL:           failed++;        break;
                case MISSING:        missing++;       break;
                case NOT_APPLICABLE: notApplicable++; break;
                default: throw new IllegalStateException("Unhandled RuleStatus: " + f.status());
            }
        }
        return new ChecklistCompletionDto(findings.size(), passed, failed, missing, notApplicable);
    }

    private static String hashToolOutputs(List<ToolOutputDto> outputs) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JVM.
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
        for (ToolOutputDto out : outputs) {
            if (out == null || out.payloadHash() == null) continue;
            md.update(out.payloadHash().getBytes(StandardCharsets.UTF_8));
            md.update((byte) '|');
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }
}
