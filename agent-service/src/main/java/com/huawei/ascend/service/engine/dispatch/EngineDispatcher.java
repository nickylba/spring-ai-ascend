package com.huawei.ascend.service.engine.dispatch;

import com.huawei.ascend.service.engine.event.EngineAgentCallEvent;
import com.huawei.ascend.service.engine.event.EngineCancelledEvent;
import com.huawei.ascend.service.engine.event.EngineCommandEvent;
import com.huawei.ascend.service.engine.event.EngineCompletedEvent;
import com.huawei.ascend.service.engine.event.EngineExecutionEvent;
import com.huawei.ascend.service.engine.event.EngineFailedEvent;
import com.huawei.ascend.service.engine.event.EngineInterruptedEvent;
import com.huawei.ascend.service.engine.event.EngineOutputEvent;
import com.huawei.ascend.service.engine.event.EngineStartedEvent;
import com.huawei.ascend.service.engine.handler.AgentExecutionContext;
import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.model.InterruptType;
import com.huawei.ascend.service.engine.spi.AccessLayerClient;
import com.huawei.ascend.service.engine.spi.AgentHandler;
import com.huawei.ascend.service.engine.spi.TaskControlClient;
import java.util.stream.Stream;

/**
 * Pulls the {@code AgentHandler} for a command, runs it, and routes each emitted
 * execution event to the task-control and access-layer clients per the state and
 * output mapping in engine model design §13.
 */
public class EngineDispatcher {

    private final AgentHandlerRegistry registry;
    private final TaskControlClient taskControlClient;
    private final AccessLayerClient accessLayerClient;

    public EngineDispatcher(AgentHandlerRegistry registry, TaskControlClient taskControlClient, AccessLayerClient accessLayerClient) {
        this.registry = registry;
        this.taskControlClient = taskControlClient;
        this.accessLayerClient = accessLayerClient;
    }

    public void dispatch(EngineCommandEvent command) {
        AgentHandler handler = registry.findByAgentId(command.getScope().agentId());
        AgentExecutionContext context = new AgentExecutionContext(command.getScope(), command.getInput());
        try (Stream<EngineExecutionEvent> events = handler.execute(context)) {
            events.forEach(this::route);
        }
    }

    private void route(EngineExecutionEvent event) {
        EngineExecutionScope scope = event.getScope();
        if (event instanceof EngineStartedEvent) {
            taskControlClient.markRunning(scope);
        } else if (event instanceof EngineOutputEvent e) {
            accessLayerClient.appendOutput(scope, e);
        } else if (event instanceof EngineInterruptedEvent e) {
            taskControlClient.markWaiting(scope, e);
            if (e.getInterruptType() != InterruptType.WAITING_CHILD_AGENT) {
                accessLayerClient.requestUserInput(scope, e);
            }
        } else if (event instanceof EngineCompletedEvent e) {
            taskControlClient.markSucceeded(scope, e);
            accessLayerClient.completeOutput(scope, e);
        } else if (event instanceof EngineFailedEvent e) {
            taskControlClient.markFailed(scope, e);
            accessLayerClient.failOutput(scope, e);
        } else if (event instanceof EngineCancelledEvent e) {
            taskControlClient.markCancelled(scope, e);
        } else if (event instanceof EngineAgentCallEvent) {
            // Agent-to-agent routing is handled from Phase 3 onward.
            throw new UnsupportedOperationException("EngineAgentCallEvent routing not implemented in Phase 1");
        }
    }
}
