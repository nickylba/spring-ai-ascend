package com.huawei.ascend.runtime.engine.spi;

/**
 * Protocol-neutral descriptor for one security scheme entry in an agent card.
 *
 * <p>Covers the common cases: apiKey, http (Bearer/Basic), oauth2, openIdConnect,
 * and mutualTLS. The A2A mapper in {@code engine.a2a} selects the correct
 * {@code SecurityScheme} subtype based on {@code type}. Carries zero
 * {@code org.a2aproject} imports.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code type} — one of {@code "apiKey"}, {@code "http"}, {@code "oauth2"},
 *       {@code "openIdConnect"}, {@code "mutualTLS"}</li>
 *   <li>{@code location} — for {@code apiKey}: {@code "header"}, {@code "query"},
 *       or {@code "cookie"}</li>
 *   <li>{@code name} — for {@code apiKey}: the header/query-parameter name;
 *       for {@code http}: ignored</li>
 *   <li>{@code scheme} — for {@code http}: e.g. {@code "bearer"} or {@code "basic"}</li>
 *   <li>{@code description} — optional human-readable note</li>
 * </ul>
 */
public record SecuritySchemeDescriptor(
        String type,
        String location,
        String name,
        String scheme,
        String description) {

    /** Convenience factory for an API-key scheme. */
    public static SecuritySchemeDescriptor apiKey(String location, String name, String description) {
        return new SecuritySchemeDescriptor("apiKey", location, name, null, description);
    }

    /** Convenience factory for an HTTP auth scheme (e.g. bearer). */
    public static SecuritySchemeDescriptor http(String scheme, String description) {
        return new SecuritySchemeDescriptor("http", null, null, scheme, description);
    }
}
