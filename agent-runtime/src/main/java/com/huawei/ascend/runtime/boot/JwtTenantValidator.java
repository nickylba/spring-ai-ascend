package com.huawei.ascend.runtime.boot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The platform's JWT tenant cross-check primitive (ADR-0040): minimal HS256
 * validation that verifies the signature against a shared secret, rejects
 * expired tokens, and extracts the {@code tenant_id} claim. Reused by every
 * tenant-attributing edge — the runtime's A2A filter here in boot and the
 * serviceization (agent-service) ingress edges — so the cross-check semantics
 * stay pinned by one validator and one set of security tests.
 *
 * <p>HS256 shared-secret validation is the recorded TRANSITION path
 * (Authority: ADR-0164): dev/local and single-operator deployments, no key
 * infrastructure required. The production trajectory is OIDC/JWKS through
 * Spring Security's {@code JwtDecoder} with the platform keeping only the
 * tenant cross-check; it slots in behind the same two-method surface when the
 * key-infrastructure wave lands. Deliberately JDK+Jackson only until then —
 * a hard Nimbus dependency would ride into every facade deployment.
 */
public final class JwtTenantValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long clockSkewSeconds;

    public JwtTenantValidator(String hmacSecret, long clockSkewSeconds) {
        if (hmacSecret == null || hmacSecret.isBlank()) {
            throw new IllegalArgumentException("jwt hmac secret must not be blank when jwt auth is enabled");
        }
        this.secret = hmacSecret.getBytes(StandardCharsets.UTF_8);
        this.clockSkewSeconds = clockSkewSeconds;
    }

    /** The validated tenant_id claim. */
    public record ValidatedToken(String tenantId) {
    }

    /** Thrown for every rejection; the message is safe to surface to the client. */
    public static final class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }

    public ValidatedToken validate(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new InvalidTokenException("token is not a JWS compact serialization");
        }
        JsonNode header = json(parts[0], "header");
        String algorithm = header.path("alg").asText("");
        if (!"HS256".equals(algorithm)) {
            // An attacker-chosen alg (none / HS384 / RS256 confusion) must never reach verification.
            throw new InvalidTokenException("unsupported jwt algorithm: " + algorithm.toLowerCase(Locale.ROOT));
        }
        verifySignature(parts[0] + "." + parts[1], parts[2]);
        JsonNode claims = json(parts[1], "claims");
        long exp = claims.path("exp").asLong(0);
        if (exp > 0 && Instant.now().getEpochSecond() > exp + clockSkewSeconds) {
            throw new InvalidTokenException("token is expired");
        }
        String tenantId = claims.path("tenant_id").asText("");
        if (tenantId.isBlank()) {
            throw new InvalidTokenException("token carries no tenant_id claim");
        }
        return new ValidatedToken(tenantId);
    }

    private void verifySignature(String signedContent, String encodedSignature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] expected = mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8));
            byte[] provided = URL_DECODER.decode(encodedSignature);
            if (!MessageDigest.isEqual(expected, provided)) {
                throw new InvalidTokenException("token signature does not verify");
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("token signature is not valid base64url");
        } catch (java.security.GeneralSecurityException e) {
            throw new InvalidTokenException("signature verification unavailable: " + e.getMessage());
        }
    }

    private static JsonNode json(String encodedSegment, String segmentName) {
        try {
            return MAPPER.readTree(URL_DECODER.decode(encodedSegment));
        } catch (Exception e) {
            throw new InvalidTokenException("token " + segmentName + " is not valid base64url JSON");
        }
    }
}
