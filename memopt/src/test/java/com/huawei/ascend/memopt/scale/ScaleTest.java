package com.huawei.ascend.memopt.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.memopt.shared.InMemorySharedMemoryStore;
import com.huawei.ascend.memopt.shared.SharedMemoryKit;
import com.huawei.ascend.memopt.user.InMemoryUserMemoryStore;
import com.huawei.ascend.memopt.user.MemoryRecord;
import com.huawei.ascend.memopt.user.MemoryScope;
import com.huawei.ascend.memopt.user.UserMemoryKit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Elasticity: thousands of concurrent A2A collaborations and per-user scopes over
 * a single scope-partitioned store stay correct and isolated — evidence the design
 * scales horizontally (one shared store, partitioned, not a structure per
 * collaboration/user).
 */
class ScaleTest {

    @Test
    void thousandsOfConcurrentCollaborationsStayIsolatedAndCorrect() throws Exception {
        InMemorySharedMemoryStore store = new InMemorySharedMemoryStore(() -> 1L);
        int collaborations = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        AtomicInteger errors = new AtomicInteger();
        try {
            for (int i = 0; i < collaborations; i++) {
                String collabId = "collab-" + i;
                pool.submit(() -> {
                    try {
                        SharedMemoryKit board = SharedMemoryKit.forCollaboration(store, "bank", collabId);
                        board.put("risk", "C3-" + collabId, "risk-agent");
                        board.put("loan", "ok-" + collabId, "loan-agent");
                        // each collaboration only sees its own blackboard
                        if (!("C3-" + collabId).equals(board.get("risk").orElse(""))) {
                            errors.incrementAndGet();
                        }
                        if (board.keys().size() != 2) {
                            errors.incrementAndGet();
                        }
                    } catch (RuntimeException e) {
                        errors.incrementAndGet();
                    }
                });
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "all collaborations finished");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, errors.get(), "no cross-collaboration leakage or contention error at scale");
    }

    @Test
    void thousandsOfConcurrentUsersStayIsolated() throws Exception {
        InMemoryUserMemoryStore store = new InMemoryUserMemoryStore();
        int users = 5_000;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        AtomicInteger leaks = new AtomicInteger();
        try {
            for (int i = 0; i < users; i++) {
                String userId = "u-" + i;
                pool.submit(() -> {
                    MemoryScope scope = MemoryScope.ofUser("bank", userId);
                    UserMemoryKit mem = UserMemoryKit.forUser(store, scope);
                    mem.remember(java.util.List.of(MemoryRecord.of("secret-" + userId)));
                    // this user recalls only its own secret
                    var hits = mem.recall("secret-" + userId, 5);
                    if (hits.size() != 1 || !hits.get(0).content().equals("secret-" + userId)) {
                        leaks.incrementAndGet();
                    }
                });
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "all users finished");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, leaks.get(), "no cross-user leakage at scale");
    }
}
