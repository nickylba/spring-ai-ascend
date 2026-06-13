package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TenantContractTest {

    @Test
    void tenantStateKeyIsCanonicalValue() {
        assertThat(TenantContract.TENANT_STATE_KEY).isEqualTo("tenantId");
    }

    @Test
    void defaultTenantIdIsCanonicalValue() {
        assertThat(TenantContract.DEFAULT_TENANT_ID).isEqualTo("default");
    }
}
