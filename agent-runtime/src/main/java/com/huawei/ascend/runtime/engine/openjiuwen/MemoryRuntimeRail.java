package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional openJiuwen rail that bridges openJiuwen callback messages to a
 * runtime-neutral {@link MemoryProvider}.
 *
 * <p>This class is intentionally openJiuwen-local. Other agent frameworks
 * should use their own native callback/middleware mechanism rather than
 * depending on openJiuwen's Rail API.
 */
final class MemoryRuntimeRail extends AgentRail {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryRuntimeRail.class);
    private static final int DEFAULT_MEMORY_SEARCH_LIMIT = 5;

    private final AgentExecutionContext executionContext;
    private final MemoryProvider memoryProvider;
    private final OpenJiuwenMemoryMessageAdapter memoryMessageAdapter;

    /**
     * Memory block computed once per invocation and reused across model-call iterations.
     * Null until {@code beforeInvoke} completes the search, or when no hits are found.
     */
    private String cachedMemoryBlock;

    MemoryRuntimeRail(AgentExecutionContext executionContext, MemoryProvider memoryProvider,
            OpenJiuwenMemoryMessageAdapter memoryMessageAdapter) {
        this.executionContext = Objects.requireNonNull(executionContext, "executionContext");
        this.memoryProvider = Objects.requireNonNull(memoryProvider, "memoryProvider");
        this.memoryMessageAdapter = Objects.requireNonNull(memoryMessageAdapter, "memoryMessageAdapter");
    }

    @Override
    public void beforeInvoke(AgentCallbackContext callbackContext) {
        try {
            memoryProvider.init(executionContext);
        } catch (RuntimeException error) {
            LOGGER.warn("openjiuwen memory init failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                    executionContext.getScope().tenantId(),
                    executionContext.getScope().sessionId(),
                    executionContext.getScope().taskId(),
                    error.getClass().getSimpleName(),
                    OpenJiuwenAgentRuntimeHandler.errorMessage(error));
        }
        try {
            cachedMemoryBlock = computeMemoryBlock();
            injectMemoryIntoModelContext(callbackContext);
        } catch (RuntimeException error) {
            LOGGER.warn("openjiuwen memory search inject failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                    executionContext.getScope().tenantId(),
                    executionContext.getScope().sessionId(),
                    executionContext.getScope().taskId(),
                    error.getClass().getSimpleName(),
                    OpenJiuwenAgentRuntimeHandler.errorMessage(error));
        }
    }

    /**
     * Injects the cached memory block into the outgoing per-iteration model call messages.
     * ReActAgent rebuilds its prompt from its config template each iteration, so the
     * ModelContext mutation in {@code beforeInvoke} is not visible to it. Injecting here
     * into {@link ModelCallInputs} reaches the actual request sent to the LLM on every
     * ReActAgent iteration. The injection is idempotent: if the system message already
     * contains the memory block it is not added again.
     */
    @Override
    public void beforeModelCall(AgentCallbackContext context) {
        if (cachedMemoryBlock == null || cachedMemoryBlock.isBlank()) {
            return;
        }
        try {
            if (!(context.getInputs() instanceof ModelCallInputs inputs)) {
                return;
            }
            List<Object> current = inputs.getMessages();
            List<Object> updated = new ArrayList<>(current == null ? List.of() : current);
            String block = runtimeMemoryBlock(cachedMemoryBlock);
            for (int i = 0; i < updated.size(); i++) {
                Object msg = updated.get(i);
                if (isSystemMessage(msg)) {
                    String existingContent = contentOf(msg);
                    if (existingContent != null && existingContent.contains(block)) {
                        return;
                    }
                    updated.set(i, mergedSystemMessage(msg, block));
                    inputs.setMessages(updated);
                    return;
                }
            }
            if (updated.stream().noneMatch(m -> isSystemMessage(m)
                    && block.equals(contentOf(m)))) {
                updated.add(0, new SystemMessage(block));
                inputs.setMessages(updated);
            }
        } catch (RuntimeException error) {
            LOGGER.warn("openjiuwen memory beforeModelCall inject failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                    executionContext.getScope().tenantId(),
                    executionContext.getScope().sessionId(),
                    executionContext.getScope().taskId(),
                    error.getClass().getSimpleName(),
                    OpenJiuwenAgentRuntimeHandler.errorMessage(error));
        }
    }

    @Override
    public void afterInvoke(AgentCallbackContext callbackContext) {
        List<BaseMessage> messages = messages(callbackContext);
        if (messages.isEmpty()) {
            return;
        }
        try {
            List<MemoryProvider.MemoryRecord> records = messages.stream()
                    .map(this::toLongTermMemoryRecord)
                    .filter(Objects::nonNull)
                    .toList();
            if (!records.isEmpty()) {
                memoryProvider.save(executionContext, records);
            }
        } catch (RuntimeException error) {
            LOGGER.warn("openjiuwen memory save failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                    executionContext.getScope().tenantId(),
                    executionContext.getScope().sessionId(),
                    executionContext.getScope().taskId(),
                    error.getClass().getSimpleName(),
                    OpenJiuwenAgentRuntimeHandler.errorMessage(error));
        }
    }

    private static List<BaseMessage> messages(AgentCallbackContext callbackContext) {
        if (callbackContext == null) {
            return List.of();
        }
        ModelContext modelContext = callbackContext.getContext();
        if (modelContext == null) {
            return List.of();
        }
        List<BaseMessage> messages = modelContext.getMessages();
        return messages == null ? List.of() : messages;
    }

    private MemoryProvider.MemoryRecord toLongTermMemoryRecord(BaseMessage message) {
        MemoryProvider.MemoryRecord record = memoryMessageAdapter.toMemoryRecord(message);
        if (isLongTermTurnRole(record.role()) && hasText(record.content())) {
            return record;
        }
        return null;
    }

    /**
     * Searches memory once and returns the formatted block, or null when there are no hits.
     * Called in {@code beforeInvoke} so the search runs at most once per agent invocation.
     */
    private String computeMemoryBlock() {
        String query = executionContext.lastUserText();
        if (query.isBlank()) {
            return null;
        }
        List<MemoryProvider.MemoryHit> hits =
                memoryProvider.search(executionContext, query, DEFAULT_MEMORY_SEARCH_LIMIT);
        if (hits.isEmpty()) {
            return null;
        }
        return formatMemoryBlock(hits);
    }

    /**
     * Injects the cached memory block into the ModelContext for agent types that read it
     * (e.g., agents that use a mutable systemPrompt backed by ModelContext). This path is
     * kept so callers that rely on ModelContext continue to work.
     */
    private void injectMemoryIntoModelContext(AgentCallbackContext callbackContext) {
        if (cachedMemoryBlock == null) {
            return;
        }
        ModelContext modelContext = callbackContext == null ? null : callbackContext.getContext();
        if (modelContext == null) {
            return;
        }
        mergeMemoryIntoSystemMessage(modelContext, runtimeMemoryBlock(cachedMemoryBlock));
    }

    private static String formatMemoryBlock(List<MemoryProvider.MemoryHit> hits) {
        StringBuilder block = new StringBuilder("Relevant memory:\n");
        for (MemoryProvider.MemoryHit hit : hits) {
            if (hit != null && !hit.content().isBlank()) {
                block.append("- ").append(hit.content()).append('\n');
            }
        }
        return block.toString().trim();
    }

    private static void mergeMemoryIntoSystemMessage(ModelContext modelContext, String memoryBlock) {
        List<BaseMessage> currentMessages = modelContext.getMessages();
        List<BaseMessage> updatedMessages =
                new ArrayList<>(currentMessages == null ? List.of() : currentMessages);
        for (int i = 0; i < updatedMessages.size(); i++) {
            BaseMessage message = updatedMessages.get(i);
            if (isSystemMessage(message)) {
                updatedMessages.set(i, mergedSystemMessage(message, memoryBlock));
                modelContext.setMessages(updatedMessages, true);
                return;
            }
        }
        updatedMessages.add(0, new SystemMessage(memoryBlock));
        modelContext.setMessages(updatedMessages, true);
    }

    private static String runtimeMemoryBlock(String memoryBlock) {
        return "[System note: recalled memory context from runtime memory, not new user input.]\n\n"
                + memoryBlock;
    }

    private static boolean isLongTermTurnRole(String role) {
        return "user".equalsIgnoreCase(role) || "assistant".equalsIgnoreCase(role);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Returns true when {@code message} is an openJiuwen SystemMessage or any object
     * whose role resolves to "system". Accepts {@code Object} so the check works for
     * both the typed {@code List<BaseMessage>} (ModelContext path) and the untyped
     * {@code List<Object>} (ModelCallInputs path).
     */
    private static boolean isSystemMessage(Object message) {
        return message instanceof SystemMessage
                || (message instanceof BaseMessage bm && "system".equalsIgnoreCase(bm.getRole()));
    }

    /** Returns the string content of a BaseMessage, or null if not a BaseMessage. */
    private static String contentOf(Object message) {
        return message instanceof BaseMessage bm ? bm.getContentAsString() : null;
    }

    private static SystemMessage mergedSystemMessage(Object original, String memoryBlock) {
        if (original instanceof BaseMessage bm) {
            String originalContent = bm.getContentAsString();
            String mergedContent = originalContent == null || originalContent.isBlank()
                    ? memoryBlock
                    : originalContent + "\n\n" + memoryBlock;
            String name = bm.getName();
            return name == null || name.isBlank()
                    ? new SystemMessage(mergedContent)
                    : new SystemMessage(mergedContent, name);
        }
        return new SystemMessage(memoryBlock);
    }
}
