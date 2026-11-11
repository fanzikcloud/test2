package dev.fanzik.client.module;

import com.google.gson.JsonElement;
import dev.fanzik.client.config.ModuleState;
import dev.fanzik.client.event.EventBus;
import dev.fanzik.client.event.Subscription;
import dev.fanzik.client.setting.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public abstract class Module implements AutoCloseable {
    protected final MinecraftClient mc;
    private final EventBus events;
    private final String id;
    private final String nameKey;
    private final String descriptionKey;
    private final Category category;
    private final Map<String, Setting<?>> settings = new LinkedHashMap<>();
    private final List<Subscription> activeSubscriptions = new ArrayList<>();
    private final List<Subscription> settingSubscriptions = new ArrayList<>();
    private final CopyOnWriteArrayList<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private boolean enabled;
    private int key = -1;

    protected Module(MinecraftClient mc, EventBus events, String id, String nameKey, String descriptionKey, Category category) {
        if (id == null || !id.matches("[a-z][a-z0-9-]{0,47}")) {
            throw new IllegalArgumentException("Invalid module id: " + id);
        }
        this.mc = Objects.requireNonNull(mc);
        this.events = Objects.requireNonNull(events);
        this.id = id;
        this.nameKey = Objects.requireNonNull(nameKey);
        this.descriptionKey = Objects.requireNonNull(descriptionKey);
        this.category = Objects.requireNonNull(category);
    }

    public final String id() {
        return id;
    }

    public final String nameKey() {
        return nameKey;
    }

    public final String descriptionKey() {
        return descriptionKey;
    }

    public final Category category() {
        return category;
    }

    public final boolean enabled() {
        return enabled;
    }

    public final int key() {
        return key;
    }

    public final void key(int key) {
        if (key != -1 && (key < 32 || key > 348)) {
            throw new IllegalArgumentException("Invalid keyboard key: " + key);
        }
        if (this.key != key) {
            this.key = key;
            changed();
        }
    }

    public final List<Setting<?>> settings() {
        return List.copyOf(settings.values());
    }

    public final Setting<?> setting(String id) {
        return settings.get(id);
    }

    protected final <S extends Setting<?>> S add(S setting) {
        if (settings.putIfAbsent(setting.id(), setting) != null) {
            throw new IllegalArgumentException("Duplicate setting id: " + setting.id());
        }
        settingSubscriptions.add(setting.onChange(this::changed));
        return setting;
    }

    public final Subscription onChange(Runnable listener) {
        changeListeners.add(Objects.requireNonNull(listener));
        return () -> changeListeners.remove(listener);
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    public final void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            try {
                onEnable();
            } catch (RuntimeException exception) {
                this.enabled = false;
                clearActiveSubscriptions();
                try {
                    onDisable();
                } catch (RuntimeException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
                changed();
                throw new ModuleExecutionException(id, exception);
            }
        } else {
            clearActiveSubscriptions();
            try {
                onDisable();
            } finally {
                changed();
            }
            return;
        }
        changed();
    }

    protected final <E> void listen(Class<E> eventType, Consumer<E> listener) {
        if (!enabled) {
            throw new IllegalStateException("Subscriptions can only be added while the module is enabled");
        }
        activeSubscriptions.add(events.subscribe(eventType, event -> {
            if (enabled) {
                try {
                    listener.accept(event);
                } catch (RuntimeException exception) {
                    try {
                        setEnabled(false);
                    } catch (RuntimeException cleanupFailure) {
                        exception.addSuppressed(cleanupFailure);
                    }
                    throw new ModuleExecutionException(id, exception);
                }
            }
        }));
    }

    public final ModuleState snapshot() {
        Map<String, JsonElement> values = new LinkedHashMap<>();
        settings.forEach((id, setting) -> values.put(id, setting.encode()));
        return new ModuleState(enabled, key, values);
    }

    public final ModuleState defaults() {
        Map<String, JsonElement> values = new LinkedHashMap<>();
        settings.forEach((id, setting) -> values.put(id, setting.defaultJson()));
        return new ModuleState(false, -1, values);
    }

    public final void resetSettings() {
        settings.values().forEach(Setting::reset);
    }

    protected abstract void onEnable();

    protected abstract void onDisable();

    private void changed() {
        changeListeners.forEach(Runnable::run);
    }

    private void clearActiveSubscriptions() {
        activeSubscriptions.forEach(Subscription::close);
        activeSubscriptions.clear();
    }

    @Override
    public final void close() {
        try {
            setEnabled(false);
        } finally {
            clearActiveSubscriptions();
            settingSubscriptions.forEach(Subscription::close);
            settingSubscriptions.clear();
            changeListeners.clear();
        }
    }
}
