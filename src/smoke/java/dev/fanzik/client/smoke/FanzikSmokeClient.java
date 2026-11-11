package dev.fanzik.client.smoke;

import com.google.gson.JsonPrimitive;
import dev.fanzik.client.FanzikClient;
import dev.fanzik.client.config.ConfigSnapshot;
import dev.fanzik.client.config.ModuleState;
import dev.fanzik.client.event.HudRenderEvent;
import dev.fanzik.client.event.KeyInputEvent;
import dev.fanzik.client.event.Subscription;
import dev.fanzik.client.event.WorldRenderEvent;
import dev.fanzik.client.module.Category;
import dev.fanzik.client.module.Module;
import dev.fanzik.client.render.Render3D;
import dev.fanzik.client.setting.BooleanSetting;
import dev.fanzik.client.ui.ClickGuiScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class FanzikSmokeClient implements ClientModInitializer {
    private final List<String> checks = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private Stage stage = Stage.START;
    private int ticks;
    private int worldFrames;
    private int hudFrames;
    private long deadline;
    private FanzikClient fanzik;
    private Module sprint;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("fanzik.smokeTest")) {
            return;
        }
        deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(4);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient mc) {
        if (stage == Stage.DONE) {
            return;
        }
        if (System.nanoTime() > deadline) {
            throw new AssertionError("Smoke test timed out in stage " + stage);
        }
        ticks++;
        switch (stage) {
            case START -> {
                if (mc.getOverlay() != null || ticks < 10) {
                    return;
                }
                fanzik = FanzikClient.instance();
                sprint = fanzik.modules().find("auto-sprint");
                check(sprint != null && fanzik.modules().all().size() == 1, "Exactly one registered module");
                check(Category.values().length == 5, "Five categories");
                sprint.setEnabled(false);
                int before = fanzik.events().listenerCount();
                for (int i = 0; i < 20; i++) {
                    sprint.setEnabled(true);
                    sprint.setEnabled(false);
                }
                check(before == fanzik.events().listenerCount(), "No subscription leaks across 20 enable/disable cycles");
                subscriptions.add(fanzik.events().subscribe(WorldRenderEvent.class, this::drawWorld));
                subscriptions.add(fanzik.events().subscribe(HudRenderEvent.class, event -> {
                    hudFrames++;
                    event.renderer().roundedGradient(12, 12, 160, 24, 7, 0xA0202438, 0xA0111624);
                    event.renderer().text("Fanzik smoke test", 20, 19, 0xFFE8DFFF);
                }));
                mc.options.pauseOnLostFocus = false;
                mc.options.getViewDistance().setValue(2);
                mc.options.getSimulationDistance().setValue(2);
                mc.options.getGuiScale().setValue(1);
                mc.options.getMaxFps().setValue(60);
                mc.getWindow().setWindowedSize(1200, 680);
                mc.onResolutionChanged();
                GameRules rules = new GameRules();
                rules.get(GameRules.DO_MOB_SPAWNING).set(false, null);
                LevelInfo info = new LevelInfo("Fanzik smoke", GameMode.SURVIVAL, false, Difficulty.PEACEFUL,
                        true, rules, DataConfiguration.SAFE_MODE);
                advance(Stage.WORLD);
                mc.createIntegratedServerLoader().createAndStart("fanzik-smoke", info, new GeneratorOptions(42L, false, false),
                        registries -> registries.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder(), new TitleScreen());
            }
            case WORLD -> {
                if (mc.world == null || mc.player == null || mc.currentScreen != null || ticks < 30) {
                    return;
                }
                mc.player.getHungerManager().setFoodLevel(20);
                mc.player.setYaw(0);
                mc.player.setPitch(0);
                sprint.setEnabled(true);
                mc.options.forwardKey.setPressed(true);
                advance(Stage.SPRINT);
            }
            case SPRINT -> {
                if (ticks < 5) {
                    return;
                }
                check(mc.player.isSprinting(), "Auto Sprint starts while moving forward");
                mc.options.sneakKey.setPressed(true);
                advance(Stage.SNEAK);
            }
            case SNEAK -> {
                if (ticks < 4) {
                    return;
                }
                check(!mc.player.isSprinting(), "Auto Sprint respects sneaking");
                mc.options.sneakKey.setPressed(false);
                advance(Stage.RESUME);
            }
            case RESUME -> {
                if (ticks < 4) {
                    return;
                }
                check(mc.player.isSprinting(), "Auto Sprint resumes after sneaking");
                mc.player.getHungerManager().setFoodLevel(2);
                advance(Stage.HUNGER);
            }
            case HUNGER -> {
                if (ticks < 4) {
                    return;
                }
                check(!mc.player.isSprinting(), "Auto Sprint respects hunger");
                mc.player.getHungerManager().setFoodLevel(20);
                mc.options.forwardKey.setPressed(false);
                check(fanzik.handleMenuKey(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, GLFW.GLFW_PRESS, 0), "Right Shift is consumed locally");
                check(mc.currentScreen instanceof ClickGuiScreen, "Right Shift opens the menu in a world");
                advance(Stage.MENU);
            }
            case MENU -> {
                if (ticks < 25) {
                    return;
                }
                check(!mc.player.isSprinting(), "Menu pauses module-owned sprint");
                check(((ClickGuiScreen) mc.currentScreen).selectedModule() == sprint, "Movement selects Auto Sprint");
                screenshot(mc, "01-movement.png");
                validateAtomicConfigApply();
                check(!ClientSendMessageEvents.ALLOW_CHAT.invoker().allowSendChatMessage(".cfg save smoke"), "Dot config command never reaches the server");
                check(!ClientSendMessageEvents.ALLOW_CHAT.invoker().allowSendChatMessage(".unknown-smoke-command"), "Unknown dot command never reaches the server");
                check(ClientSendMessageEvents.ALLOW_CHAT.invoker().allowSendChatMessage("ordinary chat"), "Ordinary chat remains allowed");
                advance(Stage.SAVED);
            }
            case SAVED -> {
                if (fanzik.configs().cachedProfiles().stream().noneMatch(entry -> entry.name().equals("smoke"))) {
                    return;
                }
                check(true, "Config saved asynchronously through .cfg save");
                sprint.setEnabled(false);
                check(!ClientSendMessageEvents.ALLOW_CHAT.invoker().allowSendChatMessage(".cfg load smoke"), "Load command stays local");
                advance(Stage.LOADED);
            }
            case LOADED -> {
                if (!sprint.enabled()) {
                    return;
                }
                check(true, "Config restores module state on the client thread");
                fanzik.commands().dispatch(".cfg list");
                fanzik.commands().dispatch(".bind auto-sprint r");
                check(sprint.key() == GLFW.GLFW_KEY_R, "Bind command assigns the requested key");
                ((ClickGuiScreen) mc.currentScreen).close();
                fanzik.events().post(new KeyInputEvent(GLFW.GLFW_KEY_R, 0, GLFW.GLFW_PRESS, 0));
                check(!sprint.enabled(), "A bound key toggles the module");
                sprint.setEnabled(true);
                ChatScreen chat = new ChatScreen(".cf");
                mc.setScreen(chat);
                check(chat.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0), "Chat completion consumes Tab");
                TextFieldWidget field = chat.children().stream().filter(TextFieldWidget.class::isInstance)
                        .map(TextFieldWidget.class::cast).findFirst().orElseThrow();
                check(field.getText().equals(".cfg"), "Chat mixin completes dot commands");
                mc.setScreen(null);
                fanzik.handleMenuKey(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, GLFW.GLFW_PRESS, 0);
                fanzik.preferences().category(Category.COMBAT);
                advance(Stage.EMPTY);
            }
            case EMPTY -> {
                if (ticks < 12) {
                    return;
                }
                screenshot(mc, "02-combat.png");
                check(fanzik.modules().in(Category.COMBAT).isEmpty(), "Empty categories do not contain fake modules");
                fanzik.preferences().category(Category.MOVEMENT);
                click(mc, 645, 37);
                advance(Stage.CONFIG_PANEL);
            }
            case CONFIG_PANEL -> {
                if (ticks < 10) {
                    return;
                }
                screenshot(mc, "03-configs.png");
                check(worldFrames > 5 && hudFrames > 5, "2D and 3D events render in the real client");
                mc.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                mc.options.getGuiScale().setValue(3);
                mc.onResolutionChanged();
                advance(Stage.SCALED);
            }
            case SCALED -> {
                if (ticks < 12) {
                    return;
                }
                check(mc.currentScreen instanceof ClickGuiScreen, "Menu survives GUI scale changes");
                screenshot(mc, "04-scaled.png");
                ((ClickGuiScreen) mc.currentScreen).close();
                check(mc.currentScreen == null, "Menu closes cleanly");
                sprint.setEnabled(false);
                subscriptions.forEach(Subscription::close);
                Path result = FabricLoader.getInstance().getGameDir().resolve("smoke-result.txt");
                try {
                    Files.writeString(result, "PASSED\n" + String.join("\n", checks) + "\n");
                } catch (IOException exception) {
                    throw new IllegalStateException("Cannot write smoke result", exception);
                }
                FanzikClient.LOGGER.info("FANZIK_SMOKE_PASSED: {} checks; world frames {}; HUD frames {}", checks.size(), worldFrames, hudFrames);
                advance(Stage.DONE);
                mc.scheduleStop();
            }
            case DONE -> throw new IllegalStateException("Unexpected completed smoke stage");
        }
    }

    private void validateAtomicConfigApply() {
        ConfigSnapshot before = fanzik.modules().snapshot();
        ModuleState invalid = new ModuleState(false, -1, Map.of("forward-threshold", new JsonPrimitive(9)));
        try {
            fanzik.modules().apply(new ConfigSnapshot(Map.of("auto-sprint", invalid), before.ui()));
            throw new AssertionError("Invalid settings must be rejected");
        } catch (IllegalArgumentException expected) {
            check(before.equals(fanzik.modules().snapshot()), "Invalid config does not partially change state");
        }
        var result = fanzik.modules().apply(new ConfigSnapshot(Map.of("auto-sprint", before.modules().get("auto-sprint"),
                "unknown-module", new ModuleState(true, -1, Map.of())), before.ui()));
        check(result.unknownModules() == 1, "Unknown modules are counted and safely skipped");
        check(((BooleanSetting) sprint.setting("pause-in-screens")).get(), "Config preserves typed settings");
    }

    private void drawWorld(WorldRenderEvent event) {
        worldFrames++;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }
        Vec3d p = mc.player.getPos().add(1.5, 0.25, 4);
        event.renderer().box(new Box(p, p.add(0.8, 1, 0.8)), 0.018, 0x239D8AFF, 0xFF9D8AFF, Render3D.DepthMode.TESTED);
        event.renderer().circle(p.add(0.4, 0, 0.4), 1, 40, 0.015, 0xAA6FE3BA, Render3D.DepthMode.THROUGH_WALLS);
        event.renderer().billboard(p.add(0.4, 1.4, 0.4), 0.5f, 0.15f, 0x9969BFFF, Render3D.DepthMode.TESTED);
    }

    private void click(MinecraftClient mc, float x, float y) {
        float scale = Math.min(1, Math.min((mc.getWindow().getScaledWidth() - 24f) / ClickGuiScreen.WIDTH,
                (mc.getWindow().getScaledHeight() - 24f) / ClickGuiScreen.HEIGHT));
        double ox = (mc.getWindow().getScaledWidth() - ClickGuiScreen.WIDTH * scale) * fanzik.preferences().snapshot().horizontalPosition();
        double oy = (mc.getWindow().getScaledHeight() - ClickGuiScreen.HEIGHT * scale) * fanzik.preferences().snapshot().verticalPosition();
        mc.currentScreen.mouseClicked(ox + x * scale, oy + y * scale, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private void screenshot(MinecraftClient mc, String filename) {
        Path directory = FabricLoader.getInstance().getGameDir().resolve("screenshots");
        try {
            Files.createDirectories(directory);
            try (NativeImage image = ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())) {
                image.writeTo(directory.resolve(filename));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save smoke screenshot", exception);
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Smoke test failed: " + message);
        }
        checks.add(message);
        FanzikClient.LOGGER.info("SMOKE OK: {}", message);
    }

    private void advance(Stage next) {
        stage = next;
        ticks = 0;
        FanzikClient.LOGGER.info("Smoke stage: {}", next);
    }

    private enum Stage {
        START, WORLD, SPRINT, SNEAK, RESUME, HUNGER, MENU, SAVED, LOADED, EMPTY, CONFIG_PANEL, SCALED, DONE
    }
}
