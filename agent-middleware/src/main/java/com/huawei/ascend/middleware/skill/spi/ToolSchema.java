package com.huawei.ascend.middleware.skill.spi;

import java.util.Map;
import java.util.Objects;

/**
 * Ensures Gene-Tool Dispatch Consistency. 
 * The exact schema provided here must be used to render the LLM's prompt.
 */
public record ToolSchema(
        String name,
        String description,
        Map<String, Object> jsonSchema
) {
    public ToolSchema {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(jsonSchema, "jsonSchema must not be null");
    }
}
