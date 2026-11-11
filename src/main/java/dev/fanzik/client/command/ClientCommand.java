package dev.fanzik.client.command;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public record ClientCommand(String name, String usage, String descriptionKey, List<String> aliases,
                            Consumer<List<String>> action, Function<List<String>, List<String>> completion) {
    public ClientCommand {
        Objects.requireNonNull(name);
        Objects.requireNonNull(usage);
        Objects.requireNonNull(descriptionKey);
        Objects.requireNonNull(action);
        Objects.requireNonNull(completion);
        if (!name.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid command name: " + name);
        }
        aliases = aliases.stream().map(alias -> alias.toLowerCase(Locale.ROOT)).toList();
        if (aliases.stream().anyMatch(alias -> !alias.matches("[a-z][a-z0-9-]*"))) {
            throw new IllegalArgumentException("Invalid command alias");
        }
    }
}
