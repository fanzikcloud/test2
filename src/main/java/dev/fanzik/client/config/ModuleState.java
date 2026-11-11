package dev.fanzik.client.config;

import com.google.gson.JsonElement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ModuleState(boolean enabled, int key, Map<String, JsonElement> settings) {
    public ModuleState {
        if (key != -1 && (key < 32 || key > 348)) {
            throw new IllegalArgumentException("Invalid keyboard key: " + key);
        }
        LinkedHashMap<String, JsonElement> copy = new LinkedHashMap<>();
        Objects.requireNonNull(settings).forEach((id, value) -> copy.put(Objects.requireNonNull(id),
                Objects.requireNonNull(value).deepCopy()));
        settings = Collections.unmodifiableMap(copy);
    }

    @Override
    public Map<String, JsonElement> settings() {
        LinkedHashMap<String, JsonElement> copy = new LinkedHashMap<>();
        settings.forEach((id, value) -> copy.put(id, value.deepCopy()));
        return Collections.unmodifiableMap(copy);
    }
}
