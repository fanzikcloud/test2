package dev.fanzik.client.event;

import dev.fanzik.client.render.Render2D;

public record HudRenderEvent(Render2D renderer, float tickDelta, int width, int height) {
}
