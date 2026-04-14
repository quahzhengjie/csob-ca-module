package com.csob.ca.validation.support;

import com.csob.ca.shared.dto.CitationDto;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.dto.tool.DocumentMetadataToolPayload;
import com.csob.ca.shared.dto.tool.PartyToolPayload;
import com.csob.ca.shared.dto.tool.RelatedPartiesToolPayload;
import com.csob.ca.shared.dto.tool.ScreeningToolPayload;
import com.csob.ca.shared.dto.tool.ToolPayload;
import com.csob.ca.shared.enums.SourceType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Resolves a CitationDto to the string-form value at its fieldPath within the
 * pack's frozen evidence (ToolOutputs + ChecklistResult).
 *
 * Two-stage resolution:
 *   1. Locate the source entity by (sourceType, sourceId).
 *   2. Walk the dotted fieldPath through record accessors (reflection), with
 *      optional [i] index segments for list traversal.
 *
 * Returns Optional.empty() when anything along the chain is missing, null,
 * or unreachable — callers treat that as unresolved.
 *
 * NEVER throws.
 */
public final class DefaultCitationResolver implements CitationResolver {

    @Override
    public Optional<String> resolve(CitationDto citation,
                                    List<ToolOutputDto> toolOutputs,
                                    ChecklistResultDto checklistResult) {
        if (citation == null) return Optional.empty();
        SourceType type = citation.sourceType();
        String sourceId = citation.sourceId();
        String fieldPath = citation.fieldPath();
        if (type == null || sourceId == null || sourceId.isEmpty()
                || fieldPath == null || fieldPath.isBlank()) {
            return Optional.empty();
        }

        Object entity;
        try {
            entity = findEntity(type, sourceId,
                    toolOutputs == null ? List.of() : toolOutputs,
                    checklistResult);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (entity == null) return Optional.empty();

        Object value;
        try {
            value = walkFieldPath(entity, fieldPath);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (value == null) return Optional.empty();

        return Optional.of(Objects.toString(value));
    }

    // ---- step 1: entity lookup ----

    private static Object findEntity(SourceType type,
                                     String sourceId,
                                     List<ToolOutputDto> outputs,
                                     ChecklistResultDto checklistResult) {
        return switch (type) {
            case CHECKLIST_FINDING -> {
                if (checklistResult == null || checklistResult.findings() == null) yield null;
                yield checklistResult.findings().stream()
                        .filter(f -> sourceId.equals(f.ruleId()))
                        .findFirst()
                        .orElse(null);
            }
            case DOCUMENT_META -> payloads(outputs, DocumentMetadataToolPayload.class)
                    .flatMap(p -> p.documents().stream())
                    .filter(d -> sourceId.equals(d.documentId()))
                    .findFirst()
                    .orElse(null);
            case SCREENING_RESULT -> payloads(outputs, ScreeningToolPayload.class)
                    .flatMap(p -> p.results().stream())
                    .filter(r -> sourceId.equals(r.resultId()))
                    .findFirst()
                    .orElse(null);
            case RELATED_PARTY -> payloads(outputs, RelatedPartiesToolPayload.class)
                    .flatMap(p -> p.relatedParties().stream())
                    .filter(r -> sourceId.equals(r.relatedPartyId()))
                    .findFirst()
                    .orElse(null);
            case PARTY_FIELD -> payloads(outputs, PartyToolPayload.class)
                    .map(PartyToolPayload::party)
                    .filter(p -> sourceId.equals(p.partyId()))
                    .findFirst()
                    .orElse(null);
        };
    }

    // ---- step 2: fieldPath walk via record accessors ----

    /**
     * Walks a dotted path like {@code individualDetails.dateOfBirth}, with
     * optional {@code [i]} index segments like {@code identifiers[0].value},
     * using reflective invocation of record accessor methods.
     */
    private static Object walkFieldPath(Object start, String fieldPath) {
        Object current = start;
        for (String segment : fieldPath.split("\\.")) {
            if (current == null) return null;
            if (segment.isEmpty()) return null;

            String name = segment;
            Integer index = null;
            int bracket = segment.indexOf('[');
            if (bracket >= 0) {
                if (!segment.endsWith("]")) return null;
                name = segment.substring(0, bracket);
                try {
                    index = Integer.parseInt(
                            segment.substring(bracket + 1, segment.length() - 1));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            if (name.isEmpty()) return null;

            try {
                Method accessor = current.getClass().getMethod(name);
                Object next = accessor.invoke(current);
                if (index != null) {
                    if (!(next instanceof List<?> list)) return null;
                    if (index < 0 || index >= list.size()) return null;
                    next = list.get(index);
                }
                current = next;
            } catch (NoSuchMethodException
                     | IllegalAccessException
                     | InvocationTargetException e) {
                return null;
            }
        }
        return current;
    }

    private static <T extends ToolPayload> Stream<T> payloads(List<ToolOutputDto> outputs,
                                                              Class<T> type) {
        return outputs.stream()
                .map(ToolOutputDto::payload)
                .filter(type::isInstance)
                .map(type::cast);
    }
}
