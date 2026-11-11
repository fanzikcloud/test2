package dev.fanzik.client.render;

import dev.fanzik.client.setting.Labelled;

public enum Palette implements Labelled {
    IRIS(0xFF9D8AFF),
    SKY(0xFF69BFFF),
    MINT(0xFF6FE3BA);

    private final int accent;

    Palette(int accent) {
        this.accent = accent;
    }

    public int accent() {
        return accent;
    }

    @Override
    public String translationKey() {
        return "palette.fanzik." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
