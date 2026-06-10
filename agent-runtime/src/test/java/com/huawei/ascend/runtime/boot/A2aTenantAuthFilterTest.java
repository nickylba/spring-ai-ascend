package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class A2aTenantAuthFilterTest {

    private static final String SECRET = "test-secret-which-is-long-enough";

    private final A2aTenantAuthFilter filter = new A2aTenantAuthFilter(accessProperties());

    @Test
    void missingBearerTokenIsRejected401() throws Exception {
        MockHttpServletRequest request = a2aRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("missing bearer token");
    }

    @Test
    void badSignatureIsRejected401() throws Exception {
        MockHttpServletRequest request = a2aRequest();
        String token = jwt("bank-7", Instant.now().plusSeconds(300).getEpochSecond(), "wrong-secret");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("signature");
    }

    @Test
    void expiredTokenIsRejected401() throws Exception {
        MockHttpServletRequest request = a2aRequest();
        String token = jwt("bank-7", Instant.now().minusSeconds(600).getEpochSecond(), SECRET);
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("expired");
    }

    /** The ADR-0040 cross-check: header and claim must agree when both are present. */
    @Test
    void headerClaimMismatchIsRejected403() throws Exception {
        MockHttpServletRequest request = a2aRequest();
        request.addHeader("X-Tenant-Id", "bank-OTHER");
        String token = jwt("bank-7", Instant.now().plusSeconds(300).getEpochSecond(), SECRET);
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("does not match");
    }

    @Test
    void validTokenPublishesAuthenticatedTenantAndProceeds() throws Exception {
        MockHttpServletRequest request = a2aRequest();
        request.addHeader("X-Tenant-Id", "bank-7");
        String token = jwt("bank-7", Instant.now().plusSeconds(300).getEpochSecond(), SECRET);
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(A2aTenantAuthFilter.AUTHENTICATED_TENANT_ATTRIBUTE))
                .isEqualTo("bank-7");
    }

    /** Without the header the claim alone authenticates the tenant. */
    @Test
    void validTokenWithoutHeaderProceedsWithClaimTenant() throws Exception {
        MockHttpServletRequest request = a2aRequest();
        String token = jwt("bank-9", Instant.now().plusSeconds(300).getEpochSecond(), SECRET);
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(A2aTenantAuthFilter.AUTHENTICATED_TENANT_ATTRIBUTE))
                .isEqualTo("bank-9");
    }

    /** alg confusion (e.g. none) must be rejected before any verification logic. */
    @Test
    void nonHs256AlgorithmIsRejected() throws Exception {
        MockHttpServletRequest request = a2aRequest();
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"tenant_id\":\"bank-7\"}");
        request.addHeader("Authorization", "Bearer " + header + "." + payload + ".AAAA");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("unsupported jwt algorithm");
    }

    @Test
    void nonA2aPathsAreNotFiltered() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/.well-known/agent-card.json");
        request.setRequestURI("/.well-known/agent-card.json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest a2aRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/a2a");
        request.setRequestURI("/a2a");
        return request;
    }

    private static RuntimeAccessProperties accessProperties() {
        RuntimeAccessProperties access = new RuntimeAccessProperties();
        access.getJwt().setEnabled(true);
        access.getJwt().setHmacSecret(SECRET);
        return access;
    }

    static String jwt(String tenantId, long exp, String secret) throws Exception {
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"tenant_id\":\"" + tenantId + "\",\"exp\":" + exp + "}");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
