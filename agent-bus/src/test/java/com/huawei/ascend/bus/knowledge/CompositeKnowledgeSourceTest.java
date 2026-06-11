package com.huawei.ascend.bus.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class CompositeKnowledgeSourceTest {

    private static KnowledgeFragment fragment(String sourceId, String content, double score) {
        return new KnowledgeFragment(sourceId, content, score, Map.of());
    }

    /** A source that ignores the query and returns canned fragments — isolates merge behavior. */
    private static KnowledgeSource canned(KnowledgeFragment... fragments) {
        return query -> List.of(fragments);
    }

    @Test
    void merge_orders_by_score_descending_across_sources() {
        var registry = new KnowledgeRegistry();
        registry.register("t1", "alpha", canned(fragment("alpha", "low", 0.2), fragment("alpha", "high", 0.9)));
        registry.register("t1", "beta", canned(fragment("beta", "mid", 0.5)));
        var composite = new CompositeKnowledgeSource(registry);

        var results = composite.retrieve(new KnowledgeQuery("t1", "anything", 10, Map.of()));

        assertThat(results).extracting(KnowledgeFragment::content).containsExactly("high", "mid", "low");
    }

    @Test
    void equal_scores_keep_stable_source_name_order() {
        var registry = new KnowledgeRegistry();
        // register out of name order on purpose — fan-out is name-sorted, so beta still follows alpha
        registry.register("t1", "beta", canned(fragment("beta", "from-beta", 0.5)));
        registry.register("t1", "alpha", canned(fragment("alpha", "from-alpha", 0.5)));
        var composite = new CompositeKnowledgeSource(registry);

        var results = composite.retrieve(new KnowledgeQuery("t1", "anything", 10, Map.of()));

        assertThat(results).extracting(KnowledgeFragment::content).containsExactly("from-alpha", "from-beta");
    }

    @Test
    void top_k_is_applied_after_the_merge() {
        var registry = new KnowledgeRegistry();
        registry.register("t1", "weak", canned(fragment("weak", "w1", 0.1), fragment("weak", "w2", 0.1)));
        registry.register("t1", "strong", canned(fragment("strong", "s1", 0.9), fragment("strong", "s2", 0.8)));
        var composite = new CompositeKnowledgeSource(registry);

        var results = composite.retrieve(new KnowledgeQuery("t1", "anything", 2, Map.of()));

        // a per-source quota would have let "weak" displace the strong source's second hit
        assertThat(results).extracting(KnowledgeFragment::content).containsExactly("s1", "s2");
    }

    @Test
    void no_registered_sources_yields_empty_list() {
        var composite = new CompositeKnowledgeSource(new KnowledgeRegistry());
        assertThat(composite.retrieve(new KnowledgeQuery("t1", "anything", 5, Map.of()))).isEmpty();
    }

    @Test
    void fan_out_is_tenant_scoped() {
        var registry = new KnowledgeRegistry();
        registry.register("tenant-a", "src", canned(fragment("src", "a-only", 1.0)));
        var composite = new CompositeKnowledgeSource(registry);

        assertThat(composite.retrieve(new KnowledgeQuery("tenant-b", "anything", 5, Map.of()))).isEmpty();
    }

    @Test
    void registry_rejects_duplicate_names_per_tenant_but_allows_them_across_tenants() {
        var registry = new KnowledgeRegistry();
        registry.register("t1", "src", canned());
        assertThatIllegalStateException()
                .isThrownBy(() -> registry.register("t1", "src", canned()))
                .withMessageContaining("src");
        registry.register("t2", "src", canned());

        assertThat(registry.unregister("t1", "src")).isTrue();
        assertThat(registry.unregister("t1", "src")).isFalse();
    }
}
