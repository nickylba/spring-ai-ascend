package com.huawei.ascend.runtime.llm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmTokenUsage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Reads the OpenAI-compatible {@code usage} object out of upstream responses.
 * Non-streaming responses carry it at the top level; streams carry it in the final
 * usage-bearing SSE chunk (providers send {@code "usage": null} on every earlier
 * chunk when {@code stream_options.include_usage} is on, so nulls are skipped).
 * Both the {@code prompt_tokens}/{@code completion_tokens} and the
 * {@code input_tokens}/{@code output_tokens} field dialects are accepted. When no
 * usage is found the result is flagged estimated with zero tokens — never invented.
 */
final class UsageExtractor {

    private final ObjectMapper mapper = new ObjectMapper();

    LlmTokenUsage fromResponseBody(byte[] body) {
        try {
            return fromUsageNode(mapper.readTree(body).get("usage"));
        } catch (IOException e) {
            return LlmTokenUsage.estimatedAbsent();
        }
    }

    SseAccumulator newSseAccumulator() {
        return new SseAccumulator();
    }

    private static LlmTokenUsage fromUsageNode(JsonNode usage) {
        if (usage == null || !usage.isObject()) {
            return LlmTokenUsage.estimatedAbsent();
        }
        long input = firstLong(usage, "prompt_tokens", "input_tokens");
        long output = firstLong(usage, "completion_tokens", "output_tokens");
        return new LlmTokenUsage(input, output, false);
    }

    private static long firstLong(JsonNode usage, String primaryField, String alternateField) {
        JsonNode node = usage.get(primaryField);
        if (node == null || !node.isNumber()) {
            node = usage.get(alternateField);
        }
        return node != null && node.isNumber() ? node.asLong() : 0;
    }

    /**
     * Incremental SSE scanner fed with the same chunks that are relayed to the
     * client. Splitting on the {@code '\n'} byte is UTF-8 safe (that byte never
     * occurs inside a multi-byte sequence), so only one line is ever buffered —
     * the relay stays O(line), not O(stream).
     */
    final class SseAccumulator {

        private static final String DATA_PREFIX = "data:";

        private final ByteArrayOutputStream currentLine = new ByteArrayOutputStream();
        private LlmTokenUsage lastSeenUsage;

        void accept(byte[] chunk, int length) {
            for (int i = 0; i < length; i++) {
                if (chunk[i] == '\n') {
                    completeLine();
                } else {
                    currentLine.write(chunk[i]);
                }
            }
        }

        LlmTokenUsage finish() {
            completeLine();
            return lastSeenUsage == null ? LlmTokenUsage.estimatedAbsent() : lastSeenUsage;
        }

        private void completeLine() {
            String line = currentLine.toString(StandardCharsets.UTF_8).trim();
            currentLine.reset();
            if (!line.startsWith(DATA_PREFIX)) {
                return;
            }
            String payload = line.substring(DATA_PREFIX.length()).trim();
            if (payload.isEmpty() || payload.equals("[DONE]")) {
                return;
            }
            try {
                JsonNode usage = mapper.readTree(payload).get("usage");
                if (usage != null && usage.isObject()) {
                    lastSeenUsage = fromUsageNode(usage);
                }
            } catch (IOException e) {
                // Not JSON we recognise — pass-through fidelity is unaffected; only
                // usage extraction degrades, and finish() then reports estimated.
            }
        }
    }
}
