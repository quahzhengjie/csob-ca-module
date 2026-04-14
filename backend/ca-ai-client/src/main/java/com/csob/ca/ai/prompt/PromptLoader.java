package com.csob.ca.ai.prompt;

/**
 * Loads pinned, versioned prompt assets (system template, user template,
 * output schema) from the classpath. Returns raw strings — no substitution.
 *
 * Prompt changes are governance-relevant and require compliance review per
 * the CODEOWNERS policy in the repo README.
 */
public interface PromptLoader {
    String loadSystemPrompt(String promptVersion);

    String loadUserPromptTemplate(String promptVersion);

    String loadOutputSchema(String promptVersion);
}
