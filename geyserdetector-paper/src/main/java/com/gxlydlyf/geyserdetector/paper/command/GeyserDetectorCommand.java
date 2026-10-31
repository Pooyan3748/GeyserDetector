package com.gxlydlyf.geyserdetector.paper.command;

import com.gxlydlyf.geyserdetector.api.BedrockPlayer;
import com.gxlydlyf.geyserdetector.paper.BedrockRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Operator-facing inspection of what the proxy has told us.
 *
 * <p>Exists mainly for diagnosis: when a plugin misbehaves around Bedrock players,
 * {@code /geyserdetector info} answers "does this server actually know they are
 * Bedrock" without needing a debugger.
 */
public final class GeyserDetectorCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.AQUA + "[GeyserDetector] " + ChatColor.RESET;

    private final BedrockRegistry registry;

    public GeyserDetectorCommand(org.bukkit.plugin.Plugin plugin, BedrockRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            usage(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> info(sender, args);
            case "list" -> list(sender);
            case "status" -> status(sender);
            default -> usage(sender);
        }
        return true;
    }

    private void status(CommandSender sender) {
        sender.sendMessage(PREFIX + "source: " + ChatColor.YELLOW + registry.source());
        sender.sendMessage(PREFIX + "tracked: " + ChatColor.YELLOW + registry.online().size() + " player(s)");
        long since = registry.millisSinceContact();
        String contact = since == Long.MAX_VALUE
                ? ChatColor.RED + "never"
                : ChatColor.GREEN + String.valueOf(since / 1000L) + "s ago";
        sender.sendMessage(PREFIX + "last message from proxy: " + contact);
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /geyserdetector info <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Player not found: " + args[1]);
            return;
        }

        Optional<BedrockPlayer> bedrockPlayer = registry.lookup(target.getUniqueId());
        if (bedrockPlayer.isEmpty()) {
            String qualifier = registry.available()
                    ? ChatColor.GREEN + "Java edition."
                    : ChatColor.YELLOW + "Unknown (no proxy is reporting).";
            sender.sendMessage(PREFIX + target.getName() + " -> " + qualifier);
            return;
        }

        BedrockPlayer player = bedrockPlayer.get();
        sender.sendMessage(PREFIX + ChatColor.WHITE + target.getName() + " -> "
                + ChatColor.GREEN + "Bedrock edition");
        sender.sendMessage(PREFIX + "  platform: " + ChatColor.YELLOW + player.platformName());
        sender.sendMessage(PREFIX + "  version:  " + ChatColor.YELLOW + player.version());
        sender.sendMessage(PREFIX + "  xuid:     " + ChatColor.YELLOW
                + (player.hasXuid() ? player.xuid() : "(none)"));
    }

    private void list(CommandSender sender) {
        var online = registry.online();
        if (online.isEmpty()) {
            sender.sendMessage(PREFIX + "No Bedrock players are tracked.");
            return;
        }
        sender.sendMessage(PREFIX + online.size() + " Bedrock player(s):");
        for (BedrockPlayer player : online) {
            sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + player.username()
                    + ChatColor.GRAY + " (" + player.platformName()
                    + (player.version().isEmpty() ? "" : " " + player.version()) + ")");
        }
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(PREFIX + "Commands:");
        sender.sendMessage(ChatColor.GRAY + " /geyserdetector status"
                + ChatColor.DARK_GRAY + " - detection state");
        sender.sendMessage(ChatColor.GRAY + " /geyserdetector list"
                + ChatColor.DARK_GRAY + " - tracked Bedrock players");
        sender.sendMessage(ChatColor.GRAY + " /geyserdetector info <player>"
                + ChatColor.DARK_GRAY + " - details for one player");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("status", "list", "info").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
