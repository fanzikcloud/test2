package dev.fanzik.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class NumberSetting extends Setting<Double> {
    private final double minimum;
    private final double maximum;
    private final double step;

    public NumberSetting(String id, String nameKey, String descriptionKey, double defaultValue,
                         double minimum, double maximum, double step) {
        super(id, nameKey, descriptionKey, defaultValue);
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || !Double.isFinite(step)
                || maximum <= minimum || step <= 0 || step > maximum - minimum) {
            throw new IllegalArgumentException("Invalid numeric bounds for " + id);
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        if (Math.abs(normalize(defaultValue) - defaultValue) > 1.0e-8) {
            throw new IllegalArgumentException("Default value must align with the step for " + id);
        }
    }

    public double minimum() {
        return minimum;
    }

    public double maximum() {
        return maximum;
    }

    public double step() {
        return step;
    }

    public double fraction() {
        return (get() - minimum) / (maximum - minimum);
    }

    public void setFraction(double fraction) {
        if (!Double.isFinite(fraction)) {
            throw new IllegalArgumentException("Slider fraction must be finite");
        }
        set(minimum + Math.clamp(fraction, 0, 1) * (maximum - minimum));
    }

    public String formatted() {
        return BigDecimal.valueOf(get()).stripTrailingZeros().toPlainString();
    }

    @Override
    protected Double normalize(Double value) {
        if (!Double.isFinite(value) || value < minimum - 1.0e-8 || value > maximum + 1.0e-8) {
            throw new IllegalArgumentException("Setting " + id() + " must be in [" + minimum + ", " + maximum + "]");
        }
        double snapped = minimum + Math.round((value - minimum) / step) * step;
        return BigDecimal.valueOf(Math.clamp(snapped, minimum, maximum))
                .setScale(9, RoundingMode.HALF_UP).doubleValue();
    }

    @Override
    protected JsonElement encodeValue(Double value) {
        return new JsonPrimitive(value);
    }

    @Override
    public Double decode(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Setting " + id() + " must be a number");
        }
        return normalize(element.getAsDouble());
    }
}
