package dev.fanzik.client.config;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ConfigName {
    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,47}");
    private static final Set<String> RESERVED = Set.of("con", "prn", "aux", "nul", "com1", "com2", "com3", "com4",
            "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private ConfigName() {
        throw new AssertionError();
    }

    public static String normalize(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Config name is required");
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        if (!VALID.matcher(normalized).matches() || RESERVED.contains(normalized)) {
            throw new IllegalArgumentException("Use 1–48 Latin letters, digits, '-' or '_'; Windows device names are not allowed");
        }
        return normalized;
    }
}
