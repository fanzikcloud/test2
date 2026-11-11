package dev.fanzik.client.mixin;

import dev.fanzik.client.FanzikClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow
    protected TextFieldWidget chatField;
    @Unique
    private List<String> fanzik$suggestions = List.of();
    @Unique
    private String fanzik$lastCompletion = "";
    @Unique
    private int fanzik$completionIndex;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void fanzik$complete(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> info) {
        FanzikClient client = FanzikClient.instanceOrNull();
        if (client == null || keyCode != GLFW.GLFW_KEY_TAB || !chatField.getText().startsWith(".")) {
            return;
        }
        String text = chatField.getText();
        if (!text.equals(fanzik$lastCompletion)) {
            fanzik$suggestions = client.commands().suggest(text);
            fanzik$completionIndex = Screen.hasShiftDown() ? fanzik$suggestions.size() - 1 : 0;
        } else if (!fanzik$suggestions.isEmpty()) {
            fanzik$completionIndex = Math.floorMod(fanzik$completionIndex + (Screen.hasShiftDown() ? -1 : 1), fanzik$suggestions.size());
        }
        if (!fanzik$suggestions.isEmpty()) {
            fanzik$lastCompletion = fanzik$suggestions.get(fanzik$completionIndex);
            chatField.setText(fanzik$lastCompletion);
            chatField.setCursorToEnd(false);
        }
        info.setReturnValue(true);
    }
}
