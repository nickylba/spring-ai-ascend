package com.huawei.ascend.runtime.llm.gateway;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Default upstream forwarder. Non-streaming exchanges go through Spring
 * {@link RestClient} (raw {@code exchange} access, so 4xx/5xx come back as data
 * instead of exceptions). Streaming uses the JDK {@link java.net.http.HttpClient}
 * directly because {@code RestClient} closes the upstream response when its
 * exchange callback returns — too early for a servlet streaming body that relays
 * chunks on another thread.
 */
public final class RestClientUpstreamModelClient implements UpstreamModelClient {

    private final RestClient restClient;
    private final java.net.http.HttpClient streamingClient;

    public RestClientUpstreamModelClient() {
        // The JDK request factory on both paths: it streams response bodies without
        // buffering, which the SSE relay depends on.
        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
        this.streamingClient = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public UpstreamResponse exchange(UpstreamRequest request) {
        try {
            return restClient.post()
                    .uri(URI.create(request.url()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request.body())
                    .exchange((clientRequest, clientResponse) -> new UpstreamResponse(
                            clientResponse.getStatusCode().value(),
                            contentTypeOf(clientResponse.getHeaders()),
                            clientResponse.getBody().readAllBytes()));
        } catch (ResourceAccessException | UncheckedIOException e) {
            throw new UpstreamIoException("upstream I/O failure: " + e.getMessage(), e);
        }
    }

    @Override
    public UpstreamStreamResponse openStream(UpstreamRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(request.url()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.apiKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(request.body()))
                .build();
        try {
            HttpResponse<InputStream> response =
                    streamingClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            return new UpstreamStreamResponse(
                    response.statusCode(),
                    response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(null),
                    response.body());
        } catch (IOException e) {
            throw new UpstreamIoException("upstream I/O failure: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamIoException("interrupted while contacting upstream", e);
        }
    }

    private static String contentTypeOf(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        return contentType == null ? null : contentType.toString();
    }
}
