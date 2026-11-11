package dev.fanzik.client.ui;

import dev.fanzik.client.FanzikClient;
import dev.fanzik.client.config.ConfigEntry;
import dev.fanzik.client.config.ConfigName;
import dev.fanzik.client.render.Icons;
import dev.fanzik.client.render.Render2D;
import dev.fanzik.client.util.Argb;
import dev.fanzik.client.util.Failures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import org.lwjgl.glfw.GLFW;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ConfigOverlay {
    private static final float X = 250;
    private static final float Y = 83;
    private static final float W = 406;
    private static final float H = 348;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm").withZone(ZoneId.systemDefault());
    private final FanzikClient client;
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Runnable close;
    private final TextFieldWidget name;
    private List<ConfigEntry> entries = List.of();
    private String status = "";
    private boolean busy;
    private boolean error;
    private double scroll;

    public ConfigOverlay(FanzikClient client, Runnable close) {
        this.client = client;
        this.close = close;
        name = UiControls.field(mc, (int) X + 28, (int) Y + 239, (int) W - 56, 18, "ui.fanzik.config_name");
        name.setMaxLength(48);
        refresh();
    }

    private void refresh() {
        busy = true;
        client.configs().refresh().whenComplete((profiles, failure) -> mc.execute(() -> {
            busy = false;
            if (failure == null) {
                entries = profiles;
            } else {
                setFailure(failure);
            }
        }));
    }

    public void render(Render2D r, double mouseX, double mouseY, float delta, int accent) {
        r.rounded(1, 1, ClickGuiScreen.WIDTH - 2, ClickGuiScreen.HEIGHT - 2, 17, 0xB0080B13);
        r.shadow(X, Y, W, H, 14, 20, 0xFF000000);
        r.roundedGradient(X, Y, W, H, 14, 0xFF191E2E, 0xFF111622);
        r.stroke(X, Y, W, H, 14, 0.8f, Argb.alpha(accent, 0.25));
        r.rounded(X + 20, Y + 20, 32, 32, 9, Argb.alpha(accent, 0.13));
        Icons.folder(r, X + 28, Y + 27, accent);
        r.text(I18n.translate("ui.fanzik.configs"), X + 64, Y + 20, 1.25f, Theme.TEXT);
        r.text(I18n.translate("ui.fanzik.configs.description"), X + 64, Y + 40, 0.85f, Theme.MUTED);
        Icons.close(r, X + W - 31, Y + 23, UiControls.hit(mouseX, mouseY, X + W - 40, Y + 14, 28, 28) ? Theme.TEXT : Theme.MUTED);
        r.rounded(X + 20, Y + 72, W - 40, 145, 8, 0xFF0E131F);
        try (Render2D.Scope ignored = r.scissor(X + 21, Y + 73, W - 42, 143)) {
            if (entries.isEmpty()) {
                r.centered(I18n.translate(busy ? "ui.fanzik.loading" : "ui.fanzik.no_configs"), X + W / 2, Y + 133, 1, Theme.MUTED);
            } else {
                for (int i = 0; i < entries.size(); i++) {
                    ConfigEntry entry = entries.get(i);
                    float rowY = Y + 76 + i * 34 - (float) scroll;
                    if (rowY + 34 < Y + 73 || rowY > Y + 216) {
                        continue;
                    }
                    boolean selected = entry.name().equalsIgnoreCase(name.getText());
                    boolean hover = UiControls.hit(mouseX, mouseY, X + 24, rowY, W - 48, 31);
                    r.rounded(X + 24, rowY, W - 48, 31, 5, selected ? Argb.alpha(accent, 0.12) : hover ? Theme.HOVER : 0x00000000);
                    r.text(r.elide(entry.name(), 174), X + 34, rowY + 10, selected ? accent : Theme.SECONDARY);
                    String date = DATE.format(entry.modifiedAt());
                    r.text(date, X + W - 32 - r.textWidth(date, 0.78f), rowY + 11, 0.78f, Theme.MUTED);
                }
            }
        }
        r.rounded(X + 20, Y + 230, W - 40, 31, 7, 0xFF0E131F);
        r.stroke(X + 20, Y + 230, W - 40, 31, 7, 0.7f, name.isFocused() ? Argb.alpha(accent, 0.6) : Theme.BORDER);
        name.render(r.context(), (int) mouseX, (int) mouseY, delta);
        UiControls.button(r, I18n.translate("ui.fanzik.save"), X + 20, Y + 274, 112, 31, mouseX, mouseY, accent, true, !busy && !name.getText().isBlank());
        UiControls.button(r, I18n.translate("ui.fanzik.load"), X + 142, Y + 274, 112, 31, mouseX, mouseY, accent, false, !busy && !name.getText().isBlank());
        UiControls.button(r, I18n.translate("ui.fanzik.folder"), X + 264, Y + 274, 122, 31, mouseX, mouseY, accent, false, !busy);
        String message = status.isEmpty() ? I18n.translate("ui.fanzik.config_hint") : status;
        r.text(r.elide(message, W - 40), X + 20, Y + 320, 0.8f, error ? Theme.ERROR : Theme.MUTED);
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        if (UiControls.hit(x, y, X + W - 40, Y + 14, 28, 28)) {
            close.run();
            return true;
        }
        name.setFocused(UiControls.hit(x, y, X + 20, Y + 230, W - 40, 31));
        if (name.isFocused()) {
            name.mouseClicked(x, y, button);
            return true;
        }
        if (UiControls.hit(x, y, X + 20, Y + 72, W - 40, 145)) {
            int index = (int) ((y - Y - 76 + scroll) / 34);
            if (index >= 0 && index < entries.size()) {
                name.setText(entries.get(index).name());
                name.setCursorToEnd(false);
            }
            return true;
        }
        if (!busy) {
            if (UiControls.hit(x, y, X + 20, Y + 274, 112, 31) && !name.getText().isBlank()) {
                save();
            } else if (UiControls.hit(x, y, X + 142, Y + 274, 112, 31) && !name.getText().isBlank()) {
                load();
            } else if (UiControls.hit(x, y, X + 264, Y + 274, 122, 31)) {
                perform(client.configs().openDirectory(), () -> status = I18n.translate("message.fanzik.directory_opened"));
            }
        }
        return true;
    }

    public boolean mouseScrolled(double x, double y, double amount) {
        if (UiControls.hit(x, y, X + 20, Y + 72, W - 40, 145)) {
            scroll = Math.clamp(scroll - amount * 28, 0, Math.max(0, entries.size() * 34 - 139));
        }
        return true;
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE || client.isMenuKey(key, scanCode)) {
            close.run();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER && !busy && !name.getText().isBlank()) {
            save();
            return true;
        }
        return name.keyPressed(key, scanCode, modifiers);
    }

    public boolean charTyped(char character, int modifiers) {
        return name.charTyped(character, modifiers);
    }

    private void save() {
        try {
            String normalized = ConfigName.normalize(name.getText());
            perform(client.configs().save(normalized), () -> {
                name.setText(normalized);
                entries = client.configs().cachedProfiles();
                status = I18n.translate("message.fanzik.config_saved", normalized);
                client.feedback().success("message.fanzik.config_saved", normalized);
            });
        } catch (IllegalArgumentException exception) {
            setFailure(exception);
        }
    }

    private void load() {
        try {
            String normalized = ConfigName.normalize(name.getText());
            perform(client.configs().load(normalized), () -> {
                status = I18n.translate("message.fanzik.config_loaded", normalized);
                client.feedback().success("message.fanzik.config_loaded", normalized);
            });
        } catch (IllegalArgumentException exception) {
            setFailure(exception);
        }
    }

    private void perform(CompletableFuture<?> operation, Runnable onSuccess) {
        busy = true;
        error = false;
        status = I18n.translate("ui.fanzik.loading");
        operation.whenComplete((ignored, failure) -> mc.execute(() -> {
            busy = false;
            if (failure != null) {
                setFailure(failure);
            } else {
                error = false;
                onSuccess.run();
            }
        }));
    }

    private void setFailure(Throwable failure) {
        error = true;
        status = Failures.describe(failure);
        client.feedback().failure(failure);
    }
}
