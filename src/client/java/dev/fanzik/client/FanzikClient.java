package dev.fanzik.client;

import dev.fanzik.client.command.ChatFeedback;
import dev.fanzik.client.command.CommandDispatcher;
import dev.fanzik.client.command.Commands;
import dev.fanzik.client.config.ClientPreferences;
import dev.fanzik.client.config.ConfigCodec;
import dev.fanzik.client.config.ConfigRepository;
import dev.fanzik.client.config.ConfigService;
import dev.fanzik.client.event.ClientTickEvent;
import dev.fanzik.client.event.EventBus;
import dev.fanzik.client.event.HudRenderEvent;
import dev.fanzik.client.event.KeyInputEvent;
import dev.fanzik.client.event.SessionEvent;
import dev.fanzik.client.event.WorldRenderEvent;
import dev.fanzik.client.module.Module;
import dev.fanzik.client.module.ModuleExecutionException;
import dev.fanzik.client.module.ModuleManager;
import dev.fanzik.client.module.impl.AutoSprint;
import dev.fanzik.client.render.Render2D;
import dev.fanzik.client.render.Render3D;
import dev.fanzik.client.ui.ClickGuiScreen;
import dev.fanzik.client.ui.NotificationManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class FanzikClient implements ClientModInitializer {
    public static final String MOD_ID = "fanzik";
    public static final String VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElseThrow();
    public static final Logger LOGGER = LoggerFactory.getLogger("Fanzik");
    private static FanzikClient instance;
    private MinecraftClient mc;
    private EventBus events;
    private ModuleManager modules;
    private ClientPreferences preferences;
    private ConfigService configs;
    private CommandDispatcher commands;
    private NotificationManager notifications;
    private ChatFeedback feedback;
    private KeyBinding menuKey;
    private boolean welcomed;

    @Override
    public void onInitializeClient() {
        mc = MinecraftClient.getInstance();
        notifications = new NotificationManager();
        feedback = new ChatFeedback(mc, notifications, LOGGER);
        preferences = new ClientPreferences();
        events = new EventBus(this::eventFailure);
        modules = new ModuleManager(preferences);
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.fanzik.menu", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT, "category.fanzik"));
        modules.register(new AutoSprint(mc, events));
        Path configRoot = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        try {
            ConfigRepository repository = new ConfigRepository(configRoot, new ConfigCodec(VERSION));
            configs = new ConfigService(mc, modules, repository, LOGGER, failure -> {
                LOGGER.error("Fanzik persistence error", failure);
                notifications.push(Text.translatable("message.fanzik.error"), Text.translatable("message.fanzik.autosave_failed"), NotificationManager.Kind.ERROR);
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot initialize Fanzik configs at " + configRoot.toAbsolutePath(), exception);
        }
        configs.restore();
        commands = new CommandDispatcher(failure -> {
            switch (failure.type()) {
                case UNKNOWN -> feedback.error("message.fanzik.unknown_command", failure.message());
                case INPUT -> feedback.error("message.fanzik.command_error", failure.message());
                case EXECUTION -> feedback.failure(failure.cause());
            }
        });
        Commands.register(this);
        events.subscribe(KeyInputEvent.class, this::onKey);
        registerCallbacks();
        instance = this;
        LOGGER.info("Fanzik Client {} ready: {} module(s), config directory {}", VERSION, modules.all().size(), configs.directory());
    }

    private void registerCallbacks() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            events.post(new ClientTickEvent(client));
            configs.tick();
        });
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (!message.startsWith(".")) {
                return true;
            }
            mc.execute(() -> commands.dispatch(message));
            return false;
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            events.post(new SessionEvent(true));
            if (!welcomed) {
                welcomed = true;
                notifications.push(Text.literal("Fanzik Client"), Text.translatable("message.fanzik.welcome", menuKeyLabel()), NotificationManager.Kind.INFO);
            }
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            events.post(new SessionEvent(false));
            configs.flush().exceptionally(failure -> {
                LOGGER.error("Cannot save Fanzik state on disconnect", failure);
                return null;
            });
        }));
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (mc.world == null || mc.options.hudHidden) {
                return;
            }
            try (Render2D renderer = new Render2D(context)) {
                events.post(new HudRenderEvent(renderer, tickCounter.getTickDelta(false),
                        mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight()));
                if (!(mc.currentScreen instanceof ClickGuiScreen)) {
                    notifications.render(renderer, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), preferences.snapshot().palette().accent());
                }
            }
        });
        WorldRenderEvents.LAST.register(context -> {
            if (mc.world != null) {
                try (Render3D renderer = new Render3D(context)) {
                    events.post(new WorldRenderEvent(renderer, context.tickCounter().getTickDelta(false)));
                }
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            try {
                configs.close();
            } finally {
                try {
                    modules.close();
                } catch (RuntimeException failure) {
                    LOGGER.error("Cannot clean up Fanzik modules", failure);
                }
                events.clear();
            }
        });
    }

    public boolean handleMenuKey(int key, int scanCode, int action, int modifiers) {
        if (!isMenuKey(key, scanCode) || mc.world == null || mc.player == null) {
            return false;
        }
        if (mc.currentScreen instanceof ClickGuiScreen screen) {
            if (action == GLFW.GLFW_PRESS) {
                screen.keyPressed(key, scanCode, modifiers);
            }
            return action != GLFW.GLFW_RELEASE;
        }
        if (mc.currentScreen == null && action == GLFW.GLFW_PRESS) {
            mc.setScreen(new ClickGuiScreen(this));
            return true;
        }
        return false;
    }

    private void onKey(KeyInputEvent event) {
        if (event.action() != GLFW.GLFW_PRESS || mc.currentScreen != null || mc.player == null || mc.world == null
                || isMenuKey(event.key(), event.scanCode())) {
            return;
        }
        for (Module module : modules.all()) {
            if (module.key() >= 0 && module.key() == event.key()) {
                setModuleEnabled(module, !module.enabled());
            }
        }
    }

    private void eventFailure(RuntimeException failure) {
        LOGGER.error("Fanzik event handler failed", failure);
        if (failure instanceof ModuleExecutionException moduleFailure) {
            notifications.push(Text.translatable("message.fanzik.module_error"), Text.literal(moduleFailure.moduleId()), NotificationManager.Kind.ERROR);
        } else {
            notifications.push(Text.translatable("message.fanzik.error"), Text.translatable("message.fanzik.check_log"), NotificationManager.Kind.ERROR);
        }
    }

    public void setModuleEnabled(Module module, boolean enabled) {
        if (module.enabled() == enabled) {
            return;
        }
        try {
            module.setEnabled(enabled);
            notifications.push(Text.translatable(module.nameKey()), Text.translatable(enabled ? "ui.fanzik.enabled" : "ui.fanzik.disabled"),
                    enabled ? NotificationManager.Kind.SUCCESS : NotificationManager.Kind.INFO);
        } catch (RuntimeException failure) {
            feedback.failure(failure);
        }
    }

    public boolean isMenuKey(int key, int scanCode) {
        return menuKey != null && menuKey.matchesKey(key, scanCode);
    }

    public String menuKeyLabel() {
        return menuKey.getBoundKeyLocalizedText().getString();
    }

    public static FanzikClient instanceOrNull() {
        return instance;
    }

    public static FanzikClient instance() {
        if (instance == null) {
            throw new IllegalStateException("Fanzik Client has not initialized");
        }
        return instance;
    }

    public EventBus events() {
        return events;
    }

    public ModuleManager modules() {
        return modules;
    }

    public ClientPreferences preferences() {
        return preferences;
    }

    public ConfigService configs() {
        return configs;
    }

    public CommandDispatcher commands() {
        return commands;
    }

    public NotificationManager notifications() {
        return notifications;
    }

    public ChatFeedback feedback() {
        return feedback;
    }
}
