package dev.fanzik.client.command;

import dev.fanzik.client.FanzikClient;
import dev.fanzik.client.config.ConfigEntry;
import dev.fanzik.client.config.ConfigName;
import dev.fanzik.client.input.KeyNames;
import dev.fanzik.client.module.Module;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

public final class Commands {
    private Commands() {
        throw new AssertionError();
    }

    public static void register(FanzikClient client) {
        CommandDispatcher dispatcher = client.commands();
        ChatFeedback feedback = client.feedback();
        dispatcher.register(new ClientCommand("help", ".help", "command.fanzik.help", List.of("h"), args -> {
            requireCount(args, 0, 0, ".help");
            feedback.info("message.fanzik.commands");
            dispatcher.commands().forEach(command -> feedback.send(Text.literal(command.usage()).formatted(Formatting.LIGHT_PURPLE)
                    .append(Text.literal(" — ").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable(command.descriptionKey()).formatted(Formatting.GRAY))));
        }, args -> List.of()));

        dispatcher.register(new ClientCommand("cfg", ".cfg <save|load|list|dir> [name]", "command.fanzik.cfg", List.of("config"), args -> {
            requireCount(args, 1, 2, ".cfg <save|load|list|dir> [name]");
            String action = args.getFirst().toLowerCase(Locale.ROOT);
            switch (action) {
                case "save" -> {
                    requireCount(args, 2, 2, ".cfg save <name>");
                    String name = ConfigName.normalize(args.get(1));
                    feedback.handle(client.configs().save(name), ignored -> feedback.success("message.fanzik.config_saved", name));
                }
                case "load" -> {
                    requireCount(args, 2, 2, ".cfg load <name>");
                    String name = ConfigName.normalize(args.get(1));
                    feedback.handle(client.configs().load(name), result -> {
                        feedback.success("message.fanzik.config_loaded", name);
                        if (result.unknownModules() > 0 || result.unknownSettings() > 0) {
                            feedback.info("message.fanzik.config_unknown", result.unknownModules(), result.unknownSettings());
                        }
                    });
                }
                case "list" -> {
                    requireCount(args, 1, 1, ".cfg list");
                    feedback.handle(client.configs().refresh(), profiles -> {
                        if (profiles.isEmpty()) {
                            feedback.info("message.fanzik.config_empty");
                        } else {
                            feedback.info("message.fanzik.config_list", profiles.size());
                            for (ConfigEntry entry : profiles) {
                                feedback.send(Text.literal("  " + entry.name()).styled(style -> style.withColor(Formatting.LIGHT_PURPLE)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, ".cfg load " + entry.name()))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("message.fanzik.click_load")))));
                            }
                        }
                    });
                }
                case "dir" -> {
                    requireCount(args, 1, 1, ".cfg dir");
                    feedback.info("message.fanzik.config_path", client.configs().directory().toString());
                    feedback.handle(client.configs().openDirectory(), ignored -> feedback.info("message.fanzik.directory_opened"));
                }
                default -> throw new IllegalArgumentException(I18n.translate("message.fanzik.usage", ".cfg <save|load|list|dir> [name]"));
            }
        }, args -> args.size() == 1 ? List.of("save", "load", "list", "dir")
                : args.size() == 2 && (args.getFirst().equalsIgnoreCase("load") || args.getFirst().equalsIgnoreCase("save"))
                ? client.configs().cachedProfiles().stream().map(ConfigEntry::name).toList() : List.of()));

        dispatcher.register(new ClientCommand("toggle", ".toggle <module> [on|off]", "command.fanzik.toggle", List.of("t"), args -> {
            requireCount(args, 1, 2, ".toggle <module> [on|off]");
            Module module = requireModule(client, args.getFirst());
            boolean enabled = args.size() == 1 ? !module.enabled() : switch (args.get(1).toLowerCase(Locale.ROOT)) {
                case "on" -> true;
                case "off" -> false;
                default -> throw new IllegalArgumentException(I18n.translate("message.fanzik.usage", ".toggle <module> [on|off]"));
            };
            client.setModuleEnabled(module, enabled);
            feedback.info(enabled ? "message.fanzik.module_enabled" : "message.fanzik.module_disabled", Text.translatable(module.nameKey()));
        }, args -> args.size() == 1 ? moduleIds(client) : args.size() == 2 ? List.of("on", "off") : List.of()));

        dispatcher.register(new ClientCommand("bind", ".bind <module> <key|none>", "command.fanzik.bind", List.of("b"), args -> {
            requireCount(args, 2, 2, ".bind <module> <key|none>");
            Module module = requireModule(client, args.getFirst());
            int key = KeyNames.parse(args.get(1));
            if (key >= 0 && client.isMenuKey(key, 0)) {
                throw new IllegalArgumentException(I18n.translate("message.fanzik.reserved_key"));
            }
            module.key(key);
            feedback.info("message.fanzik.key_bound", Text.translatable(module.nameKey()), KeyNames.label(key));
        }, args -> args.size() == 1 ? moduleIds(client) : args.size() == 2
                ? List.of("none", "r", "g", "v", "f4", "f6", "f7", "f8", "left.alt", "right.control") : List.of()));

        dispatcher.register(new ClientCommand("modules", ".modules", "command.fanzik.modules", List.of("mods"), args -> {
            requireCount(args, 0, 0, ".modules");
            client.modules().all().forEach(module -> feedback.send(Text.translatable(module.nameKey())
                    .formatted(module.enabled() ? Formatting.GREEN : Formatting.GRAY)
                    .append(Text.literal(" · " + module.category().label() + " · " + KeyNames.label(module.key())).formatted(Formatting.DARK_GRAY))));
        }, args -> List.of()));
    }

    private static List<String> moduleIds(FanzikClient client) {
        return client.modules().all().stream().map(Module::id).toList();
    }

    private static Module requireModule(FanzikClient client, String name) {
        Module module = client.modules().find(name);
        if (module == null) {
            throw new IllegalArgumentException(I18n.translate("message.fanzik.unknown_module", name));
        }
        return module;
    }

    private static void requireCount(List<String> args, int minimum, int maximum, String usage) {
        if (args.size() < minimum || args.size() > maximum) {
            throw new IllegalArgumentException(I18n.translate("message.fanzik.usage", usage));
        }
    }
}
