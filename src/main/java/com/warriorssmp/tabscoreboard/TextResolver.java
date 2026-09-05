package com.warriorssmp.tabscoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MODIFIED FILE - reconstructed from bytecode. Functional change: resolve(...) now has
 * an overload that takes the config section to use for the {world} token, so
 * ScoreboardManager can resolve Java lines against "scoreboard" and Bedrock lines
 * against "scoreboard-bedrock" independently. The original 2-arg resolve(Player,
 * String) is unchanged in behavior (still defaults to the "scoreboard" section), so
 * TablistManager and anything else calling it needs no changes.
 */
public class TextResolver {

    private static final Pattern STRIP_PATTERN = Pattern.compile("\\{strip:(%[^%]+%)\\}");

    private final TabScoreboardPlugin plugin;
    private final PlaceholderBridge placeholderBridge;
    private final HonorBridge honorBridge;

    TextResolver(TabScoreboardPlugin plugin, PlaceholderBridge placeholderBridge, HonorBridge honorBridge) {
        this.plugin = plugin;
        this.placeholderBridge = placeholderBridge;
        this.honorBridge = honorBridge;
    }

    public String resolve(Player player, String text) {
        return resolve(player, text, "scoreboard");
    }

    /** NEW overload - same as resolve(Player, String) but with a chosen world-map section. */
    public String resolve(Player player, String text, String worldSection) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String working = text.replace("{rank}", honorBridge.getRankDisplay(player));
        if (working.contains("{world}")) {
            working = working.replace("{world}", resolveWorld(player, worldSection));
        }
        String stripped = resolveStrippedPlaceholders(player, working);
        String papiResolved = placeholderBridge.resolve(player, stripped);
        String miniMessageResolved = resolveMiniMessageTags(papiResolved);
        return ChatColor.translateAlternateColorCodes('&', miniMessageResolved);
    }

    private String resolveStrippedPlaceholders(Player player, String text) {
        if (!text.contains("{strip:")) {
            return text;
        }
        Matcher matcher = STRIP_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String resolved = placeholderBridge.resolve(player, placeholder);
            String stripped = ChatColor.stripColor(resolved);
            matcher.appendReplacement(result, stripped == null ? "" : Matcher.quoteReplacement(stripped));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String resolveMiniMessageTags(String text) {
        if (!text.contains("<")) {
            return text;
        }
        try {
            Component component = MiniMessage.miniMessage().deserialize(text);
            return LegacyComponentSerializer.legacySection().serialize(component);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse MiniMessage in '" + text + "': "
                    + e.getClass().getSimpleName() + " " + e.getMessage());
            return text;
        }
    }

    /** section is e.g. "scoreboard" or "scoreboard-bedrock". */
    private String resolveWorld(Player player, String section) {
        ConfigurationSection worldMap = plugin.getConfig().getConfigurationSection(section + ".world-display-by-world");
        String fallback = plugin.getConfig().getString(section + ".world-display-fallback", "");
        return WorldNameResolver.resolve(player, worldMap, fallback);
    }
}
