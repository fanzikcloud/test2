package dev.fanzik.client.config;

import dev.fanzik.client.module.Category;
import dev.fanzik.client.render.Palette;

import java.util.Objects;

public record GuiState(Palette palette, Category category, double horizontalPosition, double verticalPosition) {
    public GuiState {
        Objects.requireNonNull(palette);
        Objects.requireNonNull(category);
        if (!Double.isFinite(horizontalPosition) || !Double.isFinite(verticalPosition)
                || horizontalPosition < 0 || horizontalPosition > 1 || verticalPosition < 0 || verticalPosition > 1) {
            throw new IllegalArgumentException("Window positions must be in [0, 1]");
        }
    }

    public static GuiState defaults() {
        return new GuiState(Palette.IRIS, Category.MOVEMENT, 0.5, 0.5);
    }
}
