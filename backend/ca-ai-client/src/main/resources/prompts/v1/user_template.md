# User Prompt — CA Summary v1

Pack: {{packId}}
Checklist version: {{checklistVersion}}

## Party facts (whitelisted, structured)
{{partyFactsJson}}

## Checklist findings (authoritative)
{{checklistResultJson}}

Produce a JSON object conforming exactly to the output schema. Every
sentence must cite at least one sourceId that appears in the inputs
above. Do not introduce any fact not present in the inputs.

<!-- TODO governance: only variables in {{double braces}} are substituted.
     Nothing else is interpolated; no caller free text reaches this prompt. -->
