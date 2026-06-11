package com.huawei.ascend.examples.a2a.gateway.config;

import com.huawei.ascend.examples.a2a.gateway.api.AgentInteractionTelemetry;
import com.huawei.ascend.examples.a2a.gateway.core.InMemoryAgentInteractionTelemetry;
import com.huawei.ascend.examples.a2a.gateway.http.A2aGatewayController;
import com.huawei.ascend.examples.a2a.gateway.http.GatewayHealthController;
import com.huawei.ascend.examples.a2a.gateway.http.RouteGrantController;
import com.huawei.ascend.examples.a2a.gateway.http.RuntimeRegistryController;
import com.huawei.ascend.examples.a2a.gateway.http.TelemetryController;
import com.huawei.ascend.service.core.HmacRouteGrantService;
import com.huawei.ascend.service.core.InMemoryRuntimeRegistry;
import com.huawei.ascend.service.core.RuntimeA2aGateway;
import com.huawei.ascend.service.spi.discovery.AgentDirectory;
import com.huawei.ascend.service.spi.registry.RuntimeRegistry;
import com.huawei.ascend.service.spi.routing.RouteGrantService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RuntimeRegistryConfiguration {

    @Bean
    @ConditionalOnMissingBean
    InMemoryRuntimeRegistry inMemoryRuntimeRegistry() {
        return new InMemoryRuntimeRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(RuntimeRegistry.class)
    RuntimeRegistry runtimeRegistry(InMemoryRuntimeRegistry registry) {
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean(AgentDirectory.class)
    AgentDirectory agentDirectory(InMemoryRuntimeRegistry registry) {
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    RuntimeA2aGateway runtimeA2aGateway(AgentDirectory directory) {
        return new RuntimeA2aGateway(directory);
    }

    @Bean
    @ConditionalOnMissingBean
    RouteGrantService routeGrantService(
            AgentDirectory directory,
            @Value("${sample.gateway.route-grant-secret:${SAA_SAMPLE_GATEWAY_ROUTE_GRANT_SECRET:agent-examples-local-route-grant-secret}}") String secret) {
        return new HmacRouteGrantService(directory, secret);
    }

    @Bean
    @ConditionalOnMissingBean
    AgentInteractionTelemetry agentInteractionTelemetry() {
        return new InMemoryAgentInteractionTelemetry();
    }

    @Bean
    @ConditionalOnMissingBean
    RuntimeRegistryController runtimeRegistryController(
            RuntimeRegistry runtimeRegistry,
            AgentDirectory directory) {
        return new RuntimeRegistryController(runtimeRegistry, directory);
    }

    @Bean
    @ConditionalOnMissingBean
    A2aGatewayController a2aGatewayController(
            RuntimeA2aGateway gateway,
            RouteGrantService routeGrantService,
            AgentInteractionTelemetry telemetry) {
        return new A2aGatewayController(gateway, routeGrantService, telemetry);
    }

    @Bean
    @ConditionalOnMissingBean
    RouteGrantController routeGrantController(RouteGrantService routeGrantService) {
        return new RouteGrantController(routeGrantService);
    }

    @Bean
    @ConditionalOnMissingBean
    TelemetryController telemetryController(AgentInteractionTelemetry telemetry) {
        return new TelemetryController(telemetry);
    }

    @Bean
    @ConditionalOnMissingBean
    GatewayHealthController gatewayHealthController(
            InMemoryRuntimeRegistry registry,
            AgentInteractionTelemetry telemetry) {
        return new GatewayHealthController(registry, telemetry);
    }
}
