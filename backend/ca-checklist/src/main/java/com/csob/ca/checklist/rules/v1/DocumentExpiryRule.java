package com.csob.ca.checklist.rules.v1;

import com.csob.ca.checklist.rules.Rule;
import com.csob.ca.shared.dto.ChecklistFindingDto;
import com.csob.ca.shared.dto.DocumentMetaDto;
import com.csob.ca.shared.dto.EvidenceDto;
import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.dto.tool.DocumentMetadataToolPayload;
import com.csob.ca.shared.enums.RuleSeverity;
import com.csob.ca.shared.enums.RuleStatus;
import com.csob.ca.shared.enums.SourceType;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * First real deterministic checklist rule.
 *
 * Detects expired documents among the {@link DocumentMetadataToolPayload}
 * outputs of a pack. Pure function over the frozen ToolOutput snapshot —
 * no I/O, no AI, no randomness, no mutation.
 *
 * Status derivation (in priority order):
 *   FAIL            — at least one document's expiresOn is strictly before
 *                     today (per the injected Clock). Evidence lists every
 *                     expired document's id + observed date, in iteration order.
 *   MISSING         — no documents are expired, but at least one document
 *                     lacks an expiresOn value. Evidence lists every such doc.
 *                     (Interpretation: expiry is a required field for docs
 *                     that reach this adapter; its absence is a data-quality
 *                     gap the reviewer must address.)
 *   PASS            — every document has a non-null expiresOn and none is
 *                     before today. Evidence cites the first examined doc.
 *   NOT_APPLICABLE  — the pack has no DocumentMetadata tool outputs, or
 *                     those outputs contain zero documents. The rule has no
 *                     subject.
 *
 * Determinism:
 *   - "today" is sourced from the injected Clock; tests pin it.
 *   - Document order follows ToolOutput order, then documents-list order.
 *   - Evidence entries preserve that traversal order.
 *
 * Single-finding-per-rule: the Rule interface returns exactly one
 * ChecklistFindingDto; that is the contract.
 */
public final class DocumentExpiryRule implements Rule {

    public static final String RULE_ID              = "R-DOC-EXPIRED";
    public static final String RULE_DESCRIPTION     = "Customer documents must not be expired.";
    public static final String REGULATORY_REFERENCE = "MAS 626 §6.31";
    public static final RuleSeverity SEVERITY       = RuleSeverity.HIGH;

    private static final String FIELD_EXPIRES_ON = "expiresOn";
    private static final String ABSENT_VALUE     = "<absent>";

    private final Clock clock;

    public DocumentExpiryRule() {
        this(Clock.systemUTC());
    }

    public DocumentExpiryRule(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override public String       ruleId()              { return RULE_ID; }
    @Override public String       description()         { return RULE_DESCRIPTION; }
    @Override public String       regulatoryReference() { return REGULATORY_REFERENCE; }
    @Override public RuleSeverity severity()            { return SEVERITY; }

    @Override
    public ChecklistFindingDto evaluate(List<ToolOutputDto> toolOutputs) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));

        List<DocumentMetaDto> documents = collectDocuments(toolOutputs);
        if (documents.isEmpty()) {
            return finding(RuleStatus.NOT_APPLICABLE, List.of());
        }

        List<EvidenceDto> expired       = new ArrayList<>();
        List<EvidenceDto> missingExpiry = new ArrayList<>();
        DocumentMetaDto firstValid      = null;

        for (DocumentMetaDto doc : documents) {
            if (doc == null || doc.documentId() == null) continue;

            LocalDate expiresOn = doc.expiresOn();
            if (expiresOn == null) {
                missingExpiry.add(evidenceFor(doc.documentId(), ABSENT_VALUE));
            } else if (expiresOn.isBefore(today)) {
                expired.add(evidenceFor(doc.documentId(), expiresOn.toString()));
            } else if (firstValid == null) {
                firstValid = doc;
            }
        }

        // FAIL takes precedence over MISSING (expired is a harder violation).
        if (!expired.isEmpty()) {
            return finding(RuleStatus.FAIL, expired);
        }
        if (!missingExpiry.isEmpty()) {
            return finding(RuleStatus.MISSING, missingExpiry);
        }
        // All docs have valid future expiries. Single minimal evidence entry
        // citing the first checked doc satisfies the DTO's "PASS requires
        // evidence" invariant without noise.
        return finding(
                RuleStatus.PASS,
                List.of(evidenceFor(firstValid.documentId(), firstValid.expiresOn().toString())));
    }

    // ---- helpers ----

    private static List<DocumentMetaDto> collectDocuments(List<ToolOutputDto> toolOutputs) {
        if (toolOutputs == null || toolOutputs.isEmpty()) return List.of();
        List<DocumentMetaDto> docs = new ArrayList<>();
        for (ToolOutputDto out : toolOutputs) {
            if (out == null) continue;
            if (out.payload() instanceof DocumentMetadataToolPayload payload) {
                docs.addAll(payload.documents());
            }
        }
        return docs;
    }

    private static EvidenceDto evidenceFor(String documentId, String observedValue) {
        return new EvidenceDto(SourceType.DOCUMENT_META, documentId, FIELD_EXPIRES_ON, observedValue);
    }

    private ChecklistFindingDto finding(RuleStatus status, List<EvidenceDto> evidence) {
        return new ChecklistFindingDto(
                RULE_ID,
                RULE_DESCRIPTION,
                REGULATORY_REFERENCE,
                SEVERITY,
                status,
                List.copyOf(evidence));
    }
}
