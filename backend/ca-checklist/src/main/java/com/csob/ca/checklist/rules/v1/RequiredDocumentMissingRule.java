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

import java.util.ArrayList;
import java.util.List;

/**
 * Second deterministic checklist rule. Detects the absence of a document
 * type the bank requires on file. Pure function over the frozen ToolOutput
 * snapshot — no I/O, no AI, no randomness.
 *
 * v1 scope (narrow and deterministic):
 *   Required type = {@value #REQUIRED_DOC_TYPE}. If any document in the
 *   {@link DocumentMetadataToolPayload} outputs has that type (case
 *   insensitive), the rule returns PASS. Otherwise it returns MISSING.
 *
 * v1 limitation (by design):
 *   The {@link Rule} interface does not expose party type to the rule, so
 *   the required set cannot vary by INDIVIDUAL vs ORGANISATION today.
 *   When party-type-aware requirements are needed (e.g. PASSPORT for
 *   INDIVIDUAL, CERT_OF_INCORPORATION for ORGANISATION), the Rule
 *   interface will be extended to take a minimal party-type signal —
 *   that is a deliberate future contract change, not part of this rule.
 *
 * Status semantics:
 *   PASS    — at least one document with type = REQUIRED_DOC_TYPE present.
 *             Evidence cites the first such document.
 *   MISSING — no document of the required type present (including the
 *             zero-documents case). Evidence carries a synthetic locator
 *             {@code DOCUMENT_META/required:<type>.type = <absent>} so
 *             the DTO's "evidence required for MISSING" invariant holds
 *             and the reviewer can see exactly which type is expected.
 *
 * Not used here: FAIL, NOT_APPLICABLE. MISSING is always the right
 * rejection status for "required data not present".
 */
public final class RequiredDocumentMissingRule implements Rule {

    public static final String RULE_ID              = "R-DOC-REQUIRED-MISSING";
    public static final String RULE_DESCRIPTION     =
            "A document of the required type (" + "PASSPORT" + ") must be on file.";
    public static final String REGULATORY_REFERENCE = "MAS 626 §6.28";
    public static final RuleSeverity SEVERITY       = RuleSeverity.HIGH;

    /** Required document type (case-insensitive match against {@link DocumentMetaDto#type()}). */
    public static final String REQUIRED_DOC_TYPE = "PASSPORT";

    private static final String FIELD_TYPE   = "type";
    private static final String ABSENT_VALUE = "<absent>";

    public RequiredDocumentMissingRule() { /* stateless */ }

    @Override public String       ruleId()              { return RULE_ID; }
    @Override public String       description()         { return RULE_DESCRIPTION; }
    @Override public String       regulatoryReference() { return REGULATORY_REFERENCE; }
    @Override public RuleSeverity severity()            { return SEVERITY; }

    @Override
    public ChecklistFindingDto evaluate(List<ToolOutputDto> toolOutputs) {
        List<DocumentMetaDto> documents = collectDocuments(toolOutputs);

        DocumentMetaDto found = null;
        for (DocumentMetaDto doc : documents) {
            if (doc == null || doc.type() == null) continue;
            if (REQUIRED_DOC_TYPE.equalsIgnoreCase(doc.type())) {
                found = doc;
                break;
            }
        }

        if (found != null) {
            // PASS — cite the matching document's documentId.
            return finding(
                    RuleStatus.PASS,
                    List.of(new EvidenceDto(
                            SourceType.DOCUMENT_META,
                            found.documentId(),
                            FIELD_TYPE,
                            found.type())));
        }

        // MISSING — synthetic evidence naming the required type.
        // sourceId = "required:<TYPE>" is intentionally not a real documentId;
        // it's a locator for the human reviewer, not an AI-validated citation.
        return finding(
                RuleStatus.MISSING,
                List.of(new EvidenceDto(
                        SourceType.DOCUMENT_META,
                        "required:" + REQUIRED_DOC_TYPE,
                        FIELD_TYPE,
                        ABSENT_VALUE)));
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
