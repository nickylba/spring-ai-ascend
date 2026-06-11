package com.huawei.ascend.runtime.llm.gateway;

import com.huawei.ascend.runtime.llm.gateway.spi.LlmTokenUsage;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a caller-facing model alias to its configured upstream route. The alias
 * indirection is the gateway's whole adoption mechanism: agents name a platform
 * alias, and only this registry knows the real base URL, credential and pricing.
 */
public final class ModelAliasRegistry {

    private final Map<String, LlmGatewayProperties.Upstream> aliases;

    public ModelAliasRegistry(LlmGatewayProperties properties) {
        this.aliases = Map.copyOf(properties.getAliases());
    }

    public Optional<Route> resolve(String alias) {
        return Optional.ofNullable(aliases.get(alias)).map(upstream -> new Route(alias, upstream));
    }

    /**
     * Priced cost of a call against an alias, or null when the alias is unknown,
     * unpriced, or the usage is estimated — an invented cost would silently
     * corrupt the spend ledger, so absence is reported as absence.
     */
    public Double costUsd(String alias, LlmTokenUsage usage) {
        LlmGatewayProperties.Upstream upstream = aliases.get(alias);
        if (upstream == null || upstream.getPricing() == null || usage.estimated()) {
            return null;
        }
        return upstream.getPricing().costUsd(usage.inputTokens(), usage.outputTokens());
    }

    /** A resolved alias: the upstream route plus the URL/header values derived from it. */
    public record Route(String alias, LlmGatewayProperties.Upstream upstream) {

        public String chatCompletionsUrl() {
            String base = upstream.getBaseUrl();
            return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                    + "/chat/completions";
        }

        public String apiKey() {
            return upstream.getApiKey();
        }

        public String provider() {
            return upstream.getProvider();
        }

        /** The model name to forward, or null when the alias passes through unchanged. */
        public String upstreamModel() {
            return upstream.getUpstreamModel();
        }
    }
}
