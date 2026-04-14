package com.csob.ca.validation.check;

import com.csob.ca.shared.dto.AiSummaryPayloadDto;
import com.csob.ca.shared.dto.ValidationCheckResultDto;
import com.csob.ca.shared.dto.ValidationFailureDto;
import com.csob.ca.shared.enums.CheckStatus;
import com.csob.ca.shared.enums.ValidationCheckId;
import com.csob.ca.validation.ValidationContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * First validation check. Parses RawAiOutput.rawJsonText and validates it
 * against /schemas/ai-output.schema.json (JSON Schema draft 2020-12).
 *
 * Contract:
 *  - PASS  : JSON parses AND satisfies every schema constraint (additionalProperties=false,
 *            required fields, patterns, enums, minItems, length caps, etc.).
 *  - FAIL  : any schema violation, malformed JSON, absent raw text, or internal
 *            schema-loader error. NEVER throws out of check(...).
 *
 * Failure codes:
 *  - SCHEMA_INVALID : the AI output is malformed or violates the schema.
 *  - SCHEMA_ERROR   : the check could not run (schema resource missing, validator faulted).
 *
 * Field mapping note: the established ValidationFailureDto contract uses
 * `locator` for the JSON Pointer and `detail` for the message. This class
 * populates them accordingly; no DTO rename was performed.
 */
public final class SchemaValidator implements ValidationCheck {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidator.class);

    /** Single authoritative schema location. Do NOT duplicate the schema in code. */
    public static final String SCHEMA_RESOURCE = "/schemas/ai-output.schema.json";

    /** Stable machine codes surfaced on ValidationFailureDto.code. */
    static final String CODE_SCHEMA_INVALID = "SCHEMA_INVALID";
    static final String CODE_SCHEMA_ERROR   = "SCHEMA_ERROR";

    private static final int MAX_DETAIL_CHARS = 500;

    private final ObjectMapper objectMapper;
    private final JsonSchema compiledSchema;          // null iff load failed
    private final String schemaLoadError;             // non-null iff load failed

    public SchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");

        JsonSchema loaded = null;
        String err = null;
        try (InputStream in = SchemaValidator.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                err = "Schema resource not found on classpath: " + SCHEMA_RESOURCE;
            } else {
                JsonSchemaFactory factory =
                        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
                SchemaValidatorsConfig config = new SchemaValidatorsConfig();
                config.setPathType(PathType.JSON_POINTER);
                loaded = factory.getSchema(in, config);
            }
        } catch (Exception e) {
            err = "Schema load failed: " + safe(e.getMessage());
        }
        this.compiledSchema = loaded;
        this.schemaLoadError = err;

        if (loaded == null) {
            log.error("SchemaValidator initialised without a compiled schema: {}", err);
        }
    }

    @Override
    public ValidationCheckId id() {
        return ValidationCheckId.SCHEMA;
    }

    @Override
    public ValidationCheckResultDto check(ValidationContext context) {
        if (compiledSchema == null) {
            return fail(CODE_SCHEMA_ERROR, "/",
                    "Cannot validate: " + safe(schemaLoadError));
        }
        if (context == null || context.rawAiOutput() == null) {
            return fail(CODE_SCHEMA_INVALID, "/",
                    "No AI output present to validate");
        }

        String raw = context.rawAiOutput().rawJsonText();
        if (raw == null || raw.isBlank()) {
            return fail(CODE_SCHEMA_INVALID, "/",
                    "rawJsonText is null or blank");
        }

        final JsonNode parsed;
        try {
            parsed = objectMapper.readTree(raw);
        } catch (JsonProcessingException jpe) {
            return fail(CODE_SCHEMA_INVALID, "/",
                    truncate("Malformed JSON: " + safe(jpe.getOriginalMessage())));
        } catch (RuntimeException re) {
            return fail(CODE_SCHEMA_INVALID, "/",
                    truncate("JSON parse failure: " + safe(re.getMessage())));
        }

        final Set<ValidationMessage> errors;
        try {
            errors = compiledSchema.validate(parsed);
        } catch (RuntimeException re) {
            return fail(CODE_SCHEMA_ERROR, "/",
                    "Schema validator faulted: " + safe(re.getMessage()));
        }

        if (!errors.isEmpty()) {
            List<ValidationFailureDto> failures = errors.stream()
                    .map(SchemaValidator::toFailure)
                    .toList();
            return new ValidationCheckResultDto(
                    ValidationCheckId.SCHEMA, CheckStatus.FAIL, failures);
        }

        // Schema-valid. Bind into the typed DTO so downstream checks can reason
        // over sections/sentences/citations without re-parsing.
        try {
            AiSummaryPayloadDto bound =
                    objectMapper.treeToValue(parsed, AiSummaryPayloadDto.class);
            context.markSchemaPassed(bound);
        } catch (JsonProcessingException jpe) {
            // Rare: schema matches but a DTO compact-constructor rejected the values.
            // Surface as SCHEMA_INVALID rather than letting downstream checks run
            // against a half-bound state.
            return fail(CODE_SCHEMA_INVALID, "/",
                    truncate("DTO binding failed after schema PASS: "
                            + safe(jpe.getOriginalMessage())));
        } catch (RuntimeException re) {
            return fail(CODE_SCHEMA_INVALID, "/",
                    truncate("DTO binding faulted after schema PASS: "
                            + safe(re.getMessage())));
        }

        return new ValidationCheckResultDto(
                ValidationCheckId.SCHEMA, CheckStatus.PASS, List.of());
    }

    // ---- helpers ----

    private static ValidationFailureDto toFailure(ValidationMessage m) {
        String pointer = "/";
        try {
            Object loc = m.getInstanceLocation();
            if (loc != null) {
                String s = loc.toString();
                if (s != null && !s.isBlank()) {
                    pointer = s;
                }
            }
        } catch (RuntimeException ignore) {
            // keep default pointer
        }
        return new ValidationFailureDto(
                CODE_SCHEMA_INVALID,
                pointer,
                truncate(safe(m.getMessage())));
    }

    private static ValidationCheckResultDto fail(String code, String locator, String detail) {
        return new ValidationCheckResultDto(
                ValidationCheckId.SCHEMA,
                CheckStatus.FAIL,
                List.of(new ValidationFailureDto(code, locator, detail)));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_DETAIL_CHARS
                ? s
                : s.substring(0, MAX_DETAIL_CHARS) + "…";
    }
}
