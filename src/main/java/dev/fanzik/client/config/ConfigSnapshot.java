package dev.fanzik.client.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ConfigSnapshot(Map<String, ModuleState> modules, GuiState ui) {
    public ConfigSnapshot {
        modules = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(modules)));
        modules.forEach((id, state) -> {
            Objects.requireNonNull(id);
            Objects.requireNonNull(state);
        });
        Objects.requireNonNull(ui);
    }
}
