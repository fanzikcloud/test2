package dev.fanzik.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String id, String nameKey, String descriptionKey, boolean defaultValue) {
        super(id, nameKey, descriptionKey, defaultValue);
    }

    public void toggle() {
        set(!get());
    }

    @Override
    protected Boolean normalize(Boolean value) {
        return value;
    }

    @Override
    protected JsonElement encodeValue(Boolean value) {
        return new JsonPrimitive(value);
    }

    @Override
    public Boolean decode(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Setting " + id() + " must be a boolean");
        }
        return element.getAsBoolean();
    }
}
