package dev.fanzik.client.event;

@FunctionalInterface
public interface Subscription extends AutoCloseable {
    @Override
    void close();
}
