package com.huawei.ascend.bus.memory;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class InMemorySessionMemoryStoreTest {

    private static MemoryEntry entry(String text) {
        return new MemoryEntry("user", text, Instant.parse("2026-06-11T00:00:00Z"), Map.of());
    }

    @Test
    void window_returns_newest_first_and_honors_max_entries() {
        var store = new InMemorySessionMemoryStore();
        store.append("t1", "s1", entry("first"));
        store.append("t1", "s1", entry("second"));
        store.append("t1", "s1", entry("third"));

        assertThat(store.window("t1", "s1", 2))
                .extracting(MemoryEntry::text)
                .containsExactly("third", "second");
        assertThat(store.window("t1", "s1", 10))
                .extracting(MemoryEntry::text)
                .containsExactly("third", "second", "first");
    }

    @Test
    void cap_evicts_oldest_entries_first() {
        var store = new InMemorySessionMemoryStore(3);
        for (int i = 1; i <= 5; i++) {
            store.append("t1", "s1", entry("e" + i));
        }

        assertThat(store.window("t1", "s1", 10))
                .extracting(MemoryEntry::text)
                .containsExactly("e5", "e4", "e3");
    }

    @Test
    void tenants_are_structurally_isolated() {
        var store = new InMemorySessionMemoryStore();
        store.append("tenant-a", "shared-session-id", entry("a-secret"));
        store.append("tenant-b", "shared-session-id", entry("b-secret"));

        assertThat(store.window("tenant-a", "shared-session-id", 10))
                .extracting(MemoryEntry::text)
                .containsExactly("a-secret");
        assertThat(store.window("tenant-b", "shared-session-id", 10))
                .extracting(MemoryEntry::text)
                .containsExactly("b-secret");
    }

    @Test
    void clear_drops_only_the_target_session() {
        var store = new InMemorySessionMemoryStore();
        store.append("t1", "s1", entry("keep-out"));
        store.append("t1", "s2", entry("keep"));

        store.clear("t1", "s1");

        assertThat(store.window("t1", "s1", 10)).isEmpty();
        assertThat(store.window("t1", "s2", 10)).extracting(MemoryEntry::text).containsExactly("keep");
    }

    @Test
    void unknown_session_window_is_empty_not_null() {
        var store = new InMemorySessionMemoryStore();
        assertThat(store.window("t1", "never-seen", 10)).isEmpty();
    }

    @Test
    void blank_identifiers_are_rejected() {
        var store = new InMemorySessionMemoryStore();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> store.append("  ", "s1", entry("x")))
                .withMessageContaining("tenantId");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> store.window("t1", "", 10))
                .withMessageContaining("sessionId");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> store.window("t1", "s1", 0))
                .withMessageContaining("maxEntries");
    }

    @Test
    void memory_entry_validates_required_fields_and_defaults_attributes() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MemoryEntry(null, "x", Instant.now(), Map.of()))
                .withMessageContaining("role");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MemoryEntry(" ", "x", Instant.now(), Map.of()))
                .withMessageContaining("role");
        assertThat(new MemoryEntry("user", "x", Instant.now(), null).attributes()).isEmpty();
    }

    @Test
    void constructor_rejects_non_positive_cap() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InMemorySessionMemoryStore(0))
                .withMessageContaining("maxEntriesPerSession");
    }
}
