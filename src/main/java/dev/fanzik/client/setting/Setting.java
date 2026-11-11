package dev.fanzik.client.setting;

import com.google.gson.JsonElement;
import dev.fanzik.client.event.Subscription;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

public abstract class Setting<T> {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,47}");
    private final String id;
    private final String nameKey;
    private final String descriptionKey;
    private final T defaultValue;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private BooleanSupplier visibility = () -> true;
    private T value;

    protected Setting(String id, String nameKey, String descriptionKey, T defaultValue) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid setting id: " + id);
        }
        this.id = id;
        this.nameKey = Objects.requireNonNull(nameKey);
        this.descriptionKey = Objects.requireNonNull(descriptionKey);
        this.defaultValue = Objects.requireNonNull(defaultValue);
        this.value = defaultValue;
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

    public final T get() {
        return value;
    }

    public final T defaultValue() {
        return defaultValue;
    }

    public final void set(T value) {
        T normalized = normalize(Objects.requireNonNull(value));
        if (!Objects.equals(this.value, normalized)) {
            this.value = normalized;
            listeners.forEach(Runnable::run);
        }
    }

    public final void reset() {
        set(defaultValue);
    }

    public final Subscription onChange(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener));
        return () -> listeners.remove(listener);
    }

    public final Setting<T> visibleWhen(BooleanSupplier visibility) {
        this.visibility = Objects.requireNonNull(visibility);
        return this;
    }

    public final boolean visible() {
        return visibility.getAsBoolean();
    }

    public final void applyJson(JsonElement element) {
        set(decode(element));
    }

    public final void validateJson(JsonElement element) {
        normalize(decode(element));
    }

    protected abstract T normalize(T value);

    public final JsonElement encode() {
        return encodeValue(value);
    }

    public final JsonElement defaultJson() {
        return encodeValue(defaultValue);
    }

    protected abstract JsonElement encodeValue(T value);

    public abstract T decode(JsonElement element);
}
