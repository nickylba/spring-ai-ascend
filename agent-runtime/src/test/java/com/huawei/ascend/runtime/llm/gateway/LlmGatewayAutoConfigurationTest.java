package com.huawei.ascend.runtime.llm.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.llm.gateway.spi.GenerationSpanSink;
import com.huawei.ascend.runtime.llm.gateway.spi.InMemorySpendLog;
import com.huawei.ascend.runtime.llm.gateway.spi.SpendLog;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class LlmGatewayAutoConfigurationTest {

    // AutoConfigurations.of (not withUserConfiguration) so @ConditionalOnMissingBean
    // sees deployment beans first, exactly as Boot orders real auto-configuration.
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LlmGatewayAutoConfiguration.class));

    /** The gateway is opt-in: without the property the context must contain none of it. */
    @Test
    void backsOffEntirelyWhenNotEnabled() {
        runner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(ChatCompletionsController.class);
            assertThat(ctx).doesNotHaveBean(ModelAliasRegistry.class);
            assertThat(ctx).doesNotHaveBean(UpstreamModelClient.class);
            assertThat(ctx).doesNotHaveBean(GenerationSpanSink.class);
            assertThat(ctx).doesNotHaveBean(SpendLog.class);
            assertThat(ctx).doesNotHaveBean(LlmGatewayProperties.class);
        });
    }

    @Test
    void backsOffWhenExplicitlyDisabled() {
        runner.withPropertyValues("agent-runtime.llm.gateway.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ChatCompletionsController.class));
    }

    @Test
    void enabledGatewayWiresEndpointForwarderAndDefaultSinks() {
        runner.withPropertyValues("agent-runtime.llm.gateway.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(ChatCompletionsController.class);
                    assertThat(ctx).hasSingleBean(ModelAliasRegistry.class);
                    assertThat(ctx).hasSingleBean(MintedTokenAuthenticator.class);
                    assertThat(ctx).hasSingleBean(LlmGatewayMetrics.class);
                    assertThat(ctx.getBean(UpstreamModelClient.class))
                            .isInstanceOf(RestClientUpstreamModelClient.class);
                    assertThat(ctx.getBean(GenerationSpanSink.class))
                            .isInstanceOf(com.huawei.ascend.runtime.llm.gateway.spi.NoopGenerationSpanSink.class);
                    assertThat(ctx.getBean(SpendLog.class)).isInstanceOf(InMemorySpendLog.class);
                    assertThat(ctx).hasSingleBean(LlmSpanEmitterListener.class);
                    assertThat(ctx).hasSingleBean(SpendRecordListener.class);
                });
    }

    /** Deployment-supplied port and sink beans must replace the defaults, not coexist. */
    @Test
    void customUpstreamClientAndSinkSuppressDefaults() {
        runner.withUserConfiguration(CustomSeamsConfiguration.class)
                .withPropertyValues("agent-runtime.llm.gateway.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).getBeans(UpstreamModelClient.class).hasSize(1);
                    assertThat(ctx.getBean(UpstreamModelClient.class))
                            .isNotInstanceOf(RestClientUpstreamModelClient.class);
                    assertThat(ctx).getBeans(GenerationSpanSink.class).hasSize(1);
                });
    }

    @Test
    void bindsAliasAndTokenMaps() {
        runner.withPropertyValues(
                "agent-runtime.llm.gateway.enabled=true",
                "agent-runtime.llm.gateway.aliases.finance-chat.base-url=http://upstream.test/v1",
                "agent-runtime.llm.gateway.aliases.finance-chat.api-key=sk-real",
                "agent-runtime.llm.gateway.aliases.finance-chat.upstream-model=gpt-4o-mini",
                "agent-runtime.llm.gateway.aliases.finance-chat.pricing.input-per-million-tokens-usd=0.15",
                "agent-runtime.llm.gateway.tokens.tok-1.tenant-id=tenant-a",
                "agent-runtime.llm.gateway.tokens.tok-1.agent-id=agent-1")
                .run(ctx -> {
                    LlmGatewayProperties properties = ctx.getBean(LlmGatewayProperties.class);
                    var upstream = properties.getAliases().get("finance-chat");
                    assertThat(upstream.getBaseUrl()).isEqualTo("http://upstream.test/v1");
                    assertThat(upstream.getUpstreamModel()).isEqualTo("gpt-4o-mini");
                    assertThat(upstream.getPricing().getInputPerMillionTokensUsd()).isEqualTo(0.15);
                    assertThat(properties.getTokens().get("tok-1").getTenantId()).isEqualTo("tenant-a");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomSeamsConfiguration {

        @Bean
        UpstreamModelClient scriptedUpstream() {
            return new UpstreamModelClient() {
                @Override
                public UpstreamResponse exchange(UpstreamRequest request) {
                    return new UpstreamResponse(200, "application/json", new byte[0]);
                }

                @Override
                public UpstreamStreamResponse openStream(UpstreamRequest request) {
                    return new UpstreamStreamResponse(200, "text/event-stream",
                            java.io.InputStream.nullInputStream());
                }
            };
        }

        @Bean
        GenerationSpanSink recordingSink() {
            return span -> { };
        }
    }
}
