package com.huawei.ascend.runtime.llm.gateway.spi;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default in-process spend ledger. Honest limitation: records do not survive a
 * restart. The durable form is a Flyway-managed {@code llm_spend_log} table; that
 * migration lands once Postgres-backed verification is possible in this module,
 * and replaces this default by registering a JDBC-backed {@link SpendLog} bean.
 */
public final class InMemorySpendLog implements SpendLog {

    private final List<SpendRecord> records = new CopyOnWriteArrayList<>();

    @Override
    public void append(SpendRecord record) {
        records.add(record);
    }

    /** Snapshot of all appended records, in append order. */
    public List<SpendRecord> records() {
        return List.copyOf(records);
    }
}
