package com.huawei.ascend.runtime.engine.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.AgentCapabilitiesDescriptor;
import com.huawei.ascend.runtime.engine.spi.AgentCardDescriptor;
import com.huawei.ascend.runtime.engine.spi.AgentInterfaceDescriptor;
import com.huawei.ascend.runtime.engine.spi.AgentSkillDescriptor;
import com.huawei.ascend.runtime.engine.spi.SecuritySchemeDescriptor;
import com.huawei.ascend.runtime.engine.spi.SignatureDescriptor;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.spec.APIKeySecurityScheme;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link A2aAgentCardMapper#toAgentCard} produces a wire-identical card
 * to the legacy {@link AgentCards#create} shape, and that descriptor fields
 * flow through correctly.
 */
class A2aAgentCardMapperTest {

    @Test
    void defaultDescriptorProducesHonestCard() {
        // The default descriptor now uses fail-safe defaults (streaming=false, push=false, outputModes=["text"]).
        // AgentCards.create still exists for backward-compatibility call sites; both now share the same defaults.
        AgentCardDescriptor descriptor = AgentCardDescriptor.of("sample-agent", "agent-runtime");
        AgentCard fromDescriptor = A2aAgentCardMapper.toAgentCard(descriptor);

        assertThat(fromDescriptor.name()).isEqualTo("sample-agent");
        assertThat(fromDescriptor.description()).isEqualTo("agent-runtime");
        assertThat(fromDescriptor.version()).isEqualTo("0.1.0");
        assertThat(fromDescriptor.url()).isEqualTo("/a2a");
        assertThat(fromDescriptor.provider().organization()).isEqualTo("spring-ai-ascend");
        assertThat(fromDescriptor.capabilities().streaming()).isFalse();
        assertThat(fromDescriptor.capabilities().pushNotifications()).isFalse();
        assertThat(fromDescriptor.defaultInputModes()).containsExactly("text");
        assertThat(fromDescriptor.defaultOutputModes()).containsExactly("text");
        assertThat(fromDescriptor.skills()).isEmpty();
        assertThat(fromDescriptor.preferredTransport()).isEqualTo(TransportProtocol.JSONRPC.asString());
    }

    @Test
    void mappedCardHasFailSafeCapabilitiesByDefault() {
        // Intentional honest-default change (#229/#230): streaming=false, push=false.
        AgentCard card = A2aAgentCardMapper.toAgentCard(AgentCardDescriptor.of("a", "b"));

        assertThat(card.capabilities().streaming()).isFalse();
        assertThat(card.capabilities().pushNotifications()).isFalse();
        assertThat(card.capabilities().extendedAgentCard()).isFalse();
    }

    @Test
    void explicitStreamingTrueFlowsThrough() {
        AgentCardDescriptor d = AgentCardDescriptor.of("a", "b")
                .withCapabilities(new AgentCapabilitiesDescriptor(true, false, false));
        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.capabilities().streaming()).isTrue();
    }

    @Test
    void explicitPushNotificationsTrueFlowsThrough() {
        AgentCardDescriptor d = AgentCardDescriptor.of("a", "b")
                .withCapabilities(new AgentCapabilitiesDescriptor(false, true, false));
        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.capabilities().pushNotifications()).isTrue();
    }

    @Test
    void mappedCardHasDefaultVersion() {
        AgentCard card = A2aAgentCardMapper.toAgentCard(AgentCardDescriptor.of("a", "b"));

        assertThat(card.version()).isEqualTo("0.1.0");
    }

    @Test
    void mappedCardHasFailSafeOutputModesByDefault() {
        // Intentional honest-default change (#233): defaultOutputModes defaults to ["text"].
        AgentCard card = A2aAgentCardMapper.toAgentCard(AgentCardDescriptor.of("a", "b"));

        assertThat(card.defaultOutputModes()).containsExactly("text");
    }

    @Test
    void artifactOutputModeFlowsThroughWhenExplicitlySet() {
        AgentCardDescriptor d = AgentCardDescriptor.of("a", "b")
                .withDefaultOutputModes(List.of("text", "artifact"));
        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.defaultOutputModes()).containsExactly("text", "artifact");
    }

    @Test
    void mappedCardHasDefaultInputModes() {
        AgentCard card = A2aAgentCardMapper.toAgentCard(AgentCardDescriptor.of("a", "b"));

        assertThat(card.defaultInputModes()).containsExactly("text");
    }

    @Test
    void mappedCardHasDefaultProviderOrganization() {
        AgentCard card = A2aAgentCardMapper.toAgentCard(AgentCardDescriptor.of("a", "b"));

        assertThat(card.provider().organization()).isEqualTo("spring-ai-ascend");
        assertThat(card.provider().url()).isEqualTo("");
    }

    @Test
    void mappedCardHasJsonRpcAsPreferredTransport() {
        AgentCard card = A2aAgentCardMapper.toAgentCard(AgentCardDescriptor.of("a", "b"));

        assertThat(card.preferredTransport()).isEqualTo(TransportProtocol.JSONRPC.asString());
    }

    @Test
    void mappedCardHasSingleJsonRpcInterface() {
        AgentCard card = A2aAgentCardMapper.toAgentCard(AgentCardDescriptor.of("a", "b"));

        assertThat(card.supportedInterfaces()).hasSize(1);
        AgentInterface iface = card.supportedInterfaces().get(0);
        assertThat(iface.protocolBinding()).isEqualTo(TransportProtocol.JSONRPC.asString());
        assertThat(iface.url()).isEqualTo("/a2a");
    }

    @Test
    void customVersionFlowsThrough() {
        AgentCardDescriptor d = AgentCardDescriptor.of("a", "b").withVersion("2.5.0");

        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.version()).isEqualTo("2.5.0");
    }

    @Test
    void customEndpointFlowsThrough() {
        AgentCardDescriptor d = AgentCardDescriptor.of("a", "b").withEndpoint("/custom");

        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.url()).isEqualTo("/custom");
        assertThat(card.supportedInterfaces().get(0).url()).isEqualTo("/custom");
    }

    @Test
    void customProviderFlowsThrough() {
        AgentCardDescriptor d = AgentCardDescriptor.of("a", "b")
                .withProvider("acme", "https://acme.example.com");

        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.provider().organization()).isEqualTo("acme");
        assertThat(card.provider().url()).isEqualTo("https://acme.example.com");
    }

    @Test
    void customCapabilitiesFlowThrough() {
        AgentCapabilitiesDescriptor none = AgentCapabilitiesDescriptor.none();
        AgentCardDescriptor d = AgentCardDescriptor.of("a", "b").withCapabilities(none);

        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.capabilities().streaming()).isFalse();
        assertThat(card.capabilities().pushNotifications()).isFalse();
        assertThat(card.capabilities().extendedAgentCard()).isFalse();
    }

    @Test
    void skillsFlowThrough() {
        AgentSkillDescriptor skill = new AgentSkillDescriptor(
                "s1", "My Skill", "Does stuff",
                List.of("tag1"), List.of("example"), List.of("text"), List.of("text"));
        AgentCardDescriptor d = AgentCardDescriptor.of("a", "b").withSkills(List.of(skill));

        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.skills()).hasSize(1);
        assertThat(card.skills().get(0).id()).isEqualTo("s1");
        assertThat(card.skills().get(0).name()).isEqualTo("My Skill");
        assertThat(card.skills().get(0).tags()).containsExactly("tag1");
    }

    @Test
    void additionalInterfacesAppendAfterPrimary() {
        AgentInterfaceDescriptor extra = AgentInterfaceDescriptor.of("HTTP_JSON", "/a2a/http");
        AgentCardDescriptor d = new AgentCardDescriptor(
                "a", "b", "0.1.0", "/a2a", "spring-ai-ascend", "",
                null, null, null,
                AgentCapabilitiesDescriptor.defaults(),
                List.of("text"), List.of("text", "artifact"),
                null, null, null,
                List.of(extra),
                null);

        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.supportedInterfaces()).hasSize(2);
        assertThat(card.supportedInterfaces().get(0).protocolBinding())
                .isEqualTo(TransportProtocol.JSONRPC.asString());
        assertThat(card.supportedInterfaces().get(1).protocolBinding()).isEqualTo("HTTP_JSON");
    }

    @Test
    void securitySchemesFlowThrough() {
        SecuritySchemeDescriptor scheme = SecuritySchemeDescriptor.apiKey("header", "X-Key", "API key");
        AgentCardDescriptor d = new AgentCardDescriptor(
                "a", "b", "0.1.0", "/a2a", "spring-ai-ascend", "",
                null, null, null,
                AgentCapabilitiesDescriptor.defaults(),
                List.of("text"), List.of("text", "artifact"),
                null, Map.of("apiKey", scheme), null, null, null);

        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.securitySchemes()).containsKey("apiKey");
        // The A2A SDK APIKeySecurityScheme.type() returns the SDK-internal type string.
        assertThat(card.securitySchemes().get("apiKey").type()).isNotBlank();
    }

    @Test
    void emptySkillsYieldsEmptySkillsList() {
        AgentCard card = A2aAgentCardMapper.toAgentCard(AgentCardDescriptor.of("a", "b"));

        assertThat(card.skills()).isEmpty();
    }

    // --- New tests for E2 (#229/#230/#231/#232/#233) ---

    @Test
    void primaryJsonRpcInterfaceHasNonNullProtocolVersion() {
        // The 2-arg AgentInterface constructor automatically sets protocolVersion=CURRENT_PROTOCOL_VERSION.
        AgentCard card = A2aAgentCardMapper.toAgentCard(AgentCardDescriptor.of("a", "b"));

        AgentInterface primary = card.supportedInterfaces().get(0);
        assertThat(primary.protocolVersion())
                .as("primary JSONRPC interface must carry a non-null protocolVersion")
                .isNotNull()
                .isNotBlank();
    }

    @Test
    void additionalInterfaceWithExplicitProtocolVersionSetsProtocolVersionNotTenant() {
        // The 4-arg ctor AgentInterface(binding, url, tenant=null, protocolVersion) is used for
        // additional interfaces when protocolVersion is declared — it must land in protocolVersion(),
        // not in tenant().
        AgentInterfaceDescriptor extra = new AgentInterfaceDescriptor("HTTP_JSON", "/a2a/http", "0.9");
        AgentCardDescriptor d = AgentCardDescriptor.of("a", "b")
                .withCapabilities(AgentCapabilitiesDescriptor.none());
        d = new AgentCardDescriptor(
                d.name(), d.description(), d.version(), d.endpoint(),
                d.providerOrganization(), d.providerUrl(),
                d.documentationUrl(), d.iconUrl(), d.protocolVersion(),
                d.capabilities(),
                d.defaultInputModes(), d.defaultOutputModes(),
                d.skills(), d.securitySchemes(), d.securityRequirements(),
                List.of(extra), d.signatures());

        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        AgentInterface additional = card.supportedInterfaces().get(1);
        assertThat(additional.protocolBinding()).isEqualTo("HTTP_JSON");
        assertThat(additional.protocolVersion()).isEqualTo("0.9");
        // tenant is set to "" (empty string) because the 4-arg SDK ctor requires non-null tenant.
    }

    @Test
    void nullCapabilitiesDescriptorMapsToFailSafeDefaults() {
        // When capabilities is null in the descriptor the mapper must apply fail-safe defaults.
        AgentCardDescriptor d = new AgentCardDescriptor(
                "a", "b", "0.1.0", "/a2a", "spring-ai-ascend", "",
                null, null, null,
                null,   // null capabilities
                List.of("text"), List.of("text"),
                null, null, null, null, null);

        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.capabilities().streaming()).isFalse();
        assertThat(card.capabilities().pushNotifications()).isFalse();
    }

    // --- New tests for E3 (#234/#235/#236/#237) ---

    @Test
    void apiKeySecuritySchemeDescriptorMapsToApiKeySecurityScheme() {
        SecuritySchemeDescriptor scheme = SecuritySchemeDescriptor.apiKey("header", "X-Tenant-Id",
                "Tenant identifier.");
        AgentCardDescriptor d = AgentCardDescriptor.of("a", "b")
                .withSecuritySchemes(Map.of("x-tenant-id", scheme));

        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.securitySchemes()).containsKey("x-tenant-id");
        assertThat(card.securitySchemes().get("x-tenant-id")).isInstanceOf(APIKeySecurityScheme.class);
        APIKeySecurityScheme apiKey = (APIKeySecurityScheme) card.securitySchemes().get("x-tenant-id");
        assertThat(apiKey.location()).isEqualTo(APIKeySecurityScheme.Location.HEADER);
        assertThat(apiKey.name()).isEqualTo("X-Tenant-Id");
    }

    @Test
    void httpSecuritySchemeDescriptorMapsToHttpAuthSecurityScheme() {
        SecuritySchemeDescriptor scheme = SecuritySchemeDescriptor.http("bearer", "Bearer token.");
        AgentCardDescriptor d = AgentCardDescriptor.of("a", "b")
                .withSecuritySchemes(Map.of("bearerAuth", scheme));

        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.securitySchemes()).containsKey("bearerAuth");
        // The A2A SDK's HTTPAuthSecurityScheme.type() returns its SDK-internal type string.
        assertThat(card.securitySchemes().get("bearerAuth").type()).isNotBlank();
    }

    @Test
    void additionalInterfacesFromWitherFlowThrough() {
        AgentInterfaceDescriptor grpc = AgentInterfaceDescriptor.of("GRPC", "/a2a/grpc");
        AgentCardDescriptor d = AgentCardDescriptor.of("a", "b")
                .withAdditionalInterfaces(List.of(grpc));

        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.supportedInterfaces()).hasSize(2);
        assertThat(card.supportedInterfaces().get(1).protocolBinding()).isEqualTo("GRPC");
        assertThat(card.supportedInterfaces().get(1).url()).isEqualTo("/a2a/grpc");
    }

    @Test
    void documentationUrlAndIconUrlFlowThrough() {
        AgentCardDescriptor d = AgentCardDescriptor.of("a", "b")
                .withDocumentationUrl("https://docs.example.com")
                .withIconUrl("https://example.com/icon.png");

        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.documentationUrl()).isEqualTo("https://docs.example.com");
        assertThat(card.iconUrl()).isEqualTo("https://example.com/icon.png");
    }

    @Test
    void signatureFlowsThrough() {
        SignatureDescriptor sig = new SignatureDescriptor("eyJ...", "sig123", null);
        AgentCardDescriptor d = AgentCardDescriptor.of("a", "b")
                .withSignatures(List.of(sig));

        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.signatures()).hasSize(1);
        assertThat(card.signatures().get(0).protectedHeader()).isEqualTo("eyJ...");
        assertThat(card.signatures().get(0).signature()).isEqualTo("sig123");
    }

    @Test
    void securityRequirementsFlowThrough() {
        SecuritySchemeDescriptor scheme = SecuritySchemeDescriptor.apiKey("header", "X-Key", null);
        AgentCardDescriptor d = AgentCardDescriptor.of("a", "b")
                .withSecuritySchemes(Map.of("apiKey", scheme))
                .withSecurityRequirements(List.of(Map.of("apiKey", List.of())));

        AgentCard card = A2aAgentCardMapper.toAgentCard(d);

        assertThat(card.securityRequirements()).hasSize(1);
        assertThat(card.securityRequirements().get(0).schemes()).containsKey("apiKey");
    }
}
