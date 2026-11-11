package dev.fanzik.client.ui;

import dev.fanzik.client.render.Render2D;
import dev.fanzik.client.render.UiFonts;
import dev.fanzik.client.util.Argb;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;

public final class UiControls {
    private UiControls() {
        throw new AssertionError();
    }

    public static boolean hit(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    public static float smooth(float current, float target, float speed, double delta) {
        return (float) (current + (target - current) * (1 - Math.exp(-speed * Math.min(delta, 0.1))));
    }

    public static void button(Render2D r, String label, float x, float y, float width, float height,
                              double mouseX, double mouseY, int accent, boolean primary, boolean enabled) {
        boolean hover = enabled && hit(mouseX, mouseY, x, y, width, height);
        int background = primary ? Argb.alpha(accent, hover ? 0.28 : 0.18) : hover ? Theme.HOVER : Theme.PANEL;
        r.rounded(x, y, width, height, 7, Argb.alpha(background, enabled ? 1 : 0.5));
        r.stroke(x, y, width, height, 7, 0.7f, primary ? Argb.alpha(accent, 0.25) : Theme.BORDER);
        r.centered(label, x + width / 2, y + (height - 10) / 2, 0.95f,
                enabled ? primary ? accent : Theme.SECONDARY : Theme.MUTED);
    }

    public static void toggle(Render2D r, float x, float y, float progress, int accent) {
        r.rounded(x, y, 34, 18, 9, Argb.lerp(0xFF30384C, Argb.alpha(accent, 0.85), progress));
        r.circle(x + 9 + 16 * progress, y + 9, 5.8f, Argb.lerp(0xFF9DA6BD, 0xFFFFFFFF, progress));
    }

    public static TextFieldWidget field(MinecraftClient mc, int x, int y, int width, int height, String placeholderKey) {
        TextFieldWidget field = new TextFieldWidget(UiFonts.renderer(mc), x, y, width, height, Text.translatable(placeholderKey));
        field.setDrawsBackground(false);
        field.setEditableColor(Theme.TEXT);
        field.setUneditableColor(Theme.MUTED);
        field.setMaxLength(64);
        field.setRenderTextProvider((value, firstCharacter) -> Render2D.styled(value).asOrderedText());
        field.setPlaceholder(Render2D.styled(I18n.translate(placeholderKey)).copy().withColor(Theme.MUTED));
        return field;
    }
}
