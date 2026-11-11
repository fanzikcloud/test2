package dev.fanzik.client.event;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class EventBus {
    private final CopyOnWriteArrayList<Binding<?>> bindings = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Consumer<RuntimeException> errorHandler;

    public EventBus(Consumer<RuntimeException> errorHandler) {
        this.errorHandler = Objects.requireNonNull(errorHandler);
    }

    public <E> Subscription subscribe(Class<E> type, Consumer<? super E> listener) {
        return subscribe(type, 0, listener);
    }

    public synchronized <E> Subscription subscribe(Class<E> type, int priority, Consumer<? super E> listener) {
        Binding<E> binding = new Binding<>(Objects.requireNonNull(type), Objects.requireNonNull(listener),
                priority, sequence.getAndIncrement());
        bindings.add(binding);
        bindings.sort(Comparator.<Binding<?>>comparingInt(bindingValue -> bindingValue.priority).reversed()
                .thenComparingLong(bindingValue -> bindingValue.sequence));
        return () -> {
            binding.active.set(false);
            bindings.remove(binding);
        };
    }

    public <E> E post(E event) {
        Objects.requireNonNull(event);
        for (Binding<?> binding : bindings) {
            if (binding.active.get() && binding.type.isInstance(event)) {
                try {
                    binding.dispatch(event);
                } catch (RuntimeException exception) {
                    errorHandler.accept(exception);
                }
            }
        }
        return event;
    }

    public int listenerCount() {
        return bindings.size();
    }

    public void clear() {
        bindings.forEach(binding -> binding.active.set(false));
        bindings.clear();
    }

    private static final class Binding<E> {
        private final Class<E> type;
        private final Consumer<? super E> listener;
        private final int priority;
        private final long sequence;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Binding(Class<E> type, Consumer<? super E> listener, int priority, long sequence) {
            this.type = type;
            this.listener = listener;
            this.priority = priority;
            this.sequence = sequence;
        }

        private void dispatch(Object event) {
            listener.accept(type.cast(event));
        }
    }
}
