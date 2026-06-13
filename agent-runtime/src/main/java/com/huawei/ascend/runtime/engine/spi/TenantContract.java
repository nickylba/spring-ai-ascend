package com.huawei.ascend.runtime.engine.spi;

/**
 * Single-source constants for the tenant call-context: the state-map key under
 * which the access layer publishes the resolved tenant, and the sentinel value
 * used when no tenant is provided by the caller or the deployment configuration.
 *
 * <p>All code that reads or writes the tenant key (the A2A executor, the access
 * controller, framework adapters, and their tests) must reference these
 * constants so a rename propagates from one place.
 */
public final class TenantContract {

    /** Call-context state-map key that carries the resolved tenant identifier. */
    public static final String TENANT_STATE_KEY = "tenantId";

    /**
     * Fallback tenant value used when neither the request nor the deployment
     * configuration supplies one; single-tenant deployments rely on this default.
     */
    public static final String DEFAULT_TENANT_ID = "default";

    private TenantContract() {
        // non-instantiable contract holder
    }
}
