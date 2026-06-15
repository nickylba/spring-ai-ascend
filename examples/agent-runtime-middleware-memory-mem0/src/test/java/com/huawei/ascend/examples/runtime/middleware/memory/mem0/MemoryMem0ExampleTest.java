package com.huawei.ascend.examples.runtime.middleware.memory.mem0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("manual")
class MemoryMem0ExampleTest {
    @Test
    void mem0RestMemoryProviderWorksThroughOpenJiuwenHandlerExecution() {
        String baseUrl = System.getenv("SAA_SAMPLE_MEM0_BASE_URL");
        assumeTrue(hasText(baseUrl), "Set SAA_SAMPLE_MEM0_BASE_URL to run the real Mem0 example");
        assumeTrue(hasText(System.getenv("SAA_SAMPLE_LLM_API_KEY")),
                "Set SAA_SAMPLE_LLM_API_KEY to run the real LLM example");
        Mem0RestMemoryProvider provider = new Mem0RestMemoryProvider(
                baseUrl, System.getenv("SAA_SAMPLE_MEM0_API_KEY"), false, envOrDefault("SAA_SAMPLE_MEM0_API_MODE", "oss"));
        AgentExecutionContext context = MiddlewareTestFixtures.context("mem0-state-" + System.nanoTime());
        provider.save(context, List.of(new MemoryProvider.MemoryRecord(null, "assistant",
                "the user prefers green tea", Map.of("source", "test"))));
        SampleMem0OpenJiuwenHandler handler = new SampleMem0OpenJiuwenHandler(
                "openjiuwen-simple-agent",
                envOrDefault("SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER", "openai"),
                System.getenv("SAA_SAMPLE_LLM_API_KEY"),
                envOrDefault("SAA_SAMPLE_OPENJIUWEN_API_BASE", "https://api.deepseek.com"),
                envOrDefault("SAA_SAMPLE_LLM_MODEL", "deepseek-chat"),
                Boolean.parseBoolean(envOrDefault("SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY", "false")));
        handler.setOpenJiuwenRailFactories(handler.buildMemoryRailFactories(provider));

        List<?> rawResults = handler.execute(context).toList();

        assertThat(rawResults).isNotEmpty();
        assertThat(provider.search(context, "green tea", 5))
                .extracting(MemoryProvider.MemoryHit::content)
                .anySatisfy(content -> assertThat(content).containsIgnoringCase("green tea"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return hasText(value) ? value : fallback;
    }
}
