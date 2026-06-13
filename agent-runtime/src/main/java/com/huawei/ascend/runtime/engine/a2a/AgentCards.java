package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.spi.AgentCardDescriptor;
import org.a2aproject.sdk.spec.AgentCard;

/**
 * Thin descriptor factory that preserves the pre-ADR-0163 call sites by
 * building an {@link AgentCardDescriptor} with the current hardcoded defaults
 * and projecting it to wire via {@link A2aAgentCardMapper}.
 *
 * <p>Kept for backward compatibility with call sites (examples, tests) that
 * construct an {@link AgentCard} directly. New code should build an
 * {@link AgentCardDescriptor} and let the boot configuration or mapper do
 * the A2A projection.
 */
public final class AgentCards {

    private AgentCards() {
    }

    /** Builds a default card for {@code name}/{@code description} with version {@code 0.1.0} and endpoint {@code /a2a}. */
    public static AgentCard create(String name, String description) {
        return create(name, description, "0.1.0", "/a2a");
    }

    /** Builds a default card with explicit version and endpoint. */
    public static AgentCard create(String name, String description, String version, String endpoint) {
        org.springframework.util.Assert.hasText(name, "name must not be blank");
        org.springframework.util.Assert.hasText(description, "description must not be blank");
        org.springframework.util.Assert.hasText(version, "version must not be blank");
        org.springframework.util.Assert.hasText(endpoint, "endpoint must not be blank");
        AgentCardDescriptor descriptor = AgentCardDescriptor.of(name, description)
                .withVersion(version)
                .withEndpoint(endpoint);
        return A2aAgentCardMapper.toAgentCard(descriptor);
    }

    /**
     * Builds a card applying YAML-configured field values with sensible production-safe defaults
     * for any blank/null field. When {@code organization} or {@code organizationUrl} is non-blank
     * the provider block is overridden; otherwise the default card provider is kept.
     *
     * @param name            card name (non-blank; supplied by the caller)
     * @param description     blank/null → {@code "agent-runtime"}
     * @param version         blank/null → {@code "0.1.0"}
     * @param endpoint        blank/null → {@code "/a2a"}
     * @param organization    blank/null → kept from the base card provider
     * @param organizationUrl blank/null → kept from the base card provider
     */
    public static AgentCard create(String name, String description, String version, String endpoint,
            String organization, String organizationUrl) {
        AgentCardDescriptor descriptor = AgentCardDescriptor.of(name,
                        blankToDefault(description, "agent-runtime"))
                .withVersion(blankToDefault(version, "0.1.0"))
                .withEndpoint(blankToDefault(endpoint, "/a2a"));
        boolean hasOrganization = organization != null && !organization.isBlank();
        boolean hasOrganizationUrl = organizationUrl != null && !organizationUrl.isBlank();
        if (hasOrganization || hasOrganizationUrl) {
            descriptor = descriptor.withProvider(
                    blankToDefault(organization, "spring-ai-ascend"),
                    blankToDefault(organizationUrl, ""));
        }
        return A2aAgentCardMapper.toAgentCard(descriptor);
    }

    /** Returns the default {@link AgentCardDescriptor} shape for the given name and description. */
    public static AgentCardDescriptor defaultDescriptor(String name, String description) {
        org.springframework.util.Assert.hasText(name, "name must not be blank");
        return AgentCardDescriptor.of(name, description != null ? description : "agent-runtime");
    }

    /** Returns the default {@link AgentCardDescriptor} shape with all YAML-overlay fields applied. */
    public static AgentCardDescriptor defaultDescriptor(String name, String description, String version,
            String endpoint, String organization, String organizationUrl) {
        AgentCardDescriptor descriptor = AgentCardDescriptor.of(name,
                        blankToDefault(description, "agent-runtime"))
                .withVersion(blankToDefault(version, "0.1.0"))
                .withEndpoint(blankToDefault(endpoint, "/a2a"));
        boolean hasOrganization = organization != null && !organization.isBlank();
        boolean hasOrganizationUrl = organizationUrl != null && !organizationUrl.isBlank();
        if (hasOrganization || hasOrganizationUrl) {
            descriptor = descriptor.withProvider(
                    blankToDefault(organization, "spring-ai-ascend"),
                    blankToDefault(organizationUrl, ""));
        }
        return descriptor;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
