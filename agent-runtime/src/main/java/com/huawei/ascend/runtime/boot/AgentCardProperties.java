package com.huawei.ascend.runtime.boot;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A2A agent-card metadata configurable via {@code application.yaml}.
 *
 * <p>All fields are optional. When {@code name} is left unset, the runtime
 * falls back to the handler-derived default card (as before). Set {@code name}
 * to opt into explicit YAML-driven card control; every unset field then draws
 * a sensible production-safe default.
 *
 * <p>YAML overlay semantics: each explicitly set field wins over the handler-derived
 * or default value. Null/empty fields leave the handler-derived or default value
 * intact. The overlay is applied in {@code A2aExecutionConfiguration}.
 *
 * <p>Example:
 * <pre>{@code
 * agent-runtime:
 *   access:
 *     a2a:
 *       agent-card:
 *         name: my-agent
 *         description: My custom agent served by agent-runtime.
 *         version: "1.0"
 *         documentation-url: https://docs.example.com/my-agent
 *         icon-url: https://example.com/icon.png
 *         capabilities:
 *           streaming: true
 *         default-output-modes:
 *           - text
 *           - artifact
 *         additional-endpoints:
 *           - protocol: GRPC
 *             path: /a2a/grpc
 * }</pre>
 */
@ConfigurationProperties("agent-runtime.access.a2a.agent-card")
public class AgentCardProperties {

    /** Agent name shown in {@code /.well-known/agent-card.json}. Falls back to handler {@code agentId}. */
    private String name;

    /** Human-readable description. Defaults to {@code "agent-runtime"}. */
    private String description;

    /** Agent version string. Defaults to {@code "0.1.0"}. */
    private String version;

    /** Organization name in the card's provider block. Defaults to {@code "spring-ai-ascend"}. */
    private String organization;

    /**
     * Organization URL in the card's provider block. Defaults to blank, which the
     * discovery controller rewrites to the published base (public-base-url or
     * request-derived) at serve time, so the default card never leaks a
     * hardcoded host.
     */
    private String organizationUrl;

    /** A2A endpoint path. Defaults to {@code "/a2a"}. */
    private String endpoint;

    /** Documentation URL for the agent. Null means no documentation URL on the card. */
    private String documentationUrl;

    /** Icon URL for the agent. Null means no icon URL on the card. */
    private String iconUrl;

    /**
     * Explicit capabilities override. When null, capabilities are derived from
     * the registered handler ({@code supportsStreaming()}) and push-store durability.
     */
    private CapabilitiesConfig capabilities;

    /**
     * Default input modes override. When null or empty, the handler-derived value
     * (or the fail-safe default {@code ["text"]}) is used.
     */
    private List<String> defaultInputModes;

    /**
     * Default output modes override. When null or empty, the handler-derived value
     * (or the fail-safe default {@code ["text"]}) is used.
     */
    private List<String> defaultOutputModes;

    /**
     * Skills override. When non-empty, replaces the skills reported by the handler.
     * Each entry maps to an {@code AgentSkill} on the published card.
     */
    private List<SkillConfig> skills = new ArrayList<>();

    /**
     * Additional transport endpoints beyond the primary JSONRPC endpoint.
     * Each entry becomes an extra {@code AgentInterface} on the published card.
     */
    private List<AdditionalEndpoint> additionalEndpoints = new ArrayList<>();

