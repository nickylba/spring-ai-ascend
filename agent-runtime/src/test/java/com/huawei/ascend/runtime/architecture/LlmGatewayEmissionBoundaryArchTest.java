package com.huawei.ascend.runtime.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Emission boundary of the LLM egress gateway: the upstream-forwarding client is
 * gateway-internal, and GENERATION records flow only through the gateway and its
 * SPI. A class elsewhere forwarding to providers or writing GENERATION records
 * would bypass the listener chain — the sole telemetry emission path — and break
 * tenant/cost attribution.
 */
class LlmGatewayEmissionBoundaryArchTest {

    private static final String GATEWAY_PACKAGE = "com.huawei.ascend.runtime.llm.gateway";
    private static final String SPI_PACKAGE = "com.huawei.ascend.runtime.llm.gateway.spi";

    private static final JavaClasses RUNTIME_MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.huawei.ascend.runtime");

    @Test
    void onlyTheGatewayPackageMayDependOnTheUpstreamClient() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage(GATEWAY_PACKAGE)
                .should().dependOnClassesThat()
                .haveNameMatching("com\\.huawei\\.ascend\\.runtime\\.llm\\.gateway\\."
                        + "(RestClient)?UpstreamModelClient(\\$.*)?");
        rule.check(RUNTIME_MAIN_CLASSES);
    }

    @Test
    void onlyGatewayAndSpiPackagesMayDependOnTheGenerationSpanSink() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages(GATEWAY_PACKAGE, SPI_PACKAGE)
                .should().dependOnClassesThat()
                .haveNameMatching("com\\.huawei\\.ascend\\.runtime\\.llm\\.gateway\\.spi\\."
                        + ".*GenerationSpanSink(\\$.*)?");
        rule.check(RUNTIME_MAIN_CLASSES);
    }
}
