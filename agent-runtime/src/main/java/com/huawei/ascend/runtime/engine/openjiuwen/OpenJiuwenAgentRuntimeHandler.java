package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryDraft;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEmitter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.harness.rails.ExternalMemoryRail;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Set;
import java.util.Spliterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for openJiuwen {@link AgentRuntimeHandler} implementations. The
 * concrete handler owns how it builds its openJiuwen agent; this class owns the
 * runtime-facing execute flow, rail installation, input/result mapping, and
 * stable {@code conversation_id}. openJiuwen session persistence is delegated to
 * its native checkpointer mechanism.
 *
 * <p>The runtime always executes through openJiuwen's streaming runner and
 * maps standard {@link OutputSchema} chunks into the framework-neutral result
 * stream.
 */
public abstract class OpenJiuwenAgentRuntimeHandler extends AbstractAgentRuntimeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenJiuwenAgentRuntimeHandler.class);

    private final OpenJiuwenMessageAdapter messageConverter;
    private final OpenJiuwenStreamAdapter resultMapper;
    private OpenJiuwenRemoteToolInstaller runtimeToolInstaller;

    protected OpenJiuwenAgentRuntimeHandler(String agentId) {
        this(agentId, new OpenJiuwenMessageAdapter());
    }

    protected OpenJiuwenAgentRuntimeHandler(String agentId, OpenJiuwenMessageAdapter messageConverter) {
        this(agentId, messageConverter, new OpenJiuwenStreamAdapter());
    }

    OpenJiuwenAgentRuntimeHandler(String agentId, OpenJiuwenMessageAdapter messageConverter,
            OpenJiuwenStreamAdapter resultMapper) {
        super(agentId);
        this.messageConverter = Objects.requireNonNull(messageConverter, "messageConverter");
        this.resultMapper = Objects.requireNonNull(resultMapper, "resultMapper");
    }

    /**
     * openJiuwen taps native model-call callbacks (token usage, reasoning, finish reason) on top of
     * the cross-framework core, so it advertises the model-call kinds. Without this, the optional
     * tier would be dropped by the capability gate before the FULL-level gate is ever reached.
     */
    @Override
    protected Set<Kind> supportedKinds() {
        return EnumSet.of(
                Kind.RUN_START, Kind.RUN_END,
                Kind.MODEL_CALL_START, Kind.MODEL_CALL_END,
                Kind.TOOL_CALL_START, Kind.TOOL_CALL_END,
                Kind.ERROR);
    }

    @Override
    protected final java.util.stream.Stream<?> doExecute(AgentExecutionContext context,
            TrajectoryEmitter trajectory) {
        try {
            LOGGER.info("openjiuwen execute start tenantId={} sessionId={} taskId={} agentId={}",
                    context.getScope().tenantId(),
                    context.getScope().sessionId(),
                    context.getScope().taskId(),
                    context.getScope().agentId());
            BaseAgent agent = Objects.requireNonNull(createOpenJiuwenAgent(context), "openJiuwen agent");
            installRails(agent, context);
            installRuntimeTools(agent, context);
            if (trajectory != TrajectoryEmitter.NOOP) {
                agent.registerRail(new OpenJiuwenTrajectoryRail(trajectory));
            }
            Object input = toOpenJiuwenInput(context);
            Iterator<Object> result = runOpenJiuwenAgentStreaming(
                    agent, input, openJiuwenConversationId(context), openJiuwenStreamModes(context));
            LOGGER.info("openjiuwen execute finished tenantId={} sessionId={} taskId={} resultType={}",
                    context.getScope().tenantId(),
                    context.getScope().sessionId(),
                    context.getScope().taskId(),
                    result == null ? "null" : result.getClass().getName());
            if (result == null) {
                return java.util.stream.Stream.of((Object) null);
            }
            return java.util.stream.StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(result, Spliterator.ORDERED), false);
        } catch (RuntimeException error) {
            LOGGER.warn("openjiuwen execute failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                    context.getScope().tenantId(),
                    context.getScope().sessionId(),
                    context.getScope().taskId(),
                    error.getClass().getSimpleName(),
                    errorMessage(error));
            // ERROR is a mandatory trajectory kind: surface the run-level failure northbound even though
            // the failure is mapped to a result (not rethrown), so the trajectory is not silently truncated.
            trajectory.emit(TrajectoryDraft.error(null, "OPENJIUWEN_RUN_ERROR", errorMessage(error), null, false));
            return java.util.stream.Stream.of(AgentExecutionResult.failed("OPENJIUWEN_RUN_ERROR", errorMessage(error)));
        }
    }

    /** Build the concrete openJiuwen agent instance for this execution. */
    protected abstract BaseAgent createOpenJiuwenAgent(AgentExecutionContext context);

    /**
     * Adapter-owned rails installed on every openJiuwen agent before execution.
     *
     * <p>The default installs no rails. Subclasses can opt in to openJiuwen-local
     * decorations such as OpenJiuwen's external memory rail or the ReActAgent
     * compatibility {@link MemoryRuntimeRail} without changing A2A execution or
     * the framework-neutral runtime SPI.
     *
     * <p>Registration order is stable: rails returned here are registered first,
     * runtime-owned tools are installed next through
     * {@link #installRuntimeTools(BaseAgent, AgentExecutionContext)}, and the
     * runtime trajectory rail is registered last when trajectory capture is
     * enabled. A rail returned here should therefore not assume trajectory
     * callbacks have already been attached.
     *
     * <p>The default handler creates a fresh {@link BaseAgent} per execution. If
     * a subclass caches or reuses an agent instance, the returned rails must be
     * idempotent or the subclass must avoid duplicate registration.
     */
    protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
        return List.of();
    }

    /**
     * Install runtime-owned tools on the concrete openJiuwen agent instance.
     *
     * <p>The default is intentionally empty. Runtime integrations such as remote
     * A2A tool injection can use this hook without changing the concrete user's
     * agent implementation.
     */
    protected void installRuntimeTools(BaseAgent agent, AgentExecutionContext context) {
        if (runtimeToolInstaller != null) {
            runtimeToolInstaller.install(agent, context);
        }
    }

    public final void setRuntimeToolInstaller(OpenJiuwenRemoteToolInstaller runtimeToolInstaller) {
        this.runtimeToolInstaller = runtimeToolInstaller;
    }

    /**
     * Create the ReActAgent-compatible memory rail for subclasses that opt in.
     *
     * <p>Use {@link #openJiuwenExternalMemoryRail(AgentExecutionContext, MemoryProvider)}
     * first when the concrete OpenJiuwen agent supports the native harness
     * external-memory rail.
     */
    protected final AgentRail memoryRuntimeRail(AgentExecutionContext context, MemoryProvider memoryProvider) {
        return new MemoryRuntimeRail(context, memoryProvider, new OpenJiuwenMemoryMessageAdapter());
    }

    /**
     * Create an openJiuwen-native external memory rail backed by the runtime
     * neutral {@link MemoryProvider}.
     *
     * <p>Prefer this hook when the concrete openJiuwen agent supports the
     * harness external-memory rail. The OpenJiuwen memory API is intentionally
     * hidden behind an adapter in this package so the public runtime SPI remains
     * independent from OpenJiuwen memory package names.
     */
    protected final AgentRail openJiuwenExternalMemoryRail(AgentExecutionContext context, MemoryProvider memoryProvider) {
        return createExternalMemoryRail(
                new OpenJiuwenExternalMemoryProviderAdapter(context, memoryProvider),
                context.getScope().userId(),
                context.getAgentStateKey(),
                context.getScope().sessionId());
    }

    protected Iterator<Object> runOpenJiuwenAgentStreaming(BaseAgent agent, Object input, String conversationId,
            List<StreamMode> streamModes) {
        return Runner.runAgentStreaming(agent, input, conversationId, null, streamModes);
    }

    protected List<StreamMode> openJiuwenStreamModes(AgentExecutionContext context) {
        return List.of(StreamMode.OUTPUT);
    }

    /**
     * Returns the stable conversation id openJiuwen should use for native
     * checkpointer restore/save. Subclasses pass this value as the Runner
     * session id, or rely on {@link #toOpenJiuwenInput(AgentExecutionContext)}
     * to place it in {@code conversation_id}.
     */
    protected String openJiuwenConversationId(AgentExecutionContext context) {
        String conversationId = context.getAgentStateKey();
        LOGGER.info("openjiuwen conversation resolve tenantId={} sessionId={} taskId={} agentId={} conversationId={}",
                context.getScope().tenantId(),
                context.getScope().sessionId(),
                context.getScope().taskId(),
                context.getScope().agentId(),
                conversationId);
        return conversationId;
    }

    protected Object toOpenJiuwenInput(AgentExecutionContext context) {
        LOGGER.info("openjiuwen input convert tenantId={} sessionId={} taskId={} agentId={} inputType={} messages={}",
                context.getScope().tenantId(),
                context.getScope().sessionId(),
                context.getScope().taskId(),
                context.getScope().agentId(),
                context.getInputType(),
                context.getMessages().size());
        return messageConverter.toOpenJiuwenInput(context);
    }

    private void installRails(BaseAgent agent, AgentExecutionContext context) {
        for (AgentRail rail : openJiuwenRails(context)) {
            if (rail != null) {
                agent.registerRail(rail);
            }
        }
    }

    @Override
    public StreamAdapter resultAdapter() {
        return rawResults -> rawResults.map(this::mapRawResult).filter(Objects::nonNull);
    }

    private AgentExecutionResult mapRawResult(Object rawResult) {
        LOGGER.info("openjiuwen raw result received type={}",
                rawResult == null ? "null" : rawResult.getClass().getName());
        if (rawResult instanceof AgentExecutionResult result) {
            return result;
        }
        if (rawResult == null) {
            return AgentExecutionResult.failed(
                    OpenJiuwenStreamAdapter.ERROR_CODE, "openjiuwen runner returned no result");
        }
        if (rawResult instanceof OutputSchema chunk) {
            return resultMapper.map(chunk);
        }
        return AgentExecutionResult.output(String.valueOf(rawResult));
    }

    protected static String errorMessage(Throwable error) {
        StringBuilder message = new StringBuilder();
        Throwable cursor = error;
        while (cursor != null) {
            String part = cursor.getMessage();
            if (part != null && !part.isBlank()) {
                if (!message.isEmpty()) {
                    message.append(": ");
                }
                message.append(part);
            }
            cursor = cursor.getCause();
        }
        return message.isEmpty() ? error.getClass().getName() : message.toString();
    }

    private static AgentRail createExternalMemoryRail(
            com.openjiuwen.core.memory.external.MemoryProvider memoryProvider,
            String userId,
            String scopeId,
            String sessionId) {
        return ExternalMemoryRailHolder.create(memoryProvider, userId, scopeId, sessionId);
    }

    private static final class ExternalMemoryRailHolder {
        private static AgentRail create(
                com.openjiuwen.core.memory.external.MemoryProvider memoryProvider,
                String userId,
                String scopeId,
                String sessionId) {
            return new ExternalMemoryRail(memoryProvider, userId, scopeId, sessionId);
        }
    }
}
