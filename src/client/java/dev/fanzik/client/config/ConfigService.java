package dev.fanzik.client.config;

import dev.fanzik.client.event.Subscription;
import dev.fanzik.client.module.ModuleManager;
import dev.fanzik.client.util.DirectoryOpener;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ConfigService implements AutoCloseable {
    private final MinecraftClient mc;
    private final ModuleManager modules;
    private final ConfigRepository repository;
    private final Logger logger;
    private final Consumer<Throwable> failureReporter;
    private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "fanzik-config-io");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Subscription changeSubscription;
    private volatile List<ConfigEntry> profiles = List.of();
    private boolean restoring;
    private boolean dirty;
    private boolean writing;
    private boolean failureReported;
    private long saveAt;

    public ConfigService(MinecraftClient mc, ModuleManager modules, ConfigRepository repository,
                         Logger logger, Consumer<Throwable> failureReporter) {
        this.mc = mc;
        this.modules = modules;
        this.repository = repository;
        this.logger = logger;
        this.failureReporter = failureReporter;
        changeSubscription = modules.onChange(this::markDirty);
    }

    public void restore() {
        restoring = true;
        try {
            repository.loadState().ifPresent(modules::apply);
        } catch (IOException | RuntimeException exception) {
            logger.error("Cannot restore Fanzik state", exception);
            failureReporter.accept(exception);
            try {
                Path backup = repository.quarantineState();
                logger.warn("Invalid state retained at {}", backup);
            } catch (IOException backupFailure) {
                logger.warn("Cannot retain invalid state", backupFailure);
            }
        } finally {
            restoring = false;
            dirty = false;
        }
        refresh().exceptionally(exception -> {
            logger.error("Cannot list Fanzik configs", exception);
            return List.of();
        });
    }

    public boolean pending() {
        return dirty || writing;
    }

    public void markDirty() {
        if (!restoring && !closed.get()) {
            dirty = true;
            saveAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(750);
        }
    }

    public void tick() {
        if (closed.get() || !dirty || writing || System.nanoTime() < saveAt) {
            return;
        }
        ConfigSnapshot snapshot = modules.snapshot();
        dirty = false;
        writing = true;
        runIo(() -> repository.saveState(snapshot)).whenComplete((ignored, failure) -> mc.execute(() -> {
            writing = false;
            if (failure != null) {
                dirty = true;
                saveAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                logger.error("Cannot autosave Fanzik state", failure);
                if (!failureReported) {
                    failureReported = true;
                    failureReporter.accept(failure);
                }
            } else {
                failureReported = false;
            }
        }));
    }

    public CompletableFuture<Void> save(String name) {
        String normalized = ConfigName.normalize(name);
        ConfigSnapshot snapshot = modules.snapshot();
        return runIo(() -> {
            repository.save(normalized, snapshot);
            profiles = repository.list();
        });
    }

    public CompletableFuture<ModuleManager.ApplyResult> load(String name) {
        String normalized = ConfigName.normalize(name);
        return supplyIo(() -> repository.load(normalized)).thenCompose(snapshot -> onClient(() -> modules.apply(snapshot)));
    }

    public CompletableFuture<List<ConfigEntry>> refresh() {
        return supplyIo(() -> {
            List<ConfigEntry> entries = repository.list();
            profiles = entries;
            return entries;
        });
    }

    public List<ConfigEntry> cachedProfiles() {
        return profiles;
    }

    public CompletableFuture<Void> openDirectory() {
        return runIo(() -> DirectoryOpener.open(repository.directory()));
    }

    public Path directory() {
        return repository.directory();
    }

    public CompletableFuture<Void> flush() {
        ConfigSnapshot snapshot = modules.snapshot();
        return runIo(() -> repository.saveState(snapshot));
    }

    private CompletableFuture<Void> runIo(IoAction action) {
        return supplyIo(() -> {
            action.run();
            return null;
        });
    }

    private <T> CompletableFuture<T> supplyIo(IoSupplier<T> supplier) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Config service is closed"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }, io);
    }

    private <T> CompletableFuture<T> onClient(Supplier<T> supplier) {
        CompletableFuture<T> result = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                result.complete(supplier.get());
            } catch (RuntimeException exception) {
                result.completeExceptionally(exception);
            }
        });
        return result;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        changeSubscription.close();
        ConfigSnapshot snapshot = modules.snapshot();
        io.submit(() -> {
            try {
                repository.saveState(snapshot);
            } catch (IOException exception) {
                logger.error("Cannot save final Fanzik state", exception);
            }
        });
        io.shutdown();
        try {
            if (!io.awaitTermination(8, TimeUnit.SECONDS)) {
                logger.warn("Config IO did not finish before shutdown");
                io.shutdownNow();
            }
        } catch (InterruptedException exception) {
            io.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
