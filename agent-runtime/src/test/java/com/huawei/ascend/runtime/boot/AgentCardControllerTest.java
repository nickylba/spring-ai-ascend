package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.a2a.AgentCards;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Legacy_0_3_AgentInterface;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Covers the discovery endpoint's URL publication contract: a configured
 * public-base-url (which may carry a proxy path prefix) wins over the
 * request-derived base, and every published URL — card url, supported
 * interfaces, additional interfaces, AND provider — is rewritten from the
 * same base so the card never leaks a hardcoded or internal address.
 */
class AgentCardControllerTest {

    private static AgentCardController controller(String publicBaseUrl) {
        RuntimeAccessProperties access = new RuntimeAccessProperties();
        access.setPublicBaseUrl(publicBaseUrl);
        return new AgentCardController(AgentCards.create("sample-agent", "agent-runtime"), access);
    }

    private static MockHttpServletRequest request(String scheme, String host, int port) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme(scheme);
        request.setServerName(host);
        request.setServerPort(port);
        return request;
    }

    @Test
    void publicBaseUrlWithPathPrefixWinsOverTheRequest() {
        AgentCardController controller = controller("https://agents.example.com/runtime-one");

        ResponseEntity<AgentCard> response = controller.agentCard(
                request("http", "internal-pod", 8080), null);
        AgentCard card = response.getBody();

        assertThat(card.url()).isEqualTo("https://agents.example.com/runtime-one/a2a");
        assertThat(card.provider().url()).isEqualTo("https://agents.example.com/runtime-one");
        assertThat(card.supportedInterfaces())
                .extracting(AgentInterface::url)
                .containsExactly("https://agents.example.com/runtime-one/a2a");
        assertThat(cardUrls(card)).noneMatch(url -> url.contains("localhost:8080"));
    }

    @Test
    void publicBaseUrlTrailingSlashDoesNotDoubleTheSeparator() {
        AgentCardController controller = controller("https://agents.example.com/runtime-one/");

        ResponseEntity<AgentCard> response = controller.agentCard(
                request("http", "localhost", 8080), null);
        AgentCard card = response.getBody();

        assertThat(card.url()).isEqualTo("https://agents.example.com/runtime-one/a2a");
        assertThat(card.provider().url()).isEqualTo("https://agents.example.com/runtime-one");
    }

    @Test
    void blankPublicBaseUrlFallsBackToTheRequestDerivedBase() {
        AgentCardController controller = controller(null);

        ResponseEntity<AgentCard> response = controller.agentCard(
                request("http", "edge.internal", 9090), null);
        AgentCard card = response.getBody();

        assertThat(card.url()).isEqualTo("http://edge.internal:9090/a2a");
        assertThat(card.provider().url()).isEqualTo("http://edge.internal:9090");
        assertThat(card.supportedInterfaces())
                .extracting(AgentInterface::url)
                .containsExactly("http://edge.internal:9090/a2a");
        assertThat(cardUrls(card)).noneMatch(url -> url.contains("localhost:8080"));
    }

    @Test
    void requestDerivedBaseHidesDefaultHttpsPort() {
        AgentCardController controller = controller("");

        ResponseEntity<AgentCard> response = controller.agentCard(
                request("https", "agents.example.com", 443), null);
        AgentCard card = response.getBody();

        assertThat(card.url()).isEqualTo("https://agents.example.com/a2a");
        assertThat(card.provider().url()).isEqualTo("https://agents.example.com");
    }

    @Test
    void absoluteCardUrlsAreLeftUntouched() {
        RuntimeAccessProperties access = new RuntimeAccessProperties();
        access.setPublicBaseUrl("https://agents.example.com/runtime-one");
        AgentCard absolute = AgentCard.builder(AgentCards.create("sample-agent", "agent-runtime"))
                .url("https://elsewhere.example.com/a2a")
                .build();
        AgentCardController controller = new AgentCardController(absolute, access);

        ResponseEntity<AgentCard> response = controller.agentCard(
                request("http", "localhost", 8080), null);
        AgentCard card = response.getBody();

        assertThat(card.url()).isEqualTo("https://elsewhere.example.com/a2a");
    }

    // --- additionalInterfaces URL rewrite ---

    @Test
    void additionalInterfacesUrlIsRewrittenToResolvedBase() {
        RuntimeAccessProperties access = new RuntimeAccessProperties();
        access.setPublicBaseUrl("https://agents.example.com/runtime-one");
        AgentCard cardWithAdditional = AgentCard.builder(AgentCards.create("sample-agent", "agent-runtime"))
                .additionalInterfaces(java.util.List.of(
                        new Legacy_0_3_AgentInterface("grpc", "/a2a/grpc")))
                .build();
        AgentCardController controller = new AgentCardController(cardWithAdditional, access);

        ResponseEntity<AgentCard> response = controller.agentCard(
                request("http", "internal-pod", 8080), null);
        AgentCard card = response.getBody();

        assertThat(card.additionalInterfaces()).hasSize(1);
        assertThat(card.additionalInterfaces().get(0).url())
                .isEqualTo("https://agents.example.com/runtime-one/a2a/grpc");
        assertThat(card.additionalInterfaces().get(0).transport()).isEqualTo("grpc");
    }

    @Test
    void additionalInterfacesAbsoluteUrlIsLeftUntouched() {
        RuntimeAccessProperties access = new RuntimeAccessProperties();
        access.setPublicBaseUrl("https://agents.example.com/runtime-one");
        AgentCard cardWithAdditional = AgentCard.builder(AgentCards.create("sample-agent", "agent-runtime"))
                .additionalInterfaces(java.util.List.of(
                        new Legacy_0_3_AgentInterface("grpc", "https://other.example.com/grpc")))
                .build();
        AgentCardController controller = new AgentCardController(cardWithAdditional, access);

        ResponseEntity<AgentCard> response = controller.agentCard(
                request("http", "internal-pod", 8080), null);
        AgentCard card = response.getBody();

        assertThat(card.additionalInterfaces().get(0).url())
                .isEqualTo("https://other.example.com/grpc");
    }

    // --- ETag + Cache-Control + 304 ---

    @Test
    void cacheControlHeaderIsPresentOnCanonicalEndpoint() {
        AgentCardController controller = controller("https://agents.example.com");

        ResponseEntity<AgentCard> response = controller.agentCard(
                request("http", "localhost", 8080), null);

        assertThat(response.getHeaders().getFirst(org.springframework.http.HttpHeaders.CACHE_CONTROL))
                .isEqualTo("public, max-age=60");
    }

    @Test
    void etagIsPresentAndStableForSameContent() {
        AgentCardController controller = controller("https://agents.example.com");
        MockHttpServletRequest req = request("http", "localhost", 8080);

        String etag1 = controller.agentCard(req, null)
                .getHeaders().getFirst(HttpHeaders.ETAG);
        String etag2 = controller.agentCard(req, null)
                .getHeaders().getFirst(HttpHeaders.ETAG);

        assertThat(etag1).isNotNull().startsWith("\"").endsWith("\"");
        assertThat(etag1).isEqualTo(etag2);
    }

    @Test
    void matchingIfNoneMatchReturns304WithNoBody() {
        AgentCardController controller = controller("https://agents.example.com");
        MockHttpServletRequest req = request("http", "localhost", 8080);

        String etag = controller.agentCard(req, null).getHeaders().getFirst(HttpHeaders.ETAG);
        ResponseEntity<AgentCard> response = controller.agentCard(req, etag);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("public, max-age=60");
    }

    @Test
    void staleIfNoneMatchReturns200WithBody() {
        AgentCardController controller = controller("https://agents.example.com");
        MockHttpServletRequest req = request("http", "localhost", 8080);

        ResponseEntity<AgentCard> response = controller.agentCard(req, "\"stale-etag\"");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    // --- legacy canonical Link header ---

    @Test
    void legacyEndpointHasCanonicalLinkHeader() {
        AgentCardController controller = controller("https://agents.example.com");

        ResponseEntity<AgentCard> response = controller.agentCardLegacy(
                request("http", "localhost", 8080), null);

        assertThat(response.getHeaders().getFirst("Link"))
                .isEqualTo("</.well-known/agent-card.json>; rel=\"canonical\"");
    }

    @Test
    void canonicalEndpointDoesNotHaveLinkHeader() {
        AgentCardController controller = controller("https://agents.example.com");

        ResponseEntity<AgentCard> response = controller.agentCard(
                request("http", "localhost", 8080), null);

        assertThat(response.getHeaders().get("Link")).isNullOrEmpty();
    }

    @Test
    void legacyEndpointAlsoHasCacheControlAndEtag() {
        AgentCardController controller = controller("https://agents.example.com");

        ResponseEntity<AgentCard> response = controller.agentCardLegacy(
                request("http", "localhost", 8080), null);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("public, max-age=60");
        assertThat(response.getHeaders().getFirst(HttpHeaders.ETAG)).isNotNull();
    }

    private static java.util.List<String> cardUrls(AgentCard card) {
        java.util.List<String> urls = new java.util.ArrayList<>();
        urls.add(card.url());
        urls.add(card.provider().url());
        card.supportedInterfaces().forEach(i -> urls.add(i.url()));
        return urls;
    }
}
