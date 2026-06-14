package com.huawei.ascend.bus.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Design-level contract harness for {@code ICD-Agent-Registry-Discovery}
 * (Stage 3, slices 1-3).
 *
 * <p>The registry/discovery surface is contract-only in Stage 3 — there is no
 * runtime registry class, no broker binding, no Task-state ownership change.
 * This harness pins the design invariants the ICD encodes, so a future edit
 * that silently weakens them (dropping the tenantId requirement, leaking Task
 * state into discovery results, admitting a runtime registry class) fails the
 * build.
 *
 * <p>Authority: {@code docs/architecture/l0/05-contracts/human-readable/
 * ICD-agent-registry-discovery.md} (HD3-001..007); Stage 3 boundary.
 *
 * <p>The document assertions read the ICD and the L1 README as plain text and
 * anchor on stable phrases. The Stage 3 boundary assertion uses ArchUnit to
 * prove no production package under {@code com.huawei.ascend.bus.spi} is named
 * {@code registry} or {@code discovery} — the trip-wire that forces an explicit
 * decision when Stage 4 admits a registry SPI (HD3-007).
 */
class AgentBusRegistryDiscoveryDesignContractTest {

    private static final Path ICD = Path.of(
            "../docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md");
    private static final Path L1_README = Path.of("../architecture/docs/L1/agent-bus/README.md");

    private static String icdText;
    private static String readmeText;

    @BeforeAll
    static void readDesignSources() throws Exception {
        assertThat(ICD)
                .as("ICD-Agent-Registry-Discovery must be reachable from the surefire working "
                  + "directory (agent-bus module basedir)")
                .exists();
        icdText = Files.readString(ICD);

        assertThat(L1_README)
                .as("agent-bus L1 README must be reachable from the surefire working directory")
                .exists();
        readmeText = Files.readString(L1_README);
    }

    // ---- slice 3: ICD presence + L1 linkage (meta) ------------------------

    @Test
    void registry_discovery_icd_exists() {
        assertThat(icdText)
                .as("ICD-Agent-Registry-Discovery must declare its ICD ID header")
                .contains("# ICD-Agent-Registry-Discovery");
    }

    @Test
    void l1_readme_links_registry_discovery_icd() {
        assertThat(readmeText)
                .as("agent-bus L1 README must link the registry/discovery ICD (slice 3 back-link)")
                .contains("ICD-agent-registry-discovery.md");
    }

    // ---- ICD contract test 1-2: tenantId is mandatory on both ends --------

    @Test
    void registry_entry_requires_tenant_id() {
        assertThat(icdText)
                .as("ICD must declare a Registry Entry Required Fields block (HD3-002)")
                .contains("Registry Entry Required Fields")
                .as("ICD must state tenantId is a mandatory part of the registry key (HD3-003)")
                .contains("registry key 必须包含");
    }

    @Test
    void discovery_query_requires_tenant_id() {
        assertThat(icdText)
                .as("ICD must declare a Discovery Query block")
                .contains("Discovery Query")
                .as("ICD must state the discovery query must carry tenantId (HD3-003)")
                .contains("query 必须携带");
    }

    // ---- ICD contract test 3: discovery result excludes Task state --------

    @Test
    void discovery_result_has_no_task_execution_state() {
        assertThat(icdText)
                .as("ICD must forbid Task execution state in discovery results (HD3-001)")
                .contains("不得携带 Task execution state");
    }

    // ---- ICD contract test 4-5: health & version expressed explicitly -----

    @Test
    void unhealthy_target_has_explicit_health_state() {
        assertThat(icdText)
                .as("ICD must express unhealthy targets with an explicit health failure mode")
                .contains("health_unavailable")
                .as("ICD must require unhealthy targets to remain visible with explicit health")
                .contains("显式标注 health");
    }

    @Test
    void version_mismatch_has_explicit_result() {
        assertThat(icdText)
                .as("ICD must express version mismatch as an explicit failure mode (HD3-005)")
                .contains("version_unavailable")
                .contains("version mismatch");
    }

    // ---- ICD contract test 6: cross-tenant isolation ----------------------

    @Test
    void cross_tenant_query_rejected() {
        assertThat(icdText)
                .as("ICD must name a cross-tenant isolation failure mode (HD3-003)")
                .contains("tenant_isolation_violation")
                .as("ICD must forbid cross-tenant fallback")
                .contains("禁止跨 tenant fallback");
    }

    // ---- ICD contract test 7: no runtime registry in Stage 3 (ArchUnit) --

    @Test
    void no_runtime_registry_production_class_added_in_stage3() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.huawei.ascend.bus.spi");
        assertThat(classes)
                .as("sanity — ArchUnit must import agent-bus SPI classes from the test classpath")
                .isNotEmpty();

        Set<String> registryDiscoveryPackages = classes.stream()
                .map(JavaClass::getPackageName)
                .filter(p -> p.contains(".registry") || p.contains(".discovery"))
                .collect(Collectors.toSet());
        assertThat(registryDiscoveryPackages)
                .as("Stage 3 boundary (HD3-007): no runtime registry/discovery production package "
                  + "may exist under com.huawei.ascend.bus.spi yet — ICD-Agent-Registry-Discovery "
                  + "is design-level only. When Stage 4 admits a registry SPI, this assertion is "
                  + "the trip-wire that forces an explicit decision.")
                .isEmpty();
    }
}
