package com.huawei.ascend.bus.messaging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.fail;

class InMemoryAgentMessageBusTest {

    private static final Duration DEADLINE = Duration.ofSeconds(5);

    private final InMemoryAgentMessageBus bus = new InMemoryAgentMessageBus();

    @AfterEach
    void tearDown() {
        bus.close();
    }

    @Test
    void delivers_messages_in_publish_order_per_topic() throws Exception {
        int count = 50;
        var received = new CopyOnWriteArrayList<Integer>();
        var done = new CountDownLatch(count);
        bus.subscribe("t1", "orders", message -> {
            received.add((Integer) message.payload().get("seq"));
            done.countDown();
        });

        for (int i = 0; i < count; i++) {
            bus.publish(message("t1", "orders", i));
        }

        assertThat(done.await(DEADLINE.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        assertThat(received).containsExactlyElementsOf(IntStream.range(0, count).boxed().toList());
    }

    @Test
    void full_queue_drops_oldest_counts_drops_and_still_delivers_newest() throws Exception {
        try (var smallBus = new InMemoryAgentMessageBus(4)) {
            var received = new CopyOnWriteArrayList<Integer>();
            var firstInFlight = new CountDownLatch(1);
            var gate = new CountDownLatch(1);
            var subscription = smallBus.subscribe("t1", "orders", message -> {
                if ((Integer) message.payload().get("seq") == 0) {
                    firstInFlight.countDown();
                    awaitQuietly(gate);
                }
                received.add((Integer) message.payload().get("seq"));
            });

            smallBus.publish(message("t1", "orders", 0));
            // The dispatcher is now blocked inside the handler, so the next
            // publishes pile up deterministically in the bounded queue.
            assertThat(firstInFlight.await(DEADLINE.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            for (int i = 1; i <= 10; i++) {
                smallBus.publish(message("t1", "orders", i));
            }
            gate.countDown();

            awaitUntil(() -> received.size() == 5);
            // Capacity 4: seq 1..6 were evicted oldest-first; the newest four survive.
            assertThat(received).containsExactly(0, 7, 8, 9, 10);
            assertThat(subscription.droppedCount()).isEqualTo(6);
        }
    }

    @Test
    void handler_exception_does_not_disturb_other_subscribers_or_later_messages() throws Exception {
        var throwingAttempts = new AtomicInteger();
        var received = new CopyOnWriteArrayList<Integer>();
        var done = new CountDownLatch(3);
        bus.subscribe("t1", "orders", message -> {
            throwingAttempts.incrementAndGet();
            throw new IllegalStateException("boom");
        });
        bus.subscribe("t1", "orders", message -> {
            received.add((Integer) message.payload().get("seq"));
            done.countDown();
        });

        for (int i = 0; i < 3; i++) {
            bus.publish(message("t1", "orders", i));
        }

        assertThat(done.await(DEADLINE.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        assertThat(received).containsExactly(0, 1, 2);
        awaitUntil(() -> throwingAttempts.get() == 3);
    }

    @Test
    void subscriber_never_receives_another_tenants_messages_on_the_same_topic_name() throws Exception {
        var tenantAReceived = new CopyOnWriteArrayList<String>();
        var tenantALatch = new CountDownLatch(1);
        bus.subscribe("tenant-a", "orders", message -> {
            tenantAReceived.add(message.tenantId());
            tenantALatch.countDown();
        });

        // Publish B first: the single FIFO dispatcher guarantees that by the
        // time A's message is delivered, B's would already have been (mis)routed.
        bus.publish(message("tenant-b", "orders", 1));
        bus.publish(message("tenant-a", "orders", 2));

        assertThat(tenantALatch.await(DEADLINE.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        assertThat(tenantAReceived).containsExactly("tenant-a");
    }

    @Test
    void closed_subscription_stops_receiving_promptly() throws Exception {
        var sub1Received = new CopyOnWriteArrayList<Integer>();
        var sentinelReceived = new CopyOnWriteArrayList<Integer>();
        var firstDelivered = new CountDownLatch(2);
        var sentinelGotSecond = new CountDownLatch(2);
        var subscription = bus.subscribe("t1", "orders", message -> {
            sub1Received.add((Integer) message.payload().get("seq"));
            firstDelivered.countDown();
        });
        bus.subscribe("t1", "orders", message -> {
            sentinelReceived.add((Integer) message.payload().get("seq"));
            firstDelivered.countDown();
            sentinelGotSecond.countDown();
        });

        bus.publish(message("t1", "orders", 1));
        assertThat(firstDelivered.await(DEADLINE.toMillis(), TimeUnit.MILLISECONDS)).isTrue();

        subscription.close();
        bus.publish(message("t1", "orders", 2));

        // FIFO dispatcher: once the sentinel saw seq=2, sub1 would have too.
        assertThat(sentinelGotSecond.await(DEADLINE.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        assertThat(sentinelReceived).containsExactly(1, 2);
        assertThat(sub1Received).containsExactly(1);
        assertThatCode(subscription::close).doesNotThrowAnyException();
    }

    @Test
    void concurrent_publishers_lose_nothing_unaccounted() throws Exception {
        int threads = 4;
        int perThread = 100;
        var delivered = new AtomicInteger();
        var subscription = bus.subscribe("t1", "orders", message -> delivered.incrementAndGet());

        var start = new CountDownLatch(1);
        var publishers = IntStream.range(0, threads)
                .mapToObj(t -> new Thread(() -> {
                    awaitQuietly(start);
                    for (int i = 0; i < perThread; i++) {
                        bus.publish(message("t1", "orders", t * perThread + i));
                    }
                }, "publisher-" + t))
                .toList();
        publishers.forEach(Thread::start);
        start.countDown();
        for (Thread publisher : publishers) {
            publisher.join(DEADLINE.toMillis());
            assertThat(publisher.isAlive()).isFalse();
        }

        awaitUntil(() -> delivered.get() + subscription.droppedCount() == (long) threads * perThread);
        assertThat(delivered.get() + subscription.droppedCount()).isEqualTo(threads * perThread);
    }

    @Test
    void close_is_idempotent_and_publish_after_close_is_rejected() {
        var localBus = new InMemoryAgentMessageBus();
        localBus.close();
        assertThatCode(localBus::close).doesNotThrowAnyException();
        assertThatIllegalStateException()
                .isThrownBy(() -> localBus.publish(message("t1", "orders", 1)))
                .withMessageContaining("closed");
        assertThatIllegalStateException()
                .isThrownBy(() -> localBus.subscribe("t1", "orders", message -> { }));
    }

    private static AgentMessage message(String tenantId, String topic, int seq) {
        return AgentMessage.of(tenantId, topic, "agent-test", Map.of("seq", seq));
    }

    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + DEADLINE.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadlineNanos) {
                fail("condition not met within " + DEADLINE);
            }
            Thread.sleep(5);
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
