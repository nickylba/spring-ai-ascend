package com.huawei.ascend.runtime.llm.gateway;

import com.huawei.ascend.runtime.llm.gateway.spi.GenerationSpanSink;
import com.huawei.ascend.runtime.llm.gateway.spi.InMemorySpendLog;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallListener;
import com.huawei.ascend.runtime.llm.gateway.spi.NoopGenerationSpanSink;
import com.huawei.ascend.runtime.llm.gateway.spi.SpendLog;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the LLM egress gateway. Guarded by an explicit opt-in property so the
 * whole surface — endpoint, forwarder, listeners — stays off every classpath
 * consumer's context unless a deployment turns it on.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "agent-runtime.llm.gateway", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LlmGatewayProperties.class)
public class LlmGatewayAutoConfiguration {

    @Bean
    public ModelAliasRegistry llmModelAliasRegistry(LlmGatewayProperties properties) {
        return new ModelAliasRegistry(properties);
    }

    @Bean
    public MintedTokenAuthenticator llmMintedTokenAuthenticator(LlmGatewayProperties properties) {
        return new MintedTokenAuthenticator(properties);
    }

    @Bean
    @ConditionalOnMissingBean(UpstreamModelClient.class)
    public RestClientUpstreamModelClient llmUpstreamModelClient() {
        return new RestClientUpstreamModelClient();
    }

    @Bean
    public LlmGatewayMetrics llmGatewayMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
        return new LlmGatewayMetrics(meterRegistry.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(GenerationSpanSink.class)
    public NoopGenerationSpanSink llmGenerationSpanSink() {
        return new NoopGenerationSpanSink();
    }

    @Bean
    @ConditionalOnMissingBean(SpendLog.class)
    public InMemorySpendLog llmSpendLog() {
        return new InMemorySpendLog();
    }

    @Bean
    public LlmSpanEmitterListener llmSpanEmitterListener(GenerationSpanSink sink,
            ModelAliasRegistry registry, LlmGatewayMetrics metrics) {
        return new LlmSpanEmitterListener(sink, registry, metrics);
    }

    @Bean
    public SpendRecordListener llmSpendRecordListener(SpendLog spendLog,
            ModelAliasRegistry registry) {
        // UTC clock: the spend roll-up key is a UTC calendar day, never server-local.
        return new SpendRecordListener(spendLog, registry, Clock.systemUTC());
    }

    @Bean
    public ChatCompletionsController llmChatCompletionsController(
            MintedTokenAuthenticator authenticator, ModelAliasRegistry registry,
            UpstreamModelClient upstreamModelClient, LlmGatewayMetrics metrics,
            ObjectProvider<LlmCallListener> listeners) {
        return new ChatCompletionsController(authenticator, registry, upstreamModelClient,
                metrics, listeners.orderedStream().toList());
    }
}
