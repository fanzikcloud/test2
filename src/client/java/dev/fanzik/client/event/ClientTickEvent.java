package dev.fanzik.client.event;

import net.minecraft.client.MinecraftClient;

public record ClientTickEvent(MinecraftClient client) {
}
