package com.huawei.ascend.service.engine.config;

import com.huawei.ascend.service.engine.api.DefaultEngineDispatchApi;
import com.huawei.ascend.service.engine.api.EngineDispatchApi;
import com.huawei.ascend.service.engine.command.EngineCommandEventFactory;
import com.huawei.ascend.service.engine.command.EngineCommandGateway;
import com.huawei.ascend.service.engine.command.EngineCommandProcessor;
import com.huawei.ascend.service.engine.command.InternalEngineCommandGateway;
import com.huawei.ascend.service.engine.dispatch.AgentHandlerRegistry;
import com.huawei.ascend.service.engine.dispatch.DefaultAgentHandlerRegistry;
import com.huawei.ascend.service.engine.dispatch.EngineDispatcher;
import com.huawei.ascend.service.engine.port.AccessLayerClient;
import com.huawei.ascend.service.engine.port.TaskControlClient;
import com.huawei.ascend.service.queue.QueueManager;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the engine's core collaborators as beans (design §15). The dispatcher
 * and subscriber are only created once the outbound clients
 * ({@link TaskControlClient}, {@link AccessLayerClient}) are available — those
 * are provided by the task-control and access-layer modules, not the engine
 * itself, so the engine staying dormant when they are absent is intentional.
 */
@Configuration
@EnableConfigurationProperties(EngineProperties.class)
@ConditionalOnProperty(prefix = "agent-service.engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentHandlerRegistry agentHandlerRegistry(
            org.springframework.beans.factory.ObjectProvider<com.huawei.ascend.service.engine.spi.AgentHandler> handlers) {
        DefaultAgentHandlerRegistry registry = new DefaultAgentHandlerRegistry();
        // Auto-register every AgentHandler bean by its agentId so framework
        // integrators only need to publish a handler bean to plug in an agent.
        handlers.orderedStream().forEach(handler -> registry.register(handler.agentId(), handler));
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public EngineCommandGateway engineCommandGateway(QueueManager queueManager) {
        return new InternalEngineCommandGateway(queueManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public EngineCommandEventFactory engineCommandEventFactory() {
        return new EngineCommandEventFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public EngineDispatchApi engineDispatchApi(EngineCommandEventFactory commandEventFactory,
                                               EngineCommandGateway engineCommandGateway) {
        return new DefaultEngineDispatchApi(commandEventFactory, engineCommandGateway);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "engineExecutionExecutor")
    public ExecutorService engineExecutionExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({TaskControlClient.class, AccessLayerClient.class})
    public EngineDispatcher engineDispatcher(AgentHandlerRegistry registry,
                                             TaskControlClient taskControlClient,
                                             AccessLayerClient accessLayerClient) {
        return new EngineDispatcher(registry, taskControlClient, accessLayerClient);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnBean(EngineDispatcher.class)
    public EngineCommandProcessor engineCommandProcessor(
            EngineCommandGateway gateway,
            EngineDispatcher dispatcher,
            @Qualifier("engineExecutionExecutor") Executor engineExecutionExecutor) {
        return new EngineCommandProcessor(gateway, dispatcher, engineExecutionExecutor);
    }
}
