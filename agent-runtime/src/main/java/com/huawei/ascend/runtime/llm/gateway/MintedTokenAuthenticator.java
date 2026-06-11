package com.huawei.ascend.runtime.llm.gateway;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves the {@code Authorization: Bearer …} credential of a gateway request to
 * the (tenant, agent) identity it was minted for. Tokens are config-provisioned
 * opaque strings — no JWT parsing, no caller-supplied identity headers — so the
 * gateway attributes every call from server-side state only.
 */
public final class MintedTokenAuthenticator {

    private static final String BEARER_PREFIX = "Bearer ";

    private final Map<String, LlmGatewayProperties.MintedToken> tokens;

    public MintedTokenAuthenticator(LlmGatewayProperties properties) {
        this.tokens = Map.copyOf(properties.getTokens());
    }

    public Optional<LlmGatewayProperties.MintedToken> authenticate(String authorizationHeader) {
        if (authorizationHeader == null) {
            return Optional.empty();
        }
        String value = authorizationHeader.trim();
        if (!value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        String token = value.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tokens.get(token));
    }
}
