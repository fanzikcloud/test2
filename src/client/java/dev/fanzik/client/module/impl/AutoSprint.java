package dev.fanzik.client.module.impl;

import dev.fanzik.client.event.ClientTickEvent;
import dev.fanzik.client.event.EventBus;
import dev.fanzik.client.event.SessionEvent;
import dev.fanzik.client.module.Category;
import dev.fanzik.client.module.Module;
import dev.fanzik.client.setting.BooleanSetting;
import dev.fanzik.client.setting.EnumSetting;
import dev.fanzik.client.setting.Labelled;
import dev.fanzik.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;

public final class AutoSprint extends Module {
    private final BooleanSetting pauseInScreens = add(new BooleanSetting("pause-in-screens",
            "setting.fanzik.pause_in_screens", "setting.fanzik.pause_in_screens.description", true));
    private final BooleanSetting pauseUsingItem = add(new BooleanSetting("pause-using-item",
            "setting.fanzik.pause_using_item", "setting.fanzik.pause_using_item.description", true));
    private final EnumSetting<StartPolicy> startPolicy = add(new EnumSetting<>("start-policy",
            "setting.fanzik.start_policy", "setting.fanzik.start_policy.description", StartPolicy.NORMAL));
    private final NumberSetting forwardThreshold = add(new NumberSetting("forward-threshold",
            "setting.fanzik.forward_threshold", "setting.fanzik.forward_threshold.description", 0.8, 0.1, 1.0, 0.05));
    private ClientPlayerEntity managedPlayer;
    private boolean ownsSprint;

    public AutoSprint(MinecraftClient mc, EventBus events) {
        super(mc, events, "auto-sprint", "module.fanzik.auto_sprint", "module.fanzik.auto_sprint.description", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        listen(ClientTickEvent.class, event -> tick());
        listen(SessionEvent.class, event -> {
            if (!event.connected()) {
                releaseSprint();
                managedPlayer = null;
            }
        });
    }

    @Override
    protected void onDisable() {
        releaseSprint();
        managedPlayer = null;
    }

    private void tick() {
        ClientPlayerEntity player = mc.player;
        if (player != managedPlayer) {
            releaseSprint();
            managedPlayer = player;
            ownsSprint = false;
        }
        if (player == null || mc.world == null) {
            return;
        }
        boolean canSprint = (!pauseInScreens.get() || mc.currentScreen == null)
                && !player.isSneaking() && !player.input.sneaking && !mc.options.sneakKey.isPressed()
                && !player.hasVehicle() && !player.isFallFlying()
                && !player.isSpectator() && !player.getAbilities().flying
                && !player.isTouchingWater() && !player.isInLava()
                && !player.horizontalCollision && !player.hasStatusEffect(StatusEffects.BLINDNESS)
                && (!pauseUsingItem.get() || !player.isUsingItem())
                && (player.getHungerManager().getFoodLevel() > 6 || player.getAbilities().allowFlying)
                && player.input.movementForward >= forwardThreshold.get();
        if (!canSprint) {
            releaseSprint();
            return;
        }
        if (!player.isSprinting() && (startPolicy.get() == StartPolicy.NORMAL || player.isOnGround())) {
            player.setSprinting(true);
            ownsSprint = true;
        }
    }

    private void releaseSprint() {
        if (ownsSprint && managedPlayer != null && managedPlayer == mc.player && !mc.options.sprintKey.isPressed()) {
            managedPlayer.setSprinting(false);
        }
        ownsSprint = false;
    }

    public enum StartPolicy implements Labelled {
        NORMAL, ON_GROUND;

        @Override
        public String translationKey() {
            return "option.fanzik.start_policy." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
