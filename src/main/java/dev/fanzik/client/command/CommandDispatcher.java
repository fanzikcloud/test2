package dev.fanzik.client.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class CommandDispatcher {
    private final Map<String, ClientCommand> commands = new LinkedHashMap<>();
    private final Map<String, ClientCommand> lookup = new LinkedHashMap<>();
    private final Consumer<Failure> errorHandler;

    public CommandDispatcher(Consumer<Failure> errorHandler) {
        this.errorHandler = Objects.requireNonNull(errorHandler);
    }

    public void register(ClientCommand command) {
        Objects.requireNonNull(command);
        List<String> names = new ArrayList<>(command.aliases());
        names.addFirst(command.name());
        if (names.stream().distinct().count() != names.size() || names.stream().anyMatch(lookup::containsKey)) {
            throw new IllegalArgumentException("Duplicate command or alias: " + command.name());
        }
        commands.put(command.name(), command);
        names.forEach(name -> lookup.put(name, command));
    }

    public List<ClientCommand> commands() {
        return List.copyOf(commands.values());
    }

    public boolean dispatch(String message) {
        if (message == null || !message.startsWith(".")) {
            return false;
        }
        try {
            List<String> tokens = CommandTokenizer.tokenize(message.substring(1));
            String name = tokens.isEmpty() ? "help" : tokens.getFirst().toLowerCase(Locale.ROOT);
            ClientCommand command = lookup.get(name);
            if (command == null) {
                errorHandler.accept(new Failure(FailureType.UNKNOWN, name, null));
            } else {
                command.action().accept(tokens.isEmpty() ? List.of() : tokens.subList(1, tokens.size()));
            }
        } catch (IllegalArgumentException exception) {
            errorHandler.accept(new Failure(FailureType.INPUT, exception.getMessage(), exception));
        } catch (RuntimeException exception) {
            errorHandler.accept(new Failure(FailureType.EXECUTION, exception.getMessage(), exception));
        }
        return true;
    }

    public List<String> suggest(String input) {
        if (input == null || !input.startsWith(".") || input.length() > 512) {
            return List.of();
        }
        String line = input.substring(1);
        if (!line.contains(" ")) {
            String prefix = line.toLowerCase(Locale.ROOT);
            return commands.keySet().stream().filter(name -> name.startsWith(prefix)).sorted()
                    .map(name -> "." + name).limit(12).toList();
        }
        int firstSpace = line.indexOf(' ');
        ClientCommand command = lookup.get(line.substring(0, firstSpace).toLowerCase(Locale.ROOT));
        if (command == null) {
            return List.of();
        }
        List<String> args = Arrays.asList(line.substring(firstSpace + 1).split(" +", -1));
        int lastSpace = input.lastIndexOf(' ');
        String prefix = args.getLast().toLowerCase(Locale.ROOT);
        return command.completion().apply(Collections.unmodifiableList(args)).stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(prefix))
                .distinct().sorted().limit(12).map(candidate -> input.substring(0, lastSpace + 1) + candidate).toList();
    }

    public enum FailureType {
        UNKNOWN, INPUT, EXECUTION
    }

    public record Failure(FailureType type, String message, RuntimeException cause) {
    }
}
