package com.huawei.ascend.a2a.memory.shared;

import java.util.List;
import java.util.Optional;

/**
 * Backend SPI for the A2A run-scoped shared blackboard. The in-process
 * {@link InMemorySharedMemoryStore} implements it for offline eval; a remote or
 * persistent backend (e.g. redis, or the MemOpt engine) may implement the same
 * SPI for production. The choice of backend and how it is deployed (in-process,
 * remote, containerised) is out of scope here — this SPI only fixes the contract.
 * Implementations MUST enforce the ownership write rule and tenant isolation; the
 * kit facade stays thin.
 */
public interface SharedMemoryStore {

    /**
     * Append a value to a key under one collaboration, enforcing ownership.
     * Non-idempotent (each call appends a new version).
     *
     * @throws OwnershipViolationException if the key exists and {@code writerAgentId}
     *                                     is not its owner (the version-1 writer).
     */
    default SharedEntry append(String tenantId, String collaborationId, String key, String value,
            String writerAgentId) {
        return append(tenantId, collaborationId, key, value, writerAgentId, null);
    }

    /**
     * Append with an idempotency key so a RETRIED write does not duplicate: if a
     * write with the same non-null {@code idempotencyKey} was already applied for
     * this collaboration, the prior {@link SharedEntry} is returned and nothing is
     * appended. A null key falls back to non-idempotent append. Ownership is still
     * enforced.
     *
     * @throws OwnershipViolationException if the key exists and {@code writerAgentId}
     *                                     is not its owner.
     */
    SharedEntry append(String tenantId, String collaborationId, String key, String value, String writerAgentId,
            String idempotencyKey);

    /** Latest entry for a key, or empty if never written. */
    Optional<SharedEntry> latest(String tenantId, String collaborationId, String key);

    /** Full append history for a key, oldest first (provenance trail). */
    List<SharedEntry> history(String tenantId, String collaborationId, String key);

    /** All keys currently present for the collaboration. */
    List<String> keys(String tenantId, String collaborationId);

    /** Drop all blackboard state for the collaboration (run end / archival). */
    void release(String tenantId, String collaborationId);
}
