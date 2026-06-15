package com.huawei.ascend.collab.a2a;

import com.huawei.ascend.collab.core.SubTask;
import com.huawei.ascend.collab.core.TaskToken;
import com.huawei.ascend.collab.core.WorkResult;
import com.huawei.ascend.collab.core.Worker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Bridges the collaboration engine to a real A2A agent: the
 * {@link com.huawei.ascend.collab.core.Coordinator} dispatches a {@link SubTask}
 * to this worker, which streams it to a remote A2A endpoint over the SDK
 * {@link ClientTransport}, carrying the {@link TaskToken} on the message metadata,
 * and maps the remote task's terminal state to a {@link WorkResult}. The same
 * Coordinator therefore orchestrates real A2A agents (this worker) or, in eval,
 * deterministic in-memory workers.
 *
 * <p>Uses the blocking {@code message/send} call and maps the terminal
 * {@link Task} state (or a direct {@link Message} reply) to a {@link WorkResult}.
 * The {@link TaskToken} rides the message metadata as the idempotency/deadline
 * credential; the tenant rides the {@code X-Tenant-Id} header (not
 * {@code MessageSendParams.tenant()}, which would route to a tenant-scoped URL).
 * On a correlated response this worker re-presents the issued token.
 */
public final class A2aWorker implements Worker {

    public static final String MK_TOKEN = "task.token.id";
    public static final String MK_TASK = "task.token.task";
    public static final String MK_IDEM = "task.token.idempotencyKey";
    public static final String MK_DEADLINE = "task.token.deadlineEpochMs";

    private final String id;
    private final Set<String> capabilities;
    private final ClientTransport transport;
    private final long timeoutMs;

    /**
     * @param baseUrl the remote agent's BASE url (e.g. {@code http://host:8080}); the
     *                agent card is resolved from {@code /.well-known/agent-card.json}.
     */
    public A2aWorker(String id, Set<String> capabilities, String baseUrl) {
        this(id, capabilities, baseUrl, 30_000);
    }

    public A2aWorker(String id, Set<String> capabilities, String baseUrl, long timeoutMs) {
        this.id = id;
        this.capabilities = Set.copyOf(capabilities);
        this.timeoutMs = timeoutMs;
        try {
            AgentCard card = A2ACardResolver.builder().baseUrl(baseUrl).build().getAgentCard();
            this.transport = new JSONRPCTransport(card);
        } catch (Throwable e) {
            throw new IllegalStateException(
                    "failed to resolve A2A agent card at " + baseUrl + ": " + e.getMessage(), e);
        }
    }

    /** For tests/custom transports. */
    public A2aWorker(String id, Set<String> capabilities, ClientTransport transport, long timeoutMs) {
        this.id = id;
        this.capabilities = Set.copyOf(capabilities);
        this.transport = transport;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<String> capabilities() {
        return capabilities;
    }

    @Override
    public WorkResult execute(SubTask task, TaskToken token) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put(MK_TOKEN, token.tokenId().toString());
            meta.put(MK_TASK, token.taskId());
            meta.put(MK_IDEM, token.idempotencyKey().toString());
            meta.put(MK_DEADLINE, token.deadlineEpochMs());

            Message message = Message.builder()
                    .role(Message.Role.ROLE_USER)
                    .messageId(UUID.randomUUID().toString())
                    .parts(List.<Part<?>>of(new TextPart(task.payload() == null ? "" : task.payload())))
                    .metadata(meta)
                    .build();
            // Tenant rides the X-Tenant-Id header (which the runtime reads), NOT
            // MessageSendParams.tenant() — the latter makes the SDK route to a
            // tenant-scoped URL path (/a2a/{tenant}) the runtime does not serve.
            MessageSendParams params = MessageSendParams.builder()
                    .message(message).metadata(meta).build();
            ClientCallContext ctx = new ClientCallContext(Map.of(),
                    Map.of("X-Tenant-Id", token.tenantId()));

            EventKind result = transport.sendMessage(params, ctx);
            return map(task, token, result);
        } catch (Throwable t) {
            return WorkResult.failed(task.id(), token, id,
                    "a2a error: " + t.getClass().getSimpleName()
                            + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        }
    }

    private WorkResult map(SubTask task, TaskToken token, EventKind result) {
        if (result instanceof Task t) {
            TaskState state = t.status() == null ? null : t.status().state();
            String text = textFrom(t);
            if (state == TaskState.TASK_STATE_COMPLETED) {
                return WorkResult.completed(task.id(), text == null || text.isBlank() ? "completed" : text, token, id);
            }
            if (state == TaskState.TASK_STATE_INPUT_REQUIRED || state == TaskState.TASK_STATE_AUTH_REQUIRED) {
                return new WorkResult(task.id(), WorkResult.Status.INPUT_REQUIRED, null, token, id, null, "remote input");
            }
            if (state == TaskState.TASK_STATE_CANCELED) {
                return WorkResult.timeout(task.id(), token, id);
            }
            return WorkResult.failed(task.id(), token, id, "remote state " + state);
        }
        if (result instanceof Message m) {
            String text = textOf(m);
            return WorkResult.completed(task.id(), text == null || text.isBlank() ? "completed" : text, token, id);
        }
        return WorkResult.failed(task.id(), token, id, "unexpected event kind: " + result);
    }

    /** Reclaim a remote task (orchestrator on timeout/reassignment). */
    public void cancelRemote(String remoteTaskId, String tenantId) {
        try {
            transport.cancelTask(new CancelTaskParams(remoteTaskId),
                    new ClientCallContext(Map.of(), Map.of("X-Tenant-Id", tenantId)));
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    /** Collect text from a completed task: its artifacts plus any status message. */
    private static String textFrom(Task t) {
        StringBuilder sb = new StringBuilder();
        if (t.artifacts() != null) {
            for (Artifact a : t.artifacts()) {
                appendParts(sb, a.parts());
            }
        }
        if (t.status() != null && t.status().message() != null) {
            appendParts(sb, t.status().message().parts());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String textOf(Message m) {
        StringBuilder sb = new StringBuilder();
        appendParts(sb, m.parts());
        return sb.isEmpty() ? null : sb.toString();
    }

    private static void appendParts(StringBuilder sb, List<Part<?>> parts) {
        if (parts == null) {
            return;
        }
        for (Part<?> p : parts) {
            if (p instanceof TextPart tp) {
                sb.append(tp.text());
            }
        }
    }
}
