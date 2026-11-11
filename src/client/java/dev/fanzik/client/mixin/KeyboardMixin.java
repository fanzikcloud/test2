package dev.fanzik.client.mixin;

import dev.fanzik.client.FanzikClient;
import dev.fanzik.client.event.KeyInputEvent;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void fanzik$menuKey(long window, int key, int scanCode, int action, int modifiers, CallbackInfo info) {
        FanzikClient client = FanzikClient.instanceOrNull();
        if (client != null && window == MinecraftClient.getInstance().getWindow().getHandle()
                && client.handleMenuKey(key, scanCode, action, modifiers)) {
            info.cancel();
        }
    }

    @Inject(method = "onKey", at = @At("TAIL"))
    private void fanzik$onKey(long window, int key, int scanCode, int action, int modifiers, CallbackInfo info) {
        FanzikClient client = FanzikClient.instanceOrNull();
        if (client != null && window == MinecraftClient.getInstance().getWindow().getHandle()) {
            client.events().post(new KeyInputEvent(key, scanCode, action, modifiers));
        }
    }
}
