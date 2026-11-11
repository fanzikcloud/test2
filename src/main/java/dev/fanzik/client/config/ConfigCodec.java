package dev.fanzik.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.fanzik.client.module.Category;
import dev.fanzik.client.render.Palette;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ConfigCodec {
    public static final int SCHEMA_VERSION = 1;
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,47}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final String clientVersion;
    private final Clock clock;

    public ConfigCodec(String clientVersion) {
        this(clientVersion, Clock.systemUTC());
    }

    public ConfigCodec(String clientVersion, Clock clock) {
        this.clientVersion = Objects.requireNonNull(clientVersion);
        this.clock = Objects.requireNonNull(clock);
    }

    public String encode(ConfigSnapshot snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("clientVersion", clientVersion);
        root.addProperty("savedAt", clock.instant().toString());
        JsonObject modules = new JsonObject();
        snapshot.modules().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            JsonObject module = new JsonObject();
            module.addProperty("enabled", entry.getValue().enabled());
            module.addProperty("key", entry.getValue().key());
            JsonObject settings = new JsonObject();
            entry.getValue().settings().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(setting -> settings.add(setting.getKey(), setting.getValue()));
            module.add("settings", settings);
            modules.add(entry.getKey(), module);
        });
        root.add("modules", modules);
        JsonObject ui = new JsonObject();
        ui.addProperty("palette", snapshot.ui().palette().name());
        ui.addProperty("category", snapshot.ui().category().name());
        ui.addProperty("horizontalPosition", snapshot.ui().horizontalPosition());
        ui.addProperty("verticalPosition", snapshot.ui().verticalPosition());
        root.add("ui", ui);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public ConfigSnapshot decode(String json) throws ConfigFormatException {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            JsonObject root = object(parsed, "root");
            int version = integer(root.get("schemaVersion"), "schemaVersion");
            if (version != SCHEMA_VERSION) {
                throw new ConfigFormatException("Unsupported config schema: " + version);
            }
            JsonObject modules = object(root.get("modules"), "modules");
            if (modules.size() > 256) {
                throw new ConfigFormatException("Too many modules");
            }
            Map<String, ModuleState> states = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : modules.entrySet()) {
                requireId(entry.getKey());
                JsonObject module = object(entry.getValue(), entry.getKey());
                boolean enabled = module.has("enabled") && bool(module.get("enabled"), "enabled");
                int key = module.has("key") ? integer(module.get("key"), "key") : -1;
                JsonObject settings = module.has("settings") ? object(module.get("settings"), "settings") : new JsonObject();
                if (settings.size() > 128) {
                    throw new ConfigFormatException("Too many settings in " + entry.getKey());
                }
                Map<String, JsonElement> values = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> setting : settings.entrySet()) {
                    requireId(setting.getKey());
                    if (!setting.getValue().isJsonPrimitive()) {
                        throw new ConfigFormatException("Setting " + setting.getKey() + " must be a primitive value");
                    }
                    JsonPrimitive primitive = setting.getValue().getAsJsonPrimitive();
                    if (primitive.isString() && primitive.getAsString().length() > 1024) {
                        throw new ConfigFormatException("Setting text is too long");
                    }
                    if (primitive.isNumber() && !Double.isFinite(primitive.getAsDouble())) {
                        throw new ConfigFormatException("Setting numbers must be finite");
                    }
                    values.put(setting.getKey(), primitive);
                }
                states.put(entry.getKey(), new ModuleState(enabled, key, values));
            }
            GuiState ui = GuiState.defaults();
            if (root.has("ui")) {
                JsonObject object = object(root.get("ui"), "ui");
                ui = new GuiState(
                        object.has("palette") ? Palette.valueOf(string(object.get("palette"), "palette")) : ui.palette(),
                        object.has("category") ? Category.valueOf(string(object.get("category"), "category")) : ui.category(),
                        object.has("horizontalPosition") ? number(object.get("horizontalPosition"), "horizontalPosition") : ui.horizontalPosition(),
                        object.has("verticalPosition") ? number(object.get("verticalPosition"), "verticalPosition") : ui.verticalPosition());
            }
            return new ConfigSnapshot(states, ui);
        } catch (ConfigFormatException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ConfigFormatException("Invalid config: " + exception.getMessage(), exception);
        }
    }

    private static void requireId(String id) throws ConfigFormatException {
        if (!ID.matcher(id).matches()) {
            throw new ConfigFormatException("Invalid identifier: " + id);
        }
    }

    private static JsonObject object(JsonElement element, String field) throws ConfigFormatException {
        if (element == null || !element.isJsonObject()) {
            throw new ConfigFormatException(field + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static String string(JsonElement element, String field) throws ConfigFormatException {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new ConfigFormatException(field + " must be a string");
        }
        return element.getAsString();
    }

    private static double number(JsonElement element, String field) throws ConfigFormatException {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new ConfigFormatException(field + " must be a number");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            throw new ConfigFormatException(field + " must be finite");
        }
        return value;
    }

    private static int integer(JsonElement element, String field) throws ConfigFormatException {
        number(element, field);
        try {
            return new BigDecimal(element.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new ConfigFormatException(field + " must be a 32-bit integer", exception);
        }
    }

    private static boolean bool(JsonElement element, String field) throws ConfigFormatException {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new ConfigFormatException(field + " must be a boolean");
        }
        return element.getAsBoolean();
    }
}
