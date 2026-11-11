package dev.fanzik.client.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {
    private final List<RuntimeException> errors = new ArrayList<>();
    private final EventBus bus = new EventBus(errors::add);

    @Test
    void dispatchesByTypeIncludingInterfaces() {
        AtomicInteger strings = new AtomicInteger();
        AtomicInteger sequences = new AtomicInteger();
        bus.subscribe(String.class, value -> strings.incrementAndGet());
        bus.subscribe(CharSequence.class, value -> sequences.incrementAndGet());
        assertEquals("hello", bus.post("hello"));
        bus.post(42);
        assertEquals(1, strings.get());
        assertEquals(1, sequences.get());
    }

    @Test
    void priorityIsDescendingAndEqualPrioritiesAreStable() {
        List<String> order = new ArrayList<>();
        bus.subscribe(String.class, 0, event -> order.add("first"));
        bus.subscribe(String.class, 5, event -> order.add("high"));
        bus.subscribe(String.class, 0, event -> order.add("second"));
        bus.subscribe(String.class, -1, event -> order.add("last"));
        bus.post("x");
        assertEquals(List.of("high", "first", "second", "last"), order);
    }

    @Test
    void subscriptionsCanBeClosedMoreThanOnce() {
        AtomicInteger calls = new AtomicInteger();
        Subscription subscription = bus.subscribe(String.class, event -> calls.incrementAndGet());
        subscription.close();
        subscription.close();
        bus.post("x");
        assertEquals(0, calls.get());
        assertEquals(0, bus.listenerCount());
    }

    @Test
    void unsubscribeDuringDispatchSkipsTheRemovedListener() {
        AtomicInteger calls = new AtomicInteger();
        Subscription later = bus.subscribe(String.class, event -> calls.incrementAndGet());
        bus.subscribe(String.class, 1, event -> later.close());
        bus.post("x");
        assertEquals(0, calls.get());
    }

    @Test
    void subscribeDuringDispatchAppliesOnTheNextEvent() {
        AtomicInteger calls = new AtomicInteger();
        Subscription first = bus.subscribe(String.class, event -> bus.subscribe(String.class, next -> calls.incrementAndGet()));
        bus.post("first");
        assertEquals(0, calls.get());
        first.close();
        bus.post("second");
        assertEquals(1, calls.get());
    }

    @Test
    void reportsFailuresWithoutSkippingOtherListeners() {
        AtomicInteger calls = new AtomicInteger();
        bus.subscribe(String.class, event -> { throw new IllegalStateException("failure"); });
        bus.subscribe(String.class, event -> calls.incrementAndGet());
        bus.post("x");
        assertEquals(1, errors.size());
        assertEquals("failure", errors.getFirst().getMessage());
        assertEquals(1, calls.get());
    }

    @Test
    void doesNotSwallowFatalErrors() {
        bus.subscribe(String.class, event -> { throw new AssertionError("fatal"); });
        assertThrows(AssertionError.class, () -> bus.post("x"));
    }

    @Test
    void clearDisposesAllListeners() {
        bus.subscribe(String.class, event -> fail("Must not be called"));
        bus.clear();
        bus.post("x");
        assertEquals(0, bus.listenerCount());
    }

    @Test
    void rejectsNullEventsAndListeners() {
        assertThrows(NullPointerException.class, () -> bus.post(null));
        assertThrows(NullPointerException.class, () -> bus.subscribe(String.class, null));
    }
}
