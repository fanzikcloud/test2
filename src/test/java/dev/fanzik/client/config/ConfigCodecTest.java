package dev.fanzik.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.fanzik.client.module.Category;
import dev.fanzik.client.render.Palette;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigCodecTest {
    private final ConfigCodec codec = new ConfigCodec("1.0.0", Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC));

    static ConfigSnapshot snapshot(boolean enabled) {
        return new ConfigSnapshot(Map.of("auto-sprint", new ModuleState(enabled, 82,
                Map.of("pause-in-screens", new JsonPrimitive(true), "forward-threshold", new JsonPrimitive(0.8), "start-policy", new JsonPrimitive("NORMAL")))),
                new GuiState(Palette.MINT, Category.MOVEMENT, 0.3, 0.7));
    }

    @Test
    void roundTripsCompleteStateAndMetadata() throws Exception {
        String json = codec.encode(snapshot(true));
        ConfigSnapshot result = codec.decode(json);
        assertEquals(snapshot(true), result);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(1, root.get("schemaVersion").getAsInt());
        assertEquals("2026-11-11T12:00:00Z", root.get("savedAt").getAsString());
        assertEquals("1.0.0", root.get("clientVersion").getAsString());
    }

    @Test
    void missingOptionalFieldsUseSafeDefaults() throws Exception {
        ConfigSnapshot result = codec.decode("{\"schemaVersion\":1,\"modules\":{\"auto-sprint\":{}}}");
        assertEquals(new ModuleState(false, -1, Map.of()), result.modules().get("auto-sprint"));
        assertEquals(GuiState.defaults(), result.ui());
    }

    @Test
    void preservesUnknownModulesAndSettingsForTheRegistryToHandle() throws Exception {
        ConfigSnapshot result = codec.decode("{\"schemaVersion\":1,\"modules\":{\"future-module\":{\"settings\":{\"future-setting\":true}}}}");
        assertTrue(result.modules().containsKey("future-module"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "[]", "null", "{}", "{\"schemaVersion\":2,\"modules\":{}}", "{\"schemaVersion\":1.1,\"modules\":{}}", "{\"schemaVersion\":\"1\",\"modules\":{}}", "{\"schemaVersion\":1,\"modules\":[]}"})
    void rejectsInvalidDocuments(String json) {
        assertThrows(ConfigFormatException.class, () -> codec.decode(json));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"enabled\":\"true\"}", "{\"enabled\":null}", "{\"key\":1.5}", "{\"key\":999}", "{\"key\":0}", "{\"key\":-2}", "{\"key\":1e40}", "{\"key\":\"82\"}", "{\"settings\":[]}", "{\"settings\":{\"nested\":{}}}", "{\"settings\":{\"null-value\":null}}", "{\"settings\":{\"huge\":1e999}}"})
    void rejectsMalformedModuleState(String module) {
        assertThrows(ConfigFormatException.class, () -> codec.decode("{\"schemaVersion\":1,\"modules\":{\"auto-sprint\":" + module + "}}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"palette\":\"UNKNOWN\"}", "{\"category\":\"UNKNOWN\"}", "{\"horizontalPosition\":2}", "{\"verticalPosition\":-1}", "{\"horizontalPosition\":\"0.5\"}"})
    void rejectsMalformedUiState(String ui) {
        assertThrows(ConfigFormatException.class, () -> codec.decode("{\"schemaVersion\":1,\"modules\":{},\"ui\":" + ui + "}"));
    }

    @Test
    void snapshotMapsAreImmutableAndJsonValuesAreDefensivelyCopied() {
        JsonObject value = new JsonObject();
        value.addProperty("x", 1);
        ModuleState state = new ModuleState(false, -1, Map.of("value", value));
        value.addProperty("x", 2);
        assertEquals(1, state.settings().get("value").getAsJsonObject().get("x").getAsInt());
        state.settings().get("value").getAsJsonObject().addProperty("x", 3);
        assertEquals(1, state.settings().get("value").getAsJsonObject().get("x").getAsInt());
        assertThrows(UnsupportedOperationException.class, () -> state.settings().put("new", new JsonPrimitive(1)));
        assertThrows(UnsupportedOperationException.class, () -> snapshot(true).modules().clear());
    }

    @Test
    void rejectsInvalidIdsAndOversizedSettingStrings() {
        assertThrows(ConfigFormatException.class, () -> codec.decode("{\"schemaVersion\":1,\"modules\":{\"../bad\":{}}}"));
        assertThrows(ConfigFormatException.class, () -> codec.decode("{\"schemaVersion\":1,\"modules\":{\"ok\":{\"settings\":{\"text\":\"" + "a".repeat(1025) + "\"}}}}"));
    }
}
