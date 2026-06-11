package com.huawei.ascend.examples.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

/**
 * The {@code sample.llm.via-gateway} flip is pure configuration: the nested
 * placeholder chain in application.yaml selects the raw or gateway block for
 * every framework's model settings, and arms the local LLM egress gateway in
 * the same motion. This test resolves the real application.yaml both ways so
 * a broken placeholder chain fails here, not at first boot with the flag on.
 *
 * <p>Assertions follow the placeholder RELATIONSHIPS, never literal default
 * URLs: the helper scripts export {@code SAA_SAMPLE_*} overrides (e.g. the
 * ollama env file points the upstream at localhost:11434), and this test must
 * stay green under any ambient values.
 */
class SampleLlmGatewayFlipTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void defaultPathKeepsDirectUpstreamAndGatewayOff() {
        contextRunner.run(context -> {
            var env = context.getEnvironment();
            assertThat(env.getProperty("sample.llm.path")).isEqualTo("raw");
            // Framework settings resolve to the raw block, whatever its values are.
            assertThat(env.getProperty("sample.openjiuwen.api-base"))
                    .isEqualTo(env.getProperty("sample.llm.raw.openjiuwen-api-base"));
            assertThat(env.getProperty("sample.openjiuwen.model-name"))
                    .isEqualTo(env.getProperty("sample.llm.raw.model-name"));
            assertThat(env.getProperty("sample.agentscope.api-base"))
                    .isEqualTo(env.getProperty("sample.llm.raw.agentscope-api-base"));
            assertThat(env.getProperty("agent-runtime.llm.gateway.enabled")).isEqualTo("false");
        });
    }

    @Test
    void flipRoutesEveryFrameworkThroughTheGatewayWithTheMintedToken() {
        contextRunner.withPropertyValues("SAA_SAMPLE_LLM_VIA_GATEWAY=true").run(context -> {
            var env = context.getEnvironment();
            assertThat(env.getProperty("sample.llm.path")).isEqualTo("gateway");
            // Both framework configs point at the gateway block's /v1 surface …
            String gatewayBase = env.getProperty("sample.llm.gateway.base-url");
            assertThat(gatewayBase).isNotBlank();
            assertThat(env.getProperty("sample.openjiuwen.api-base")).isEqualTo(gatewayBase);
            assertThat(env.getProperty("sample.agentscope.api-base")).isEqualTo(gatewayBase);
            // … with the model alias as the model name and the minted token as the credential.
            assertThat(env.getProperty("sample.openjiuwen.model-name"))
                    .isEqualTo(env.getProperty("sample.llm.gateway.model-alias"));
            assertThat(env.getProperty("sample.openjiuwen.api-key"))
                    .isEqualTo(env.getProperty("sample.llm.gateway.token"));
            // The same flag arms the local gateway, whose alias table keeps pointing
            // at the raw upstream — wherever the ambient env put it.
            assertThat(env.getProperty("agent-runtime.llm.gateway.enabled")).isEqualTo("true");
            String alias = env.getProperty("sample.llm.gateway.model-alias");
            assertThat(env.getProperty("agent-runtime.llm.gateway.aliases." + alias + ".base-url"))
                    .isEqualTo(env.getProperty("sample.llm.raw.openjiuwen-api-base"));
        });
    }
}
