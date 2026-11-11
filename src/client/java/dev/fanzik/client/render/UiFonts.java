package dev.fanzik.client.render;

import dev.fanzik.client.mixin.TextRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;

public final class UiFonts {
    private UiFonts() {
        throw new AssertionError();
    }

    public static TextRenderer renderer(MinecraftClient mc) {
        return new TextRenderer(id -> ((TextRendererAccessor) mc.textRenderer).fanzik$getFontStorage(Render2D.UI_FONT), false);
    }
}
