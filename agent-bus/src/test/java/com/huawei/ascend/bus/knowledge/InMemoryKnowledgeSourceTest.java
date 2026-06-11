package com.huawei.ascend.bus.knowledge;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class InMemoryKnowledgeSourceTest {

    @Test
    void scores_by_token_overlap_and_orders_best_match_first() {
        var source = new InMemoryKnowledgeSource("docs");
        source.seed("t1", "wire transfer limits for corporate accounts");
        source.seed("t1", "wire transfer fees");
        source.seed("t1", "branch opening hours");

        var results = source.retrieve(new KnowledgeQuery("t1", "corporate wire transfer limits", 10, Map.of()));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).content()).isEqualTo("wire transfer limits for corporate accounts");
        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
        assertThat(results).extracting(KnowledgeFragment::sourceId).containsOnly("docs");
    }

    @Test
    void matching_is_case_insensitive() {
        var source = new InMemoryKnowledgeSource("docs");
        source.seed("t1", "FX Settlement Window");

        assertThat(source.retrieve(new KnowledgeQuery("t1", "fx settlement", 5, Map.of()))).hasSize(1);
    }

    @Test
    void honors_top_k() {
        var source = new InMemoryKnowledgeSource("docs");
        source.seed("t1", "loan rates overview");
        source.seed("t1", "loan rates detail");
        source.seed("t1", "loan rates history");

        assertThat(source.retrieve(new KnowledgeQuery("t1", "loan rates", 2, Map.of()))).hasSize(2);
    }

    @Test
    void tenant_seeds_are_invisible_to_other_tenants() {
        var source = new InMemoryKnowledgeSource("docs");
        source.seed("tenant-a", "tenant a confidential pricing");

        assertThat(source.retrieve(new KnowledgeQuery("tenant-b", "confidential pricing", 5, Map.of()))).isEmpty();
    }

    @Test
    void no_overlap_yields_empty_list_and_provenance_is_carried() {
        var source = new InMemoryKnowledgeSource("docs");
        source.seed("t1", "card dispute workflow", Map.of("doc", "kb-42"));

        assertThat(source.retrieve(new KnowledgeQuery("t1", "unrelated topic", 5, Map.of()))).isEmpty();

        var hit = source.retrieve(new KnowledgeQuery("t1", "card dispute", 5, Map.of()));
        assertThat(hit).hasSize(1);
        assertThat(hit.get(0).provenance()).containsEntry("doc", "kb-42");
    }

    @Test
    void query_record_validates_inputs() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KnowledgeQuery(" ", "q", 5, Map.of()))
                .withMessageContaining("tenantId");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KnowledgeQuery("t1", " ", 5, Map.of()))
                .withMessageContaining("query");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KnowledgeQuery("t1", "q", 0, Map.of()))
                .withMessageContaining("topK");
        assertThat(new KnowledgeQuery("t1", "q", 5, null).filters()).isEmpty();
    }

    @Test
    void fragment_record_validates_inputs() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KnowledgeFragment("src", "c", Double.NaN, Map.of()))
                .withMessageContaining("score");
        assertThat(new KnowledgeFragment("src", "c", 0.5, null).provenance()).isEmpty();
    }
}
