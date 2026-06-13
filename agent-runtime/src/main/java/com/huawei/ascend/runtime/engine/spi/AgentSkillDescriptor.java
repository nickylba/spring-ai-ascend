package com.huawei.ascend.runtime.engine.spi;

import java.util.List;

/**
 * Protocol-neutral descriptor for one agent skill.
 *
 * <p>Collections default to empty lists in the compact constructor;
 * this type carries zero {@code org.a2aproject} imports.
 */
public record AgentSkillDescriptor(
        String id,
        String name,
        String description,
        List<String> tags,
        List<String> examples,
        List<String> inputModes,
        List<String> outputModes) {

    public AgentSkillDescriptor {
        tags = tags != null ? List.copyOf(tags) : List.of();
        examples = examples != null ? List.copyOf(examples) : List.of();
        inputModes = inputModes != null ? List.copyOf(inputModes) : List.of();
        outputModes = outputModes != null ? List.copyOf(outputModes) : List.of();
    }

    /** Convenience factory for a skill with only id, name, and description. */
    public static AgentSkillDescriptor of(String id, String name, String description) {
        return new AgentSkillDescriptor(id, name, description, null, null, null, null);
    }
}
