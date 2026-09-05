package com.warriorssmp.tabscoreboard;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Everything needed to edit scoreboard/tablist config live, from console
 * or in-game, without hand-editing config.yml. Every subcommand that
 * changes something saves to disk immediately and triggers an instant
 * refresh - no separate "reload" needed after using these, that
 * subcommand is only for picking up changes made by hand-editing the
 * file directly.
 */
public class TabScoreboardCommand implements CommandExecutor {

    private final TabScoreboardPlugin plugin;

    public TabScoreboardCommand(TabScoreboardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("wsmptabscoreboard.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Config reloaded from disk.");
            }
            case "set" -> handleSet(sender, args);
            case "clear" -> handleClear(sender, args);
            case "get" -> handleGet(sender, args);
            case "line" -> handleLine(sender, args);
            case "addline" -> handleAddLine(sender, args);
            case "removeline" -> handleRemoveLine(sender, args);
            case "worldheader" -> handleWorldHeader(sender, args);
            case "removeworldheader" -> handleRemoveWorldHeader(sender, args);
            case "list" -> handleList(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /tabscoreboard set <path> <value>");
            sender.sendMessage(ChatColor.GRAY + "Example paths: scoreboard.title, tablist.footer, "
                    + "tablist.prefix, scoreboard.enabled, tablist.header-fallback, "
                    + "scoreboard.update-interval-ticks");
            sender.sendMessage(ChatColor.GRAY + "To blank out a text setting entirely, use "
                    + ChatColor.WHITE + "/tabscoreboard clear <path>" + ChatColor.GRAY
                    + " instead - an empty value isn't something normal command text can express.");
            return;
        }
        String path = args[1];
        String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        plugin.getConfig().set(path, parseValue(value));
        plugin.saveConfig();
        refreshAndConfirm(sender, "Set " + ChatColor.WHITE + path + ChatColor.GREEN
                + " to " + ChatColor.WHITE + value);
    }

    /** Sets a text path to an empty string - "/tabscoreboard set <path> "
     *  can't actually express this, since a value made only of trailing
     *  space is either trimmed by the chat box or collapses to zero
     *  arguments by the time Bukkit splits the command into tokens. This
     *  is the only reliable way to blank something out (like the tab
     *  footer) without deleting the whole path from config.yml. */
    private void handleClear(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /tabscoreboard clear <path>");
            sender.sendMessage(ChatColor.GRAY + "Sets that path to an empty value - e.g. "
                    + "/tabscoreboard clear tablist.footer");
            return;
        }
        String path = args[1];
        plugin.getConfig().set(path, "");
        plugin.saveConfig();
        refreshAndConfirm(sender, "Cleared " + ChatColor.WHITE + path + ChatColor.GREEN + " to empty.");
    }

    private void handleGet(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /tabscoreboard get <path>");
            return;
        }
        String path = args[1];
        sender.sendMessage(ChatColor.YELLOW + path + ChatColor.GRAY + " = "
                + ChatColor.WHITE + plugin.getConfig().get(path));
    }

    private void handleLine(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /tabscoreboard line <number> <text>");
            sender.sendMessage(ChatColor.GRAY + "Line numbers start at 1, matching /tabscoreboard list.");
            return;
        }
        int index = parseLineNumber(sender, args[1]);
        if (index < 0) return;
        List<String> lines = new ArrayList<>(plugin.getConfig().getStringList("scoreboard.lines"));
        if (index >= lines.size()) {
            sender.sendMessage(ChatColor.RED + "There's no line " + (index + 1) + " - the sidebar only has "
                    + lines.size() + " lines right now. Use addline to add a new one.");
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        lines.set(index, text);
        plugin.getConfig().set("scoreboard.lines", lines);
        plugin.saveConfig();
        refreshAndConfirm(sender, "Set line " + (index + 1) + " to " + ChatColor.WHITE + text);
    }

    private void handleAddLine(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /tabscoreboard addline <text>");
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        List<String> lines = new ArrayList<>(plugin.getConfig().getStringList("scoreboard.lines"));
        lines.add(text);
        plugin.getConfig().set("scoreboard.lines", lines);
        plugin.saveConfig();
        refreshAndConfirm(sender, "Added line " + lines.size() + ": " + ChatColor.WHITE + text);
    }

    private void handleRemoveLine(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /tabscoreboard removeline <number>");
            return;
        }
        int index = parseLineNumber(sender, args[1]);
        if (index < 0) return;
        List<String> lines = new ArrayList<>(plugin.getConfig().getStringList("scoreboard.lines"));
        if (index >= lines.size()) {
            sender.sendMessage(ChatColor.RED + "There's no line " + (index + 1) + " - the sidebar only has "
                    + lines.size() + " lines right now.");
            return;
        }
        String removed = lines.remove(index);
        plugin.getConfig().set("scoreboard.lines", lines);
        plugin.saveConfig();
        refreshAndConfirm(sender, "Removed line " + (index + 1) + " (was: " + ChatColor.WHITE + removed
                + ChatColor.GREEN + ")");
    }

    private void handleWorldHeader(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /tabscoreboard worldheader <world name> <text>");
            sender.sendMessage(ChatColor.GRAY + "World name must exactly match the real world's folder "
                    + "name (case-sensitive).");
            return;
        }
        String worldName = args[1];
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        plugin.getConfig().set("tablist.header-by-world." + worldName, text);
        plugin.saveConfig();
        refreshAndConfirm(sender, "Set the tab header for world " + ChatColor.WHITE + worldName
                + ChatColor.GREEN + " to " + ChatColor.WHITE + text);
    }

    private void handleRemoveWorldHeader(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /tabscoreboard removeworldheader <world name>");
            return;
        }
        String worldName = args[1];
        plugin.getConfig().set("tablist.header-by-world." + worldName, null);
        plugin.saveConfig();
        refreshAndConfirm(sender, "Removed the tab header entry for world " + ChatColor.WHITE + worldName);
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "-- Scoreboard --");
        sender.sendMessage(ChatColor.YELLOW + "Title: " + ChatColor.WHITE
                + plugin.getConfig().getString("scoreboard.title"));
        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        for (int i = 0; i < lines.size(); i++) {
            sender.sendMessage(ChatColor.YELLOW + "" + (i + 1) + ": " + ChatColor.WHITE + lines.get(i));
        }
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "-- Tablist --");
        sender.sendMessage(ChatColor.YELLOW + "Prefix: " + ChatColor.WHITE
                + plugin.getConfig().getString("tablist.prefix"));
        sender.sendMessage(ChatColor.YELLOW + "Suffix: " + ChatColor.WHITE
                + plugin.getConfig().getString("tablist.suffix"));
        sender.sendMessage(ChatColor.YELLOW + "Footer: " + ChatColor.WHITE
                + plugin.getConfig().getString("tablist.footer"));
        sender.sendMessage(ChatColor.YELLOW + "Fallback header: " + ChatColor.WHITE
                + plugin.getConfig().getString("tablist.header-fallback"));
        var section = plugin.getConfig().getConfigurationSection("tablist.header-by-world");
        if (section != null) {
            sender.sendMessage(ChatColor.YELLOW + "Per-world headers:");
            for (String key : section.getKeys(false)) {
                sender.sendMessage(ChatColor.GRAY + "  " + key + ": " + ChatColor.WHITE + section.getString(key));
            }
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "WSMP-TabScoreboard commands:");
        sender.sendMessage(ChatColor.YELLOW + "/tabscoreboard list " + ChatColor.GRAY + "- show every current setting");
        sender.sendMessage(ChatColor.YELLOW + "/tabscoreboard reload " + ChatColor.GRAY
                + "- reload config.yml from disk");
        sender.sendMessage(ChatColor.YELLOW + "/tabscoreboard set <path> <value> " + ChatColor.GRAY
                + "- set any setting directly");
        sender.sendMessage(ChatColor.YELLOW + "/tabscoreboard clear <path> " + ChatColor.GRAY
                + "- blank out a text setting entirely");
        sender.sendMessage(ChatColor.YELLOW + "/tabscoreboard get <path> " + ChatColor.GRAY
                + "- view a setting's current value");
        sender.sendMessage(ChatColor.YELLOW + "/tabscoreboard line <number> <text> " + ChatColor.GRAY
                + "- edit a sidebar line");
        sender.sendMessage(ChatColor.YELLOW + "/tabscoreboard addline <text> " + ChatColor.GRAY
                + "- add a new sidebar line");
        sender.sendMessage(ChatColor.YELLOW + "/tabscoreboard removeline <number> " + ChatColor.GRAY
                + "- remove a sidebar line");
        sender.sendMessage(ChatColor.YELLOW + "/tabscoreboard worldheader <world> <text> " + ChatColor.GRAY
                + "- set a per-world tab header");
        sender.sendMessage(ChatColor.YELLOW + "/tabscoreboard removeworldheader <world> " + ChatColor.GRAY
                + "- remove a per-world tab header");
    }

    private int parseLineNumber(CommandSender sender, String raw) {
        try {
            return Integer.parseInt(raw) - 1;
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "'" + raw + "' isn't a valid line number.");
            return -1;
        }
    }

    /** Config values typed on the command line arrive as plain strings,
     *  but settings like scoreboard.enabled or update-interval-ticks
     *  need to be a real boolean/number for the rest of the plugin to
     *  read correctly - this converts anything that unambiguously looks
     *  like one, and otherwise leaves it as text. */
    private Object parseValue(String raw) {
        if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) return Boolean.parseBoolean(raw);
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return raw;
        }
    }

    private void refreshAndConfirm(CommandSender sender, String message) {
        plugin.getScoreboardManager().refreshAllNow();
        plugin.getTablistManager().refreshAll();
        sender.sendMessage(ChatColor.GREEN + message);
    }
}
