package com.huawei.ascend.collab.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.collab.a2a.A2aWorker;
import com.huawei.ascend.collab.core.CollaborationResult;
import com.huawei.ascend.collab.core.Coordinator;
import com.huawei.ascend.collab.core.SubTask;
import com.huawei.ascend.collab.core.TaskToken;
import com.huawei.ascend.collab.core.WorkResult;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Real A2A round-trip: boots the no-LLM {@link DeterministicEchoAgent} on a random
 * port and drives {@link A2aWorker} against it over the actual A2A JSON-RPC wire —
 * proving the engine→A2A bridge works end to end, deterministically, no API key.
 * Whole a2a-sdk stack aligned to 1.0.0.Final (matching the platform).
 */
class A2aWorkerE2eTest {

    private static ConfigurableApplicationContext boot() {
        return new SpringApplicationBuilder(DeterministicEchoAgent.class)
                .run("--server.port=0", "--spring.main.web-application-type=servlet",
                        "--logging.level.root=WARN");
    }

    private static String baseUrl(ConfigurableApplicationContext ctx) {
        Integer port = ctx.getEnvironment().getProperty("local.server.port", Integer.class);
        assertNotNull(port, "local.server.port available");
        return "http://localhost:" + port; // agent base; card resolved from /.well-known/agent-card.json
    }

    @Test
    void a2aWorkerCompletesOverTheWire() {
        try (ConfigurableApplicationContext ctx = boot()) {
            A2aWorker worker = new A2aWorker("echo-worker", Set.of("echo"), baseUrl(ctx));

            TaskToken token = TaskToken.issue("t1", "echo", "echo-worker", "demo-tenant",
                    UUID.randomUUID(), 30_000, System.currentTimeMillis());
            WorkResult r = worker.execute(SubTask.of("t1", "echo", "hello world"), token);

            assertEquals(WorkResult.Status.COMPLETED, r.status(),
                    "remote echo agent completes; detail=" + r.detail() + " output=" + r.output());
            assertNotNull(r.output(), "output present");
        }
    }

    @Test
    void coordinatorOrchestratesARealA2aAgent() {
        try (ConfigurableApplicationContext ctx = boot()) {
            A2aWorker worker = new A2aWorker("echo-worker", Set.of("echo"), baseUrl(ctx));
            Coordinator coordinator = new Coordinator(List.of(worker));

            CollaborationResult result = coordinator.run(List.of(
                    SubTask.of("t1", "echo", "task one"),
                    SubTask.of("t2", "echo", "task two")));

            assertTrue(result.allCompleted(), "both tasks complete via real A2A: " + result.outcomes());
        }
    }
}
