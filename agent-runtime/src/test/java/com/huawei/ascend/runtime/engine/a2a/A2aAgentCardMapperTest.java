package com.huawei.ascend.runtime.engine.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.AgentCapabilitiesDescriptor;
import com.huawei.ascend.runtime.engine.spi.AgentCardDescriptor;
import com.huawei.ascend.runtime.engine.spi.AgentInterfaceDescriptor;
import com.huawei.ascend.runtime.engine.spi.AgentSkillDescriptor;
import com.huawei.ascend.runtime.engine.spi.SecuritySchemeDescriptor;
import java.util.List;
import java.util.Map;
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
    void defaultDescriptorMatchesLegacyCreateOutput() {
        // The default descriptor must produce a card identical to the old AgentCards.create(name, desc).
        AgentCardDescriptor descriptor = AgentCardDescriptor.of("sample-agent", "agent-runtime");

        AgentCard fromDescriptor = A2aAgentCardMapper.toAgentCard(descriptor);
        AgentCard fromLegacy = AgentCards.create("sample-agent", "agent-runtime");

        assertThat(fromDescriptor.name()).isEqualTo(fromLegacy.name());
        assertThat(fromDescriptor.description()).isEqualTo(fromLegacy.description());
        assertThat(fromDescriptor.version()).isEqualTo(fromLegacy.version());
        assertThat(fromDescriptor.url()).isEqualTo(fromLegacy.url());
        assertThat(fromDescriptor.provider().organization()).isEqualTo(fromLegacy.provider().organization());
        assertThat(fromDescriptor.provider().url()).isEqualTo(fromLegacy.provider().url());
        assertThat(fromDescriptor.capabilities().streaming()).isEqualTo(fromLegacy.capabilities().streaming());
        assertThat(fromDescriptor.capabilities().pushNotifications())
                .isEqualTo(fromLegacy.capabilities().pushNotifications());
        assertThat(fromDescriptor.capabilities().extendedAgentCard())
                .isEqualTo(fromLegacy.capabilities().extendedAgentCard());
        assertThat(fromDescriptor.defaultInputModes()).isEqualTo(fromLegacy.defaultInputModes());
        assertThat(fromDescriptor.defaultOutputModes()).isEqualTo(fromLegacy.defaultOutputModes());
        assertThat(fromDescriptor.skills()).isEqualTo(fromLegacy.skills());
        assertThat(fromDescriptor.preferredTransport()).isEqualTo(fromLegacy.preferredTransport());
        assertThat(fromDescriptor.supportedInterfaces()).hasSize(fromLegacy.supportedInterfaces().size());
        assertThat(fromDescriptor.supportedInterfaces().get(0).url())
                .isEqualTo(fromLegacy.supportedInterfaces().get(0).url());
        assertThat(fromDescriptor.supportedInterfaces().get(0).protocolBinding())
                .isEqualTo(fromLegacy.supportedInterfaces().get(0).protocolBinding());
    }

    @Test
    void mappedCardHasDefaultCapabilities() {
        AgentCard card = A2aAgentCardMapper.toAgentCard(AgentCardDescriptor.of("a", "b"));

        assertThat(card.capabilities().streaming()).isTrue();
        assertThat(card.capabilities().pushNotifications()).isTrue();
        assertThat(card.capabilities().extendedAgentCard()).isFalse();
    }

    @Test
    void mappedCardHasDefaultVersion() {
        AgentCard card = A2aAgentCardMapper.toAgentCard(AgentCardDescriptor.of("a", "b"));

        assertThat(card.version()).isEqualTo("0.1.0");
    }

    @Test
    void mappedCardHasDefaultOutputModes() {
        AgentCard card = A2aAgentCardMapper.toAgentCard(AgentCardDescriptor.of("a", "b"));

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
}