    // --- Getters and setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public String getOrganizationUrl() { return organizationUrl; }
    public void setOrganizationUrl(String organizationUrl) { this.organizationUrl = organizationUrl; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getDocumentationUrl() { return documentationUrl; }
    public void setDocumentationUrl(String documentationUrl) { this.documentationUrl = documentationUrl; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public CapabilitiesConfig getCapabilities() { return capabilities; }
    public void setCapabilities(CapabilitiesConfig capabilities) { this.capabilities = capabilities; }

    public List<String> getDefaultInputModes() { return defaultInputModes; }
    public void setDefaultInputModes(List<String> defaultInputModes) { this.defaultInputModes = defaultInputModes; }

    public List<String> getDefaultOutputModes() { return defaultOutputModes; }
    public void setDefaultOutputModes(List<String> defaultOutputModes) { this.defaultOutputModes = defaultOutputModes; }

    public List<SkillConfig> getSkills() { return skills; }
    public void setSkills(List<SkillConfig> skills) { this.skills = skills != null ? skills : new ArrayList<>(); }

    public List<AdditionalEndpoint> getAdditionalEndpoints() { return additionalEndpoints; }
    public void setAdditionalEndpoints(List<AdditionalEndpoint> additionalEndpoints) {
        this.additionalEndpoints = additionalEndpoints != null ? additionalEndpoints : new ArrayList<>();
    }

    /** Returns {@code true} when the user explicitly configured a card name. */
    public boolean hasExplicitName() {
        return name != null && !name.isBlank();
    }

    /** Returns {@code true} when the user explicitly configured capabilities. */
    public boolean hasExplicitCapabilities() {
        return capabilities != null;
    }

    /** Returns {@code true} when the user explicitly configured default input modes. */
    public boolean hasExplicitDefaultInputModes() {
        return defaultInputModes != null && !defaultInputModes.isEmpty();
    }

    /** Returns {@code true} when the user explicitly configured default output modes. */
    public boolean hasExplicitDefaultOutputModes() {
        return defaultOutputModes != null && !defaultOutputModes.isEmpty();
    }

    /** Returns {@code true} when the user explicitly configured skills. */
    public boolean hasExplicitSkills() {
        return skills != null && !skills.isEmpty();
    }

    /** Returns {@code true} when additional transport endpoints are configured. */
    public boolean hasAdditionalEndpoints() {
        return additionalEndpoints != null && !additionalEndpoints.isEmpty();
    }

    // --- Nested config types ---

    /**
     * Explicit capabilities configuration block. All three flags default to {@code null}
     * (unset), meaning the handler-derived honesty value is used for that flag.
     */
    public static class CapabilitiesConfig {
        private Boolean streaming;
        private Boolean pushNotifications;
        private Boolean extendedAgentCard;

        public Boolean getStreaming() { return streaming; }
        public void setStreaming(Boolean streaming) { this.streaming = streaming; }

        public Boolean getPushNotifications() { return pushNotifications; }
        public void setPushNotifications(Boolean pushNotifications) { this.pushNotifications = pushNotifications; }

        public Boolean getExtendedAgentCard() { return extendedAgentCard; }
        public void setExtendedAgentCard(Boolean extendedAgentCard) { this.extendedAgentCard = extendedAgentCard; }
    }

    /** One skill entry in the YAML skills list. */
    public static class SkillConfig {
        private String id;
        private String name;
        private String description;
        private List<String> tags = new ArrayList<>();
        private List<String> examples = new ArrayList<>();
        private List<String> inputModes = new ArrayList<>();
        private List<String> outputModes = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }

        public List<String> getExamples() { return examples; }
        public void setExamples(List<String> examples) { this.examples = examples != null ? examples : new ArrayList<>(); }

        public List<String> getInputModes() { return inputModes; }
        public void setInputModes(List<String> inputModes) { this.inputModes = inputModes != null ? inputModes : new ArrayList<>(); }

        public List<String> getOutputModes() { return outputModes; }
        public void setOutputModes(List<String> outputModes) { this.outputModes = outputModes != null ? outputModes : new ArrayList<>(); }
    }

    /**
     * One additional transport endpoint entry. {@code protocol} is the protocol binding
     * string (e.g. {@code "GRPC"}, {@code "HTTP+JSON"}). {@code path} is the URL path.
     */
    public static class AdditionalEndpoint {
        private String protocol;
        private String path;

        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }
}
