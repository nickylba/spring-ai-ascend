package com.huawei.ascend.client;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Interpretation of the platform's A2A event stream — the client-side truth
 * for "which event ends a run" and "which text is the user-visible answer",
 * promoted from the e2e example so consumers stop re-implementing it.
 */
public final class A2aEvents {

    /**
     * The runtime's canonical RunStatus wire values that end a run
     * (lower-cased enum names): completed / failed / canceled / rejected.
     * "cancelled" is kept only as a defensive alias.
     */
    private static final Set<String> TERMINAL_RUN_STATUSES =
            Set.of("completed", "failed", "canceled", "rejected", "cancelled");

    private A2aEvents() {
    }

    /**
     * The user-visible answer text across every event shape the platform
     * emits: agent {@code Message} parts (excluding the accepted-ack message,
     * which is an enqueue receipt, not an answer), status-update messages,
     * artifact updates, and — on the non-streaming path — a {@code Task}
     * result's status message and artifacts.
     */
    public static String textFrom(List<StreamingEventKind> events) {
        StringBuilder result = new StringBuilder();
        for (StreamingEventKind event : events) {
            if (event instanceof Message message) {
                if (message.metadata() == null || !Boolean.TRUE.equals(message.metadata().get("accepted"))) {
                    result.append(textFromParts(message.parts()));
                }
            } else if (event instanceof TaskStatusUpdateEvent statusEvent
                    && statusEvent.status() != null
                    && statusEvent.status().message() != null) {
                result.append(textFromParts(statusEvent.status().message().parts()));
            } else if (event instanceof TaskArtifactUpdateEvent artifactEvent
                    && artifactEvent.artifact() != null) {
                result.append(textFromParts(artifactEvent.artifact().parts()));
            } else if (event instanceof Task task) {
                if (task.status() != null && task.status().message() != null) {
                    result.append(textFromParts(task.status().message().parts()));
                }
                if (task.artifacts() != null) {
                    for (Artifact artifact : task.artifacts()) {
                        result.append(textFromParts(artifact.parts()));
                    }
                }
            }
        }
        return result.toString();
    }

    /**
     * True when this event ends the run. The runtime signals termination two
     * ways — a final A2A {@code TaskStatusUpdateEvent} state, or a
     * {@code Message} carrying a terminal {@code runStatus} metadata value.
     * A {@code Task} snapshot in a final state is also terminal: the OSS
     * client auto-closes the SSE subscription on it, so not recognizing it
     * here would misread the resulting unsubscribe as a transport failure.
     */
    public static boolean isTerminal(StreamingEventKind event) {
        if (event instanceof TaskStatusUpdateEvent statusEvent
                && statusEvent.status() != null
                && statusEvent.status().state() != null) {
            return isFinalState(statusEvent.status().state());
        }
        if (event instanceof Task task
                && task.status() != null
                && task.status().state() != null) {
            return isFinalState(task.status().state());
        }
        if (event instanceof Message message && message.metadata() != null) {
            return TERMINAL_RUN_STATUSES.contains(String.valueOf(message.metadata().get("runStatus")));
        }
        return false;
    }

    /**
     * A streamed error is a real failure UNLESS it is a CancellationException
     * that arrived AFTER a terminal event (the A2A SDK's normal post-terminal
     * SSE unsubscribe — it cancels the subscription right after the terminal
     * event, including for terminal FAILED runs). A cancellation before any
     * terminal event is a partial-stream transport failure that must surface.
     */
    static boolean isFailureError(Throwable error, boolean sawTerminal) {
        return !(causedByCancellation(error) && sawTerminal);
    }

    private static boolean isFinalState(TaskState state) {
        return state == TaskState.TASK_STATE_COMPLETED
                || state == TaskState.TASK_STATE_FAILED
                || state == TaskState.TASK_STATE_CANCELED
                || state == TaskState.TASK_STATE_REJECTED;
    }

    private static boolean causedByCancellation(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof CancellationException) {
                return true;
            }
        }
        return false;
    }

    private static String textFromParts(List<Part<?>> parts) {
        if (parts == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (Part<?> part : parts) {
            if (part instanceof TextPart textPart && !textPart.text().isBlank()) {
                result.append(textPart.text());
            }
        }
        return result.toString();
    }
}
