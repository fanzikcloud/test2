package dev.fanzik.client.setting;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SettingTest {
    @Test
    void booleanChangesOnlyNotifyWhenTheValueChanges() {
        BooleanSetting setting = new BooleanSetting("enabled", "name", "description", false);
        AtomicInteger calls = new AtomicInteger();
        var subscription = setting.onChange(calls::incrementAndGet);
        setting.set(false);
        setting.toggle();
        setting.set(true);
        assertEquals(1, calls.get());
        setting.reset();
        assertEquals(2, calls.get());
        subscription.close();
        setting.toggle();
        assertEquals(2, calls.get());
    }

    @Test
    void booleanSerializationIsStrictAndDefaultIsIndependent() {
        BooleanSetting setting = new BooleanSetting("enabled", "name", "description", false);
        setting.applyJson(new JsonPrimitive(true));
        assertEquals(new JsonPrimitive(true), setting.encode());
        assertEquals(new JsonPrimitive(false), setting.defaultJson());
        assertThrows(IllegalArgumentException.class, () -> setting.applyJson(new JsonPrimitive("true")));
        assertThrows(IllegalArgumentException.class, () -> setting.applyJson(new JsonPrimitive(1)));
        assertThrows(IllegalArgumentException.class, () -> setting.applyJson(JsonNull.INSTANCE));
        assertTrue(setting.get());
    }

    @Test
    void numericValuesSnapWithoutFloatingPointArtifacts() {
        NumberSetting setting = numeric();
        setting.set(0.824);
        assertEquals(0.8, setting.get(), 1e-10);
        setting.set(0.826);
        assertEquals(0.85, setting.get(), 1e-10);
        assertEquals("0.85", setting.formatted());
        setting.setFraction(1);
        assertEquals("1", setting.formatted());
        assertEquals(1, setting.fraction());
        setting.setFraction(-5);
        assertEquals(0.1, setting.get(), 1e-10);
    }

    @Test
    void numericValidationDoesNotMutateTheSetting() {
        NumberSetting setting = numeric();
        setting.validateJson(new JsonPrimitive(0.3));
        assertEquals(0.8, setting.get());
        assertThrows(IllegalArgumentException.class, () -> setting.validateJson(new JsonPrimitive(8)));
        assertThrows(IllegalArgumentException.class, () -> setting.set(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> setting.set(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> setting.setFraction(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> setting.applyJson(new JsonPrimitive("0.5")));
        assertThrows(IllegalArgumentException.class, () -> setting.applyJson(new JsonObject()));
        assertEquals(0.8, setting.get());
    }

    @Test
    void invalidNumericDefinitionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new NumberSetting("test", "n", "d", 0, 1, 0, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new NumberSetting("test", "n", "d", 0, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new NumberSetting("test", "n", "d", 0.33, 0, 1, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new NumberSetting("test", "n", "d", 2, 0, 1, 0.1));
    }

    @Test
    void enumCyclesBothDirectionsAndRoundTrips() {
        EnumSetting<Mode> setting = new EnumSetting<>("mode", "n", "d", Mode.FIRST);
        setting.cycle(-1);
        assertEquals(Mode.SECOND, setting.get());
        setting.cycle(1);
        assertEquals(Mode.FIRST, setting.get());
        setting.applyJson(new JsonPrimitive("second"));
        assertEquals("SECOND", setting.encode().getAsString());
        assertEquals("mode.second", setting.valueKey());
        assertThrows(IllegalArgumentException.class, () -> setting.applyJson(new JsonPrimitive("third")));
        assertThrows(IllegalArgumentException.class, () -> setting.applyJson(new JsonPrimitive(1)));
        assertThrows(UnsupportedOperationException.class, () -> setting.options().add(Mode.FIRST));
    }

    @Test
    void conditionalVisibilityTracksItsDependency() {
        AtomicBoolean visible = new AtomicBoolean(false);
        BooleanSetting setting = new BooleanSetting("test", "n", "d", false);
        setting.visibleWhen(visible::get);
        assertFalse(setting.visible());
        visible.set(true);
        assertTrue(setting.visible());
    }

    @Test
    void invalidIdsAndNullValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BooleanSetting("bad/id", "n", "d", false));
        assertThrows(NullPointerException.class, () -> new BooleanSetting("ok", "n", "d", false).set(null));
    }

    private NumberSetting numeric() {
        return new NumberSetting("threshold", "n", "d", 0.8, 0.1, 1, 0.05);
    }

    private enum Mode implements Labelled {
        FIRST, SECOND;

        @Override
        public String translationKey() {
            return "mode." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
