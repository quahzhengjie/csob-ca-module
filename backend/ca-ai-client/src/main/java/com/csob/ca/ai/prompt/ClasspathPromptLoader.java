package com.csob.ca.ai.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Reads pinned, versioned prompt assets from the classpath:
 *   /prompts/{version}/system.md
 *   /prompts/{version}/user_template.md
 *   /prompts/{version}/output_schema.json
 *
 * Every read is UTF-8 and returns the file verbatim — no substitution.
 * Substitution is the PromptAssembler's responsibility.
 *
 * Fails loudly (IllegalStateException) on a missing or unreadable resource.
 * Prompt assets are packaged with the jar; their absence at runtime indicates
 * a build/packaging defect that must not be silently ignored.
 */
public final class ClasspathPromptLoader implements PromptLoader {

    @Override
    public String loadSystemPrompt(String promptVersion) {
        return readResource(pathFor(promptVersion, "system.md"));
    }

    @Override
    public String loadUserPromptTemplate(String promptVersion) {
        return readResource(pathFor(promptVersion, "user_template.md"));
    }

    @Override
    public String loadOutputSchema(String promptVersion) {
        return readResource(pathFor(promptVersion, "output_schema.json"));
    }

    private static String pathFor(String version, String file) {
        Objects.requireNonNull(version, "promptVersion");
        return "/prompts/" + version + "/" + file;
    }

    private static String readResource(String path) {
        try (InputStream in = ClasspathPromptLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Prompt resource not found on classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prompt resource " + path, e);
        }
    }
}
