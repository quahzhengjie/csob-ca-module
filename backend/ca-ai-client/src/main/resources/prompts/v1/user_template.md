# Customer Assessment pack - input

Pack: {{packId}}
Checklist version: {{checklistVersion}}

## Allowed section headings (closed enum)

IDENTITY, DOCUMENTS, SCREENING, OWNERSHIP_AND_CONTROL, DATA_QUALITY

## Output schema (conform exactly)

```json
{{outputSchema}}
```

## Party facts (whitelisted structured snapshot)

```json
{{partyFactsJson}}
```

## Checklist findings (authoritative, system of record)

```json
{{checklistResultJson}}
```

## Tool outputs (frozen evidence snapshots)

```json
{{toolOutputsJson}}
```

Produce a single JSON object conforming exactly to the output schema above.
Do not include any prose, explanation, or commentary outside the JSON object.

<!-- Variable substitution is limited to {{double-braced}} placeholders.
     No other interpolation occurs. No caller free text reaches this prompt. -->
