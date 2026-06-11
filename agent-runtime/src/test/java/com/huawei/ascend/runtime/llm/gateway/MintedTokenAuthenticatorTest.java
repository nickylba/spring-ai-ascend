package com.huawei.ascend.runtime.llm.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MintedTokenAuthenticatorTest {

    private MintedTokenAuthenticator authenticator() {
        LlmGatewayProperties properties = new LlmGatewayProperties();
        LlmGatewayProperties.MintedToken token = new LlmGatewayProperties.MintedToken();
        token.setTenantId("tenant-a");
        token.setAgentId("agent-1");
        properties.getTokens().put("minted-secret", token);
        return new MintedTokenAuthenticator(properties);
    }

    @Test
    void resolvesKnownBearerTokenToItsIdentity() {
        var principal = authenticator().authenticate("Bearer minted-secret").orElseThrow();

        assertThat(principal.getTenantId()).isEqualTo("tenant-a");
        assertThat(principal.getAgentId()).isEqualTo("agent-1");
    }

    @Test
    void bearerSchemeIsCaseInsensitiveAndWhitespaceTolerant() {
        assertThat(authenticator().authenticate("  bearer minted-secret  ")).isPresent();
    }

    @Test
    void rejectsMissingHeaderUnknownTokenAndForeignSchemes() {
        MintedTokenAuthenticator authenticator = authenticator();

        assertThat(authenticator.authenticate(null)).isEmpty();
        assertThat(authenticator.authenticate("Bearer forged-token")).isEmpty();
        assertThat(authenticator.authenticate("Basic bWludGVkOnNlY3JldA==")).isEmpty();
        assertThat(authenticator.authenticate("Bearer ")).isEmpty();
        assertThat(authenticator.authenticate("minted-secret")).isEmpty();
    }
}
