package com.huawei.ascend.runtime.llm.gateway;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Port to the upstream OpenAI-compatible provider. This is the deliberate
 * provider-neutral seam: the gateway forwards opaque JSON bytes — no chat-model
 * abstraction, no re-marshalling — so the wire dialect survives the proxy intact.
 * Tests substitute it to run the gateway against a scripted upstream.
 */
public interface UpstreamModelClient {

    /**
     * Forwards a non-streaming chat-completions request and returns the upstream
     * response whatever its status — HTTP-level errors are data to pass through,
     * not exceptions.
     *
     * @throws UpstreamIoException when no upstream response was received at all
     */
    UpstreamResponse exchange(UpstreamRequest request);

    /**
     * Forwards a streaming chat-completions request and hands back the still-open
     * response body so the caller can relay bytes as the upstream produces them.
     * The caller owns the returned stream and must close it.
     *
     * @throws UpstreamIoException when no upstream response was received at all
     */
    UpstreamStreamResponse openStream(UpstreamRequest request);

    /**
     * @param url    absolute upstream chat-completions URL
     * @param apiKey real upstream credential, sent as {@code Authorization: Bearer …}
     * @param body   the exact JSON bytes to forward
     */
    record UpstreamRequest(String url, String apiKey, byte[] body) {
    }

    /** A fully-buffered upstream response: status, content type and exact body bytes. */
    record UpstreamResponse(int status, String contentType, byte[] body) {
    }

    /** An open upstream response whose body streams as the upstream produces it. */
    record UpstreamStreamResponse(int status, String contentType, InputStream body)
            implements Closeable {

        @Override
        public void close() throws IOException {
            body.close();
        }
    }

    /** Connect/IO failure before any upstream response — the gateway answers 502. */
    final class UpstreamIoException extends RuntimeException {

        public UpstreamIoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
