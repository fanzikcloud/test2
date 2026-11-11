package dev.fanzik.client.event;

public record KeyInputEvent(int key, int scanCode, int action, int modifiers) {
}
