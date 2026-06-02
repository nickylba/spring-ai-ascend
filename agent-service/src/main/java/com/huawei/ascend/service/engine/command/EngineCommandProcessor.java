package com.huawei.ascend.service.engine.command;

import com.huawei.ascend.service.engine.dispatch.EngineDispatcher;
import com.huawei.ascend.service.engine.event.EngineCommandEvent;
import java.util.Objects;
import java.util.concurrent.Executor;
import reactor.core.Disposable;

/**
 * Consumes engine commands and offloads each agent execution to the engine
 * execution pool.
 */
public class EngineCommandProcessor {

    private final EngineCommandGateway commandGateway;
    private final EngineDispatcher dispatcher;
    private final Executor executor;
    private Disposable subscription;

    public EngineCommandProcessor(EngineCommandGateway commandGateway, EngineDispatcher dispatcher, Executor executor) {
        this.commandGateway = Objects.requireNonNull(commandGateway, "commandGateway");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public void start() {
        subscription = commandGateway.commands().subscribe(this::onCommand);
    }

    private void onCommand(EngineCommandEvent command) {
        executor.execute(() -> dispatcher.dispatch(command));
    }

    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }
}
