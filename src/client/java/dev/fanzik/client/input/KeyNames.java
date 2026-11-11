package dev.fanzik.client.input;

import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.Map;

public final class KeyNames {
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("rshift", "right.shift"), Map.entry("rightshift", "right.shift"),
            Map.entry("lshift", "left.shift"), Map.entry("leftshift", "left.shift"),
            Map.entry("rctrl", "right.control"), Map.entry("lctrl", "left.control"),
            Map.entry("ralt", "right.alt"), Map.entry("lalt", "left.alt"),
            Map.entry("ctrl", "left.control"), Map.entry("shift", "left.shift"),
            Map.entry("alt", "left.alt"), Map.entry("esc", "escape"),
            Map.entry("del", "delete"), Map.entry("ins", "insert"),
            Map.entry("pgup", "page.up"), Map.entry("pgdn", "page.down"));

    private KeyNames() {
        throw new AssertionError();
    }

    public static String label(int key) {
        return key == GLFW.GLFW_KEY_UNKNOWN ? "—" : InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
    }

    public static int parse(String input) {
        String name = input.toLowerCase(Locale.ROOT).replace('_', '.').replace('-', '.');
        if (name.equals("none") || name.equals("off") || name.equals("unbound")) {
            return GLFW.GLFW_KEY_UNKNOWN;
        }
        name = ALIASES.getOrDefault(name, name);
        String translationKey = name.startsWith("key.keyboard.") ? name : "key.keyboard." + name;
        try {
            InputUtil.Key key = InputUtil.fromTranslationKey(translationKey);
            if (key.getCategory() == InputUtil.Type.KEYSYM && key.getCode() >= 32 && key.getCode() <= GLFW.GLFW_KEY_LAST) {
                return key.getCode();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown keyboard key: " + input, exception);
        }
        throw new IllegalArgumentException("Unknown keyboard key: " + input);
    }
}
