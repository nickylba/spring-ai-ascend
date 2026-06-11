package com.huawei.ascend.bus.messaging;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory reference implementation of {@link AgentMessageBus}.
 *
 * <p>All deliveries run on ONE shared daemon dispatcher thread: a single
 * serial executor is the cheapest structure that makes per-topic delivery
 * order a guarantee rather than an accident — messages are handed to handlers
 * in exactly the order their enqueues were serialized, with no per-topic
 * thread bookkeeping. The cost (one slow handler delays other topics' handlers
 * on the shared thread) is acceptable for the in-process reference plane;
 * throughput-sensitive deployments plug a real transport behind the SPI.
 *
 * <p>Backpressure is per subscriber: each subscription owns a bounded FIFO
 * queue (default {@value #DEFAULT_QUEUE_CAPACITY}); when full, the OLDEST
 * queued message is dropped so the newest is always retained, the drop is
 * counted on the subscription, and the first drop logs a warning. Handler
 * exceptions are logged and contained — they never disturb other subscribers
 * or later messages on the topic.
 */
public final class InMemoryAgentMessageBus implements AgentMessageBus, AutoCloseable {

    public static final int DEFAULT_QUEUE_CAPACITY = 256;

    private static final System.Logger LOG = System.getLogger(InMemoryAgentMessageBus.class.getName());

    private final int queueCapacity;
    private final ExecutorService dispatcher;
    private final ConcurrentHashMap<TopicKey, CopyOnWriteArrayList<Subscriber>> subscribersByTopic =
            new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public InMemoryAgentMessageBus() {
        this(DEFAULT_QUEUE_CAPACITY);
    }

    public InMemoryAgentMessageBus(int queueCapacity) {
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be >= 1");
        }
        this.queueCapacity = queueCapacity;
        this.dispatcher = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "agent-message-bus-dispatcher");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void publish(AgentMessage message) {
        Objects.requireNonNull(message, "message is required");
        ensureOpen();
        List<Subscriber> matching = subscribersByTopic.get(new TopicKey(message.tenantId(), message.topic()));
        if (matching == null) {
            return;
        }
        for (Subscriber subscriber : matching) {
            subscriber.enqueue(message);
            try {
                // One drain task per enqueue: tasks >= queued messages, so every
                // queued message is eventually polled; surplus tasks poll null.
                dispatcher.execute(subscriber::deliverOne);
            } catch (RejectedExecutionException e) {
                // Bus closed concurrently; pending deliveries are intentionally abandoned.
                return;
            }
        }
    }

    @Override
    public Subscription subscribe(String tenantId, String topic, AgentMessageHandler handler) {
        requireNonBlank(tenantId, "tenantId");
        requireNonBlank(topic, "topic");
        Objects.requireNonNull(handler, "handler is required");
        ensureOpen();
        TopicKey key = new TopicKey(tenantId, topic);
        Subscriber subscriber = new Subscriber(key, handler);
        subscribersByTopic.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(subscriber);
        return subscriber;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            dispatcher.shutdownNow();
            subscribersByTopic.clear();
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("message bus is closed");
        }
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private record TopicKey(String tenantId, String topic) { }

    private final class Subscriber implements Subscription {

        private final TopicKey key;
        private final AgentMessageHandler handler;
        /** Guarded by itself; bounded at {@code queueCapacity}. */
        private final ArrayDeque<AgentMessage> queue = new ArrayDeque<>();
        private final AtomicLong dropped = new AtomicLong();
        private volatile boolean cancelled;

        private Subscriber(TopicKey key, AgentMessageHandler handler) {
            this.key = key;
            this.handler = handler;
        }

        private void enqueue(AgentMessage message) {
            synchronized (queue) {
                if (queue.size() == queueCapacity) {
                    queue.pollFirst();
                    if (dropped.getAndIncrement() == 0) {
                        LOG.log(System.Logger.Level.WARNING,
                                "Subscriber queue full (capacity {0}) on tenant={1} topic={2}; dropping oldest"
                                        + " message — subsequent drops are counted without further logging",
                                queueCapacity, key.tenantId(), key.topic());
                    }
                }
                queue.addLast(message);
            }
        }

        /** Runs on the shared dispatcher thread only. */
        private void deliverOne() {
            if (cancelled) {
                return;
            }
            AgentMessage message;
            synchronized (queue) {
                message = queue.pollFirst();
            }
            if (message == null) {
                return;
            }
            try {
                handler.onMessage(message);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Message handler failed on tenant=" + key.tenantId() + " topic=" + key.topic()
                                + " messageId=" + message.messageId() + "; topic and other subscribers continue", e);
            }
        }

        @Override
        public long droppedCount() {
            return dropped.get();
        }

        @Override
        public void close() {
            cancelled = true;
            List<Subscriber> list = subscribersByTopic.get(key);
            if (list != null) {
                list.remove(this);
            }
        }
    }
}
