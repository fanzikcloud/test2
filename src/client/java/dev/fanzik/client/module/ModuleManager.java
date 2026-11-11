package dev.fanzik.client.module;

import com.google.gson.JsonElement;
import dev.fanzik.client.config.ClientPreferences;
import dev.fanzik.client.config.ConfigSnapshot;
import dev.fanzik.client.config.ModuleState;
import dev.fanzik.client.event.Subscription;
import dev.fanzik.client.setting.Setting;
import net.minecraft.client.resource.language.I18n;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ModuleManager implements AutoCloseable {
    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final Map<String, Subscription> subscriptions = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final ClientPreferences preferences;
    private final Subscription preferencesSubscription;
    private int batchDepth;
    private boolean pendingChange;

    public ModuleManager(ClientPreferences preferences) {
        this.preferences = preferences;
        preferencesSubscription = preferences.onChange(this::changed);
    }

    public <M extends Module> M register(M module) {
        if (modules.putIfAbsent(module.id(), module) != null) {
            throw new IllegalArgumentException("Duplicate module id: " + module.id());
        }
        subscriptions.put(module.id(), module.onChange(this::changed));
        changed();
        return module;
    }

    public Module find(String id) {
        String normalized = id.toLowerCase(Locale.ROOT).replace('_', '-');
        Module direct = modules.get(normalized);
        if (direct != null) {
            return direct;
        }
        String compact = normalized.replace("-", "").replace(" ", "");
        return modules.values().stream().filter(module -> module.id().replace("-", "").equals(compact)).findFirst().orElse(null);
    }

    public List<Module> all() {
        return List.copyOf(modules.values());
    }

    public List<Module> in(Category category) {
        return modules.values().stream().filter(module -> module.category() == category).toList();
    }

    public List<Module> search(Category category, String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return modules.values().stream().filter(module -> needle.isEmpty() ? module.category() == category
                : module.id().contains(needle) || I18n.translate(module.nameKey()).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    public long enabledCount() {
        return modules.values().stream().filter(Module::enabled).count();
    }

    public Subscription onChange(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public ConfigSnapshot snapshot() {
        Map<String, ModuleState> states = new LinkedHashMap<>();
        modules.forEach((id, module) -> states.put(id, module.snapshot()));
        return new ConfigSnapshot(states, preferences.snapshot());
    }

    public ApplyResult apply(ConfigSnapshot snapshot) {
        int unknownModules = 0;
        int unknownSettings = 0;
        for (Map.Entry<String, ModuleState> entry : snapshot.modules().entrySet()) {
            Module module = modules.get(entry.getKey());
            if (module == null) {
                unknownModules++;
                continue;
            }
            for (Map.Entry<String, JsonElement> value : entry.getValue().settings().entrySet()) {
                Setting<?> setting = module.setting(value.getKey());
                if (setting == null) {
                    unknownSettings++;
                } else {
                    setting.validateJson(value.getValue());
                }
            }
        }
        ConfigSnapshot previous = snapshot();
        batchDepth++;
        try {
            applyValidated(snapshot);
        } catch (RuntimeException failure) {
            try {
                applyValidated(previous);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            batchDepth--;
            if (batchDepth == 0 && pendingChange) {
                pendingChange = false;
                changed();
            }
        }
        return new ApplyResult(unknownModules, unknownSettings);
    }

    private void applyValidated(ConfigSnapshot snapshot) {
        modules.values().forEach(module -> module.setEnabled(false));
        for (Module module : modules.values()) {
            ModuleState state = snapshot.modules().getOrDefault(module.id(), module.defaults());
            module.resetSettings();
            for (Map.Entry<String, JsonElement> entry : state.settings().entrySet()) {
                Setting<?> setting = module.setting(entry.getKey());
                if (setting != null) {
                    setting.applyJson(entry.getValue());
                }
            }
            module.key(state.key());
        }
        preferences.apply(snapshot.ui());
        modules.values().forEach(module -> module.setEnabled(snapshot.modules().getOrDefault(module.id(), module.defaults()).enabled()));
    }

    private void changed() {
        if (batchDepth > 0) {
            pendingChange = true;
        } else {
            listeners.forEach(Runnable::run);
        }
    }

    @Override
    public void close() {
        List<RuntimeException> failures = new ArrayList<>();
        modules.values().forEach(module -> {
            try {
                module.close();
            } catch (RuntimeException exception) {
                failures.add(exception);
            }
        });
        subscriptions.values().forEach(Subscription::close);
        subscriptions.clear();
        preferencesSubscription.close();
        listeners.clear();
        if (!failures.isEmpty()) {
            RuntimeException failure = new IllegalStateException("Module cleanup failed");
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    public record ApplyResult(int unknownModules, int unknownSettings) {
    }
}
