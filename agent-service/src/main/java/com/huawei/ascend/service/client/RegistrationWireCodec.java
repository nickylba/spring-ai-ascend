package com.huawei.ascend.service.client;

import com.huawei.ascend.service.spi.registry.RuntimeAgentRegistration;
import com.huawei.ascend.service.spi.registry.RuntimeLeaseRenewal;
import com.huawei.ascend.service.spi.registry.RuntimeRegistrationResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Maps the {@code spi.registry} records onto the HTTP/JSON wire shape of the
 * {@code /v1/runtime-registrations} routes so the Spring-free runtime side
 * never needs the service's Spring edge on its classpath.
 */
final class RegistrationWireCodec {

    /** Service-side rejection payload, decoded leniently across error shapes. */
    record Rejection(String code, String message) {
    }

    private final JsonMapper json = JsonMapper.builder().build();

    String registrationBody(RuntimeAgentRegistration registration) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runtimeInstanceId", registration.runtimeInstanceId().value());
        body.put("tenantId", registration.tenantId());
        body.put("agentId", registration.agentId());
        // The agent card is a third-party A2A spec type with polymorphic
        // members (securitySchemes); its wire shape belongs to the SDK's own
        // serializer, never to a default-Jackson view of the record graph.
        body.put("agentCard", agentCardWireJson(registration));
        body.put("a2aEndpoint", registration.a2aEndpoint().toString());
        body.put("healthEndpoint", registration.healthEndpoint().toString());
        body.put("version", registration.version());
        body.put("ttlSeconds", registration.ttl().toSeconds());
        body.put("capacitySnapshot", registration.capacitySnapshot());
        body.put("metadata", registration.metadata());
        return json.writeValueAsString(body);
    }

    String renewalBody(RuntimeLeaseRenewal renewal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", renewal.state());
        body.put("ttlSeconds", renewal.ttl().toSeconds());
        body.put("slaSnapshot", renewal.slaSnapshot());
        body.put("capacitySnapshot", renewal.capacitySnapshot());
        body.put("metadata", renewal.metadata());
        return json.writeValueAsString(body);
    }

    RuntimeRegistrationResult readRegistrationResult(String body) {
        return json.readValue(body, RuntimeRegistrationResult.class);
    }

    Rejection readRejection(String body) {
        String code = null;
        String message = body;
        try {
            JsonNode node = json.readTree(body);
            if (node.path("code").isString()) {
                code = node.path("code").asText();
            }
            if (node.path("message").isString()) {
                message = node.path("message").asText();
            } else if (node.path("error").path("message").isString()) {
                // The JWT service ingress rejects with {"error":{"status","message"}}.
                message = node.path("error").path("message").asText();
            }
        } catch (RuntimeException ignored) {
            // Non-JSON error body: surface the raw payload as the message.
        }
        return new Rejection(code, message);
    }

    private JsonNode agentCardWireJson(RuntimeAgentRegistration registration) {
        try {
            return json.readTree(JsonUtil.toJson(registration.agentCard()));
        } catch (Exception ex) {
            throw new RuntimeRegistrationClientException(
                    "Failed to serialize the agent card for " + registration.agentId(), ex);
        }
    }
}
