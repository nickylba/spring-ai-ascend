package com.huawei.ascend.bus.memory;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RecordingBusinessFactPublisherTest {

    private static BusinessFactEvent fact(String factType) {
        return new BusinessFactEvent(
                "t1", "s1", "run-1", factType,
                Map.of("entity", "[USER_ID_102]"), true,
                Instant.parse("2026-06-11T00:00:00Z"));
    }

    @Test
    void drain_returns_facts_in_emission_order_and_clears_the_log() {
        var publisher = new RecordingBusinessFactPublisher();
        publisher.publish(fact("F1"));
        publisher.publish(fact("F2"));

        assertThat(publisher.drain())
                .extracting(BusinessFactEvent::factType)
                .containsExactly("F1", "F2");
        assertThat(publisher.drain()).isEmpty();
    }

    @Test
    void bounded_log_drops_oldest_facts_first() {
        var publisher = new RecordingBusinessFactPublisher(2);
        publisher.publish(fact("F1"));
        publisher.publish(fact("F2"));
        publisher.publish(fact("F3"));

        assertThat(publisher.drain())
                .extracting(BusinessFactEvent::factType)
                .containsExactly("F2", "F3");
    }

    @Test
    void event_validates_required_fields_and_allows_nullable_run_id() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BusinessFactEvent("", "s1", null, "F", Map.of(), true, Instant.now()))
                .withMessageContaining("tenantId");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BusinessFactEvent("t1", "s1", null, " ", Map.of(), true, Instant.now()))
                .withMessageContaining("factType");
        assertThatNullPointerException()
                .isThrownBy(() -> new BusinessFactEvent("t1", "s1", null, "F", Map.of(), true, null))
                .withMessageContaining("occurredAt");

        var event = new BusinessFactEvent("t1", "s1", null, "F", null, false, Instant.now());
        assertThat(event.runId()).isNull();
        assertThat(event.payload()).isEmpty();
    }

    @Test
    void constructor_rejects_non_positive_cap() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RecordingBusinessFactPublisher(0))
                .withMessageContaining("maxRecordedEvents");
    }
}
