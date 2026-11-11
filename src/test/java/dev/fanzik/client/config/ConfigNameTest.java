package dev.fanzik.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ConfigNameTest {
    @ParameterizedTest
    @ValueSource(strings = {"fanzik", "pvp-1", "a_b", "123", "x", "CONfig"})
    void acceptsPortableNames(String name) {
        assertEquals(name.toLowerCase(java.util.Locale.ROOT), ConfigName.normalize(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ".", "..", "../outside", "a/b", "a\\b", "a.json", "with space", " имя", "имя", "a:", "_a", "-a", "con", "CON", "prn", "aux", "nul", "COM1", "com9", "lpt1", "lpt9", "a\n", "a "})
    void rejectsUnsafeOrWindowsReservedNames(String name) {
        assertThrows(IllegalArgumentException.class, () -> ConfigName.normalize(name));
    }

    @Test
    void enforcesTheLengthAndNullRules() {
        assertEquals(48, ConfigName.normalize("a".repeat(48)).length());
        assertThrows(IllegalArgumentException.class, () -> ConfigName.normalize("a".repeat(49)));
        assertThrows(IllegalArgumentException.class, () -> ConfigName.normalize(null));
    }
}
