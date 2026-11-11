package dev.fanzik.client.command;

import dev.fanzik.client.ui.NotificationManager;
import dev.fanzik.client.util.Failures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public final class ChatFeedback {
    private final MinecraftClient mc;
    private final NotificationManager notifications;
    private final Logger logger;

    public ChatFeedback(MinecraftClient mc, NotificationManager notifications, Logger logger) {
        this.mc = mc;
        this.notifications = notifications;
        this.logger = logger;
    }

    public void send(Text text) {
        mc.execute(() -> mc.inGameHud.getChatHud().addMessage(Text.literal("Fanzik ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal("› ").formatted(Formatting.DARK_GRAY)).append(text)));
    }

    public void info(String key, Object... args) {
        send(Text.translatable(key, args).formatted(Formatting.GRAY));
    }

    public void error(String key, Object... args) {
        send(Text.translatable(key, args).formatted(Formatting.RED));
    }

    public void success(String key, Object... args) {
        Text message = Text.translatable(key, args);
        send(message.copy().formatted(Formatting.GREEN));
        notifications.push(Text.translatable("ui.fanzik.configs"), message, NotificationManager.Kind.SUCCESS);
    }

    public void failure(Throwable failure) {
        logger.error("Fanzik operation failed", Failures.unwrap(failure));
        error("message.fanzik.operation_failed", Failures.describe(failure));
        notifications.push(Text.translatable("message.fanzik.error"), Text.translatable("message.fanzik.check_chat"), NotificationManager.Kind.ERROR);
    }

    public <T> void handle(CompletionStage<T> operation, Consumer<T> onSuccess) {
        operation.whenComplete((value, failure) -> mc.execute(() -> {
            if (failure != null) {
                failure(failure);
                return;
            }
            try {
                onSuccess.accept(value);
            } catch (RuntimeException exception) {
                failure(exception);
            }
        }));
    }
}
