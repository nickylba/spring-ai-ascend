package com.huawei.ascend.examples.runtime.middleware.memory.inmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("manual")
class MemoryInMemoryExampleTest {

    @Test
    void inMemoryMemoryProviderWorksThroughOpenJiuwenHandlerExecution() {
        assumeTrue(hasText(System.getenv("SAA_SAMPLE_LLM_API_KEY")),
                "Set SAA_SAMPLE_LLM_API_KEY to run the real LLM example");
        InMemoryMemoryProvider provider = new InMemoryMemoryProvider();
        AgentExecutionContext first = MiddlewareTestFixtures.context("memory-state-a");
        AgentExecutionContext second = MiddlewareTestFixtures.context("memory-state-b");
        provider.save(first, List.of(record("the user prefers green tea")));
        provider.save(second, List.of(record("the user prefers black coffee")));
        SampleMemoryOpenJiuwenHandler handler = new SampleMemoryOpenJiuwenHandler(
                "openjiuwen-simple-agent",
                envOrDefault("SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER", "openai"),
                System.getenv("SAA_SAMPLE_LLM_API_KEY"),
                envOrDefault("SAA_SAMPLE_OPENJIUWEN_API_BASE", "https://api.deepseek.com"),
                envOrDefault("SAA_SAMPLE_LLM_MODEL", "deepseek-chat"),
                Boolean.parseBoolean(envOrDefault("SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY", "false")));
        handler.setOpenJiuwenRailFactories(handler.buildMemoryRailFactories(provider));

        List<?> rawResults = handler.execute(first).toList();

        assertThat(rawResults).isNotEmpty();
        assertThat(provider.search(first, "black coffee", 3)).isEmpty();
        assertThat(provider.search(first, "green tea", 3))
                .first()
                .satisfies(hit -> assertThat(hit.content()).contains("green tea"));
        assertThat(provider.records(first))
                .extracting(MemoryProvider.MemoryRecord::content)
                .contains("the user prefers green tea", "green tea");
    }

    private static MemoryProvider.MemoryRecord record(String content) {
        return new MemoryProvider.MemoryRecord(null, "assistant", content, Map.of("source", "test"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return hasText(value) ? value : fallback;
    }
}
