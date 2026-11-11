package dev.fanzik.client.config;

import dev.fanzik.client.event.Subscription;
import dev.fanzik.client.module.Category;
import dev.fanzik.client.render.Palette;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ClientPreferences {
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private GuiState state = GuiState.defaults();

    public GuiState snapshot() {
        return state;
    }

    public void apply(GuiState state) {
        if (!this.state.equals(Objects.requireNonNull(state))) {
            this.state = state;
            listeners.forEach(Runnable::run);
        }
    }

    public void palette(Palette palette) {
        apply(new GuiState(palette, state.category(), state.horizontalPosition(), state.verticalPosition()));
    }

    public void category(Category category) {
        apply(new GuiState(state.palette(), category, state.horizontalPosition(), state.verticalPosition()));
    }

    public void position(double horizontal, double vertical) {
        apply(new GuiState(state.palette(), state.category(), Math.clamp(horizontal, 0, 1), Math.clamp(vertical, 0, 1)));
    }

    public Subscription onChange(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener));
        return () -> listeners.remove(listener);
    }
}
