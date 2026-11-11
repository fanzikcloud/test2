package dev.fanzik.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.Locale;

public final class EnumSetting<E extends Enum<E>> extends Setting<E> {
    private final Class<E> enumType;
    private final List<E> options;

    public EnumSetting(String id, String nameKey, String descriptionKey, E defaultValue) {
        super(id, nameKey, descriptionKey, defaultValue);
        enumType = defaultValue.getDeclaringClass();
        options = List.of(enumType.getEnumConstants());
    }

    public List<E> options() {
        return options;
    }

    public void cycle(int direction) {
        int index = Math.floorMod(get().ordinal() + direction, options.size());
        set(options.get(index));
    }

    public String valueKey() {
        return get() instanceof Labelled labelled ? labelled.translationKey() : get().name();
    }

    @Override
    protected E normalize(E value) {
        if (value.getDeclaringClass() != enumType) {
            throw new IllegalArgumentException("Invalid enum type for " + id());
        }
        return value;
    }

    @Override
    protected JsonElement encodeValue(E value) {
        return new JsonPrimitive(value.name());
    }

    @Override
    public E decode(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Setting " + id() + " must be an enum name");
        }
        try {
            return Enum.valueOf(enumType, element.getAsString().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown value for setting " + id() + ": " + element.getAsString(), exception);
        }
    }
}
