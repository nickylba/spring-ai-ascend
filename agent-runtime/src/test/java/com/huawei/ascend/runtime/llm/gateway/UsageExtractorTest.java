package com.huawei.ascend.runtime.llm.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.llm.gateway.spi.LlmTokenUsage;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UsageExtractorTest {

    private final UsageExtractor extractor = new UsageExtractor();

    @Test
    void readsOpenAiUsageFromNonStreamingResponse() {
        LlmTokenUsage usage = extractor.fromResponseBody("""
                {"id":"chatcmpl-1","choices":[{"message":{"content":"hi"}}],
                 "usage":{"prompt_tokens":12,"completion_tokens":34,"total_tokens":46}}
                """.getBytes(StandardCharsets.UTF_8));

        assertThat(usage).isEqualTo(new LlmTokenUsage(12, 34, false));
    }

    @Test
    void readsInputOutputTokenDialect() {
        LlmTokenUsage usage = extractor.fromResponseBody("""
                {"usage":{"input_tokens":7,"output_tokens":9}}
                """.getBytes(StandardCharsets.UTF_8));

        assertThat(usage).isEqualTo(new LlmTokenUsage(7, 9, false));
    }

    @Test
    void absentUsageIsEstimatedZeroNeverInvented() {
        LlmTokenUsage usage = extractor.fromResponseBody(
                "{\"id\":\"chatcmpl-1\",\"choices\":[]}".getBytes(StandardCharsets.UTF_8));

        assertThat(usage).isEqualTo(LlmTokenUsage.estimatedAbsent());
        assertThat(usage.estimated()).isTrue();
        assertThat(usage.inputTokens()).isZero();
        assertThat(usage.outputTokens()).isZero();
    }

    @Test
    void unparseableBodyIsEstimated() {
        assertThat(extractor.fromResponseBody("not json".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(LlmTokenUsage.estimatedAbsent());
    }

    @Test
    void sseFinalChunkUsageWinsAndNullUsageChunksAreSkipped() {
        // include_usage streams carry "usage": null on every chunk before the last.
        String stream = """
                data: {"choices":[{"delta":{"content":"He"}}],"usage":null}

                data: {"choices":[{"delta":{"content":"llo"}}],"usage":null}

                data: {"choices":[],"usage":{"prompt_tokens":5,"completion_tokens":2}}

                data: [DONE]

                """;

        assertThat(feed(stream)).isEqualTo(new LlmTokenUsage(5, 2, false));
    }

    @Test
    void sseUsageSurvivesChunkBoundariesInsideALine() {
        UsageExtractor.SseAccumulator accumulator = extractor.newSseAccumulator();
        byte[] part1 = "data: {\"usage\":{\"prompt_to".getBytes(StandardCharsets.UTF_8);
        byte[] part2 = "kens\":3,\"completion_tokens\":4}}\n\ndata: [DONE]\n\n"
                .getBytes(StandardCharsets.UTF_8);

        accumulator.accept(part1, part1.length);
        accumulator.accept(part2, part2.length);

        assertThat(accumulator.finish()).isEqualTo(new LlmTokenUsage(3, 4, false));
    }

    @Test
    void sseStreamWithoutUsageIsEstimated() {
        String stream = """
                data: {"choices":[{"delta":{"content":"Hi"}}]}

                data: [DONE]

                """;

        assertThat(feed(stream)).isEqualTo(LlmTokenUsage.estimatedAbsent());
    }

    @Test
    void sseStreamMissingTrailingNewlineStillYieldsUsage() {
        assertThat(feed("data: {\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1}}"))
                .isEqualTo(new LlmTokenUsage(1, 1, false));
    }

    private LlmTokenUsage feed(String stream) {
        UsageExtractor.SseAccumulator accumulator = extractor.newSseAccumulator();
        byte[] bytes = stream.getBytes(StandardCharsets.UTF_8);
        accumulator.accept(bytes, bytes.length);
        return accumulator.finish();
    }
}
