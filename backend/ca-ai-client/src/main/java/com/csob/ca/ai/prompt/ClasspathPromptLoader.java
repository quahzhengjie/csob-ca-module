package com.csob.ca.ai.prompt;

public final class ClasspathPromptLoader implements PromptLoader {

    @Override
    public String loadSystemPrompt(String promptVersion) {
        throw new UnsupportedOperationException(
                "Skeleton — read /prompts/" + promptVersion + "/system.md from classpath");
    }

    @Override
    public String loadUserPromptTemplate(String promptVersion) {
        throw new UnsupportedOperationException(
                "Skeleton — read /prompts/" + promptVersion + "/user_template.md from classpath");
    }

    @Override
    public String loadOutputSchema(String promptVersion) {
        throw new UnsupportedOperationException(
                "Skeleton — read /prompts/" + promptVersion + "/output_schema.json from classpath");
    }
}
