package com.huawei.ascend.runtime.llm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.huawei.ascend.runtime.llm.gateway.spi.GenerationSpanSink;
import com.huawei.ascend.runtime.llm.gateway.spi.InMemorySpendLog;
import com.huawei.ascend.runtime.llm.gateway.spi.SpendLog;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Full-stack gateway round trip against a scripted WireMock "provider": real HTTP
 * in, real HTTP out, asserting the wire-fidelity discipline (what the agent client
 * sent is what the upstream received, modulo the two mutations the gateway owns)
 * plus the telemetry side effects (GENERATION record, spend ledger).
 */
class LlmGatewayIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CHAT_PATH = "/v1/chat/completions";

    private static WireMockServer wireMock;
    private static ConfigurableApplicationContext context;
    private static URI gatewayUri;
    private static final HttpClient client = HttpClient.newHttpClient();

    private RecordingSpanSink spanSink;
    private InMemorySpendLog spendLog;

    @BeforeAll
    static void startUpstreamAndGateway() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();

        SpringApplication app = new SpringApplication(GatewayTestApp.class);
        app.setDefaultProperties(Map.ofEntries(
                Map.entry("server.port", "0"),
                Map.entry("agent-runtime.llm.gateway.enabled", "true"),
                Map.entry("agent-runtime.llm.gateway.aliases.finance-chat.base-url",
                        "http://localhost:" + wireMock.port() + "/v1"),
                Map.entry("agent-runtime.llm.gateway.aliases.finance-chat.api-key", "sk-upstream"),
                Map.entry("agent-runtime.llm.gateway.aliases.finance-chat.provider", "openai"),
                Map.entry("agent-runtime.llm.gateway.aliases.finance-chat.upstream-model", "gpt-4o-mini"),
                Map.entry("agent-runtime.llm.gateway.aliases.finance-chat.pricing.input-per-million-tokens-usd", "1.0"),
                Map.entry("agent-runtime.llm.gateway.aliases.finance-chat.pricing.output-per-million-tokens-usd", "2.0"),
                Map.entry("agent-runtime.llm.gateway.aliases.plain-model.base-url",
                        "http://localhost:" + wireMock.port() + "/v1"),
                Map.entry("agent-runtime.llm.gateway.aliases.plain-model.api-key", "sk-upstream"),
                Map.entry("agent-runtime.llm.gateway.tokens.minted-secret.tenant-id", "tenant-a"),
                Map.entry("agent-runtime.llm.gateway.tokens.minted-secret.agent-id", "agent-1")));
        // The exclude list must ride command-line args: the library application.yml
        // defines spring.autoconfigure.exclude itself, which outranks defaultProperties
        // and would silently replace this list (Postgres autoconfig would then boot).
        context = app.run("--spring.autoconfigure.exclude=" + String.join(",",
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
                "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
                "io.github.resilience4j.springboot3.verifier.autoconfigure.SpringBoot3VerifierAutoConfiguration",
                "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
                "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
                "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
                "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration",
                "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
                "org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration"));
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        gatewayUri = URI.create("http://localhost:" + port + CHAT_PATH);
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            context.close();
        }
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void resetState() {
        wireMock.resetAll();
        spanSink = context.getBean(RecordingSpanSink.class);
        spanSink.spans.clear();
        spendLog = (InMemorySpendLog) context.getBean(SpendLog.class);
    }

    @Test
    void nonStreamingRoundTripIsByteIdenticalModuloModelSwap() throws Exception {
        byte[] upstreamBody = ("{\"id\":\"chatcmpl-9\",\"choices\":[{\"message\":{\"content\":\"hi\"}}],"
                + "\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":7}}")
                .getBytes(StandardCharsets.UTF_8);
        wireMock.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody(upstreamBody)));
        String sent = "{\"model\":\"finance-chat\",\"temperature\":0.2,"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}]}";

        HttpResponse<byte[]> response = send(sent, "Bearer minted-secret");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(upstreamBody);

        byte[] received = wireMock.findAll(postRequestedFor(urlEqualTo(CHAT_PATH)))
                .get(0).getBody();
        ObjectNode expected = (ObjectNode) JSON.readTree(sent);
        expected.put("model", "gpt-4o-mini");
        assertThat(JSON.readTree(received)).isEqualTo(expected);
        assertThat(wireMock.findAll(postRequestedFor(urlEqualTo(CHAT_PATH))).get(0)
                .getHeader("Authorization")).isEqualTo("Bearer sk-upstream");
    }

    @Test
    void unmappedAliasForwardsTheExactBytesTheClientSent() throws Exception {
        wireMock.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{}")));
        // Eccentric formatting on purpose: pass-through must preserve it byte-for-byte.
        String sent = "{ \"model\" : \"plain-model\",\n  \"messages\":[ ] ,\"n\":1 }";

        send(sent, "Bearer minted-secret");

        byte[] received = wireMock.findAll(postRequestedFor(urlEqualTo(CHAT_PATH)))
                .get(0).getBody();
        assertThat(received).isEqualTo(sent.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void missingTokenIs401AndTheUpstreamIsNeverContacted() throws Exception {
        HttpResponse<byte[]> response = send("{\"model\":\"finance-chat\"}", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(errorType(response.body())).isEqualTo("authentication_error");
        assertThat(wireMock.getAllServeEvents()).isEmpty();
        assertThat(spanSink.spans).isEmpty();
    }

    @Test
    void unknownAliasIs404AndTheUpstreamIsNeverContacted() throws Exception {
        HttpResponse<byte[]> response = send("{\"model\":\"ghost\"}", "Bearer minted-secret");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(errorType(response.body())).isEqualTo("invalid_request_error");
        assertThat(wireMock.getAllServeEvents()).isEmpty();
    }

    @Test
    void upstream500SurfacesAs502UpstreamError() throws Exception {
        wireMock.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(aResponse()
                .withStatus(500).withBody("{\"error\":\"provider exploded\"}")));

        HttpResponse<byte[]> response =
                send("{\"model\":\"finance-chat\",\"messages\":[]}", "Bearer minted-secret");

        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(errorType(response.body())).isEqualTo("upstream_error");
    }

    @Test
    void ssePassThroughPreservesChunkContentAndOrder() throws Exception {
        String sse = """
                data: {"choices":[{"delta":{"content":"He"}}],"usage":null}

                data: {"choices":[{"delta":{"content":"llo"}}],"usage":null}

                data: {"choices":[],"usage":{"prompt_tokens":6,"completion_tokens":3}}

                data: [DONE]

                """;
        wireMock.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "text/event-stream")
                .withBody(sse)));

        HttpResponse<byte[]> response = send(
                "{\"model\":\"finance-chat\",\"stream\":true,\"messages\":[]}", "Bearer minted-secret");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("text/event-stream");
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(sse);

        JsonNode forwarded = JSON.readTree(
                wireMock.findAll(postRequestedFor(urlEqualTo(CHAT_PATH))).get(0).getBody());
        assertThat(forwarded.get("stream_options").get("include_usage").asBoolean()).isTrue();

        // The GENERATION record lands on the relay thread after the last byte.
        awaitUntil(() -> !spanSink.spans.isEmpty());
        GenerationSpanSink.GenerationSpan span = spanSink.spans.get(0);
        assertThat(span.inputTokens()).isEqualTo(6);
        assertThat(span.outputTokens()).isEqualTo(3);
    }

    @Test
    void successfulCallEmitsGenerationRecordWithAllSixAttributesPlusTenant() throws Exception {
        wireMock.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"usage\":{\"prompt_tokens\":1000,\"completion_tokens\":500}}")));

        send("{\"model\":\"finance-chat\",\"messages\":[]}", "Bearer minted-secret");

        awaitUntil(() -> !spanSink.spans.isEmpty());
        GenerationSpanSink.GenerationSpan span = spanSink.spans.get(0);
        assertThat(span.genAiSystem()).isEqualTo("openai");
        assertThat(span.genAiRequestModel()).isEqualTo("finance-chat");
        assertThat(span.inputTokens()).isEqualTo(1000);
        assertThat(span.outputTokens()).isEqualTo(500);
        assertThat(span.costUsd()).isEqualTo(1000 / 1_000_000.0 + 500 * 2.0 / 1_000_000.0);
        assertThat(span.latencyMs()).isNotNegative();
        assertThat(span.tenantId()).isEqualTo("tenant-a");
    }

    @Test
    void successfulCallAppendsASpendLedgerRow() throws Exception {
        wireMock.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":50}}")));
        int rowsBefore = spendLog.records().size();

        send("{\"model\":\"finance-chat\",\"messages\":[]}", "Bearer minted-secret");

        awaitUntil(() -> spendLog.records().size() > rowsBefore);
        SpendLog.SpendRecord record = spendLog.records().get(spendLog.records().size() - 1);
        assertThat(record.tenantId()).isEqualTo("tenant-a");
        assertThat(record.agentId()).isEqualTo("agent-1");
        assertThat(record.modelAlias()).isEqualTo("finance-chat");
        assertThat(record.day()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
        assertThat(record.inputTokens()).isEqualTo(100);
        assertThat(record.outputTokens()).isEqualTo(50);
        assertThat(record.costUsd())
                .isEqualTo(100 / 1_000_000.0 + 50 * 2.0 / 1_000_000.0);
    }

    private static HttpResponse<byte[]> send(String body, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(gatewayUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String errorType(byte[] body) throws Exception {
        return JSON.readTree(body).path("error").path("type").asText();
    }

    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 5s");
            }
            Thread.sleep(20);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class GatewayTestApp {

        @Bean
        RecordingSpanSink recordingSpanSink() {
            return new RecordingSpanSink();
        }
    }

    static final class RecordingSpanSink implements GenerationSpanSink {
        final List<GenerationSpan> spans = new CopyOnWriteArrayList<>();

        @Override
        public void emit(GenerationSpan span) {
            spans.add(span);
        }
    }
}
