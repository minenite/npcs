package net.minenite.npcs.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minenite.npcs.NpcsPlugin;
import net.minenite.npcs.civilian.CivilianNpc;
import net.minenite.npcs.civilian.NpcManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class NpcCommand implements CommandExecutor, TabCompleter {
    private final NpcManager npcs;

    public NpcCommand(NpcsPlugin ignored, NpcManager npcs) {
        this.npcs = npcs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("npcs.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text("/npc spawn civilian | /npc remove [near|all] | /npc list",
                    NamedTextColor.YELLOW));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "spawn" -> spawn(sender, args);
            case "remove", "kill", "despawn" -> remove(sender, args);
            case "list" -> list(sender);
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean spawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        String kind = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "civilian";
        if (!kind.equals("civilian") && !kind.equals("civ")) {
            sender.sendMessage(Component.text("Known type: civilian", NamedTextColor.RED));
            return true;
        }
        CivilianNpc npc = npcs.spawnCivilian(player.getLocation());
        sender.sendMessage(Component.text("Spawned civilian " + npc.name()
                + " (" + npc.personality().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ")",
                NamedTextColor.GREEN));
        return true;
    }

    private boolean remove(CommandSender sender, String[] args) {
        String what = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "near";
        if (what.equals("all")) {
            int n = npcs.removeAll();
            sender.sendMessage(Component.text("Removed " + n + " civilian(s).", NamedTextColor.YELLOW));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only for near-remove.", NamedTextColor.RED));
            return true;
        }
        int n = npcs.removeNear(player.getLocation(), 8);
        sender.sendMessage(Component.text("Removed " + n + " nearby civilian(s).", NamedTextColor.YELLOW));
        return true;
    }

    private boolean list(CommandSender sender) {
        var all = npcs.all();
        if (all.isEmpty()) {
            sender.sendMessage(Component.text("No civilians spawned.", NamedTextColor.GRAY));
            return true;
        }
        sender.sendMessage(Component.text(all.size() + " civilian(s):", NamedTextColor.GOLD));
        for (CivilianNpc npc : all) {
            sender.sendMessage(Component.text("  " + npc.name() + " — "
                    + npc.personality().name().toLowerCase(Locale.ROOT).replace('_', ' '),
                    NamedTextColor.WHITE));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return prefix(List.of("spawn", "remove", "list"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return prefix(List.of("civilian"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            return prefix(List.of("near", "all"), args[1]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> options, String typed) {
        String low = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(low)).collect(Collectors.toList());
    }
}
