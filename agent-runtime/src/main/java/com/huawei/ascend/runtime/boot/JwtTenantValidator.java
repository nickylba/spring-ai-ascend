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
 * Minimal HS256 JWT validation for the W1 tenant cross-check (ADR-0040):
 * verifies the signature against a shared secret, rejects expired tokens,
 * and extracts the {@code tenant_id} claim. Deliberately JDK+Jackson only —
 * asymmetric algorithms arrive with the platform key infrastructure.
 */
final class JwtTenantValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long clockSkewSeconds;

    JwtTenantValidator(String hmacSecret, long clockSkewSeconds) {
        if (hmacSecret == null || hmacSecret.isBlank()) {
            throw new IllegalArgumentException("jwt hmac secret must not be blank when jwt auth is enabled");
        }
        this.secret = hmacSecret.getBytes(StandardCharsets.UTF_8);
        this.clockSkewSeconds = clockSkewSeconds;
    }

    /** The validated tenant_id claim. */
    record ValidatedToken(String tenantId) {
    }

    /** Thrown for every rejection; the message is safe to surface to the client. */
    static final class InvalidTokenException extends RuntimeException {
        InvalidTokenException(String message) {
            super(message);
        }
    }

    ValidatedToken validate(String token) {
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
