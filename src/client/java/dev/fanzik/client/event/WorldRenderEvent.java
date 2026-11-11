package dev.fanzik.client.event;

import dev.fanzik.client.render.Render3D;

public record WorldRenderEvent(Render3D renderer, float tickDelta) {
}
