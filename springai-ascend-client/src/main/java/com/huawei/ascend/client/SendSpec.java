package com.huawei.ascend.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One text message addressed to an agent hosted behind the A2A ingress.
 *
 * <p>{@code agentId}, {@code sessionId} and {@code userId} ride in the A2A
 * message metadata (and {@code sessionId} additionally as the A2A
 * {@code contextId}) because the runtime routes and attributes runs from
 * exactly those keys.
 *
 * @param agentId   id of the target agent as registered on the runtime
 * @param sessionId conversation scope; reuse one id to continue a session
 * @param userId    end-user attribution forwarded to the runtime
 * @param text      the user-visible message text
 * @param messageId A2A message id; auto-generated when null or blank
 * @param metadata  extra metadata entries; the reserved routing keys
 *                  ({@code userId}/{@code agentId}/{@code sessionId}) cannot
 *                  be overridden through this map
 */
public record SendSpec(
        String agentId,
        String sessionId,
        String userId,
        String text,
        String messageId,
        Map<String, Object> metadata) {

    public SendSpec {
        requireText("agentId", agentId);
        requireText("sessionId", sessionId);
        requireText("userId", userId);
        requireText("text", text);
        messageId = messageId == null || messageId.isBlank()
                ? UUID.randomUUID().toString() : messageId;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** The common case: required fields only, generated message id, no extra metadata. */
    public static SendSpec of(String agentId, String sessionId, String userId, String text) {
        return new SendSpec(agentId, sessionId, userId, text, null, null);
    }

    /** Full A2A message metadata: caller extras first, reserved routing keys last so they win. */
    Map<String, Object> messageMetadata() {
        Map<String, Object> merged = new LinkedHashMap<>(metadata);
        merged.put("userId", userId);
        merged.put("agentId", agentId);
        merged.put("sessionId", sessionId);
        return Map.copyOf(merged);
    }

    private static void requireText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }
}
