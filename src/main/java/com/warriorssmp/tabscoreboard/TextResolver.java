package com.warriorssmp.tabscoreboard;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/** Turns a raw config line into what actually gets shown: {rank} and
 *  {world} resolved first (both handled directly by this plugin, not
 *  PlaceholderAPI - {rank} via WSMP-Duels reflection, {world} via a
 *  config-driven per-world display name lookup), then everything else
 *  through PlaceholderAPI, then '&' color codes translated, then any
 *  remaining MiniMessage tags (some plugins, like LuckPerms, can store
 *  a prefix in MiniMessage format rather than legacy codes) converted
 *  to match. Shared by the scoreboard and tablist managers so a line
 *  means the same thing wherever it's used. */
public class TextResolver {

    private final TabScoreboardPlugin plugin;
    private final PlaceholderBridge placeholderBridge;
    private final HonorBridge honorBridge;

    public TextResolver(TabScoreboardPlugin plugin, PlaceholderBridge placeholderBridge, HonorBridge honorBridge) {
        this.plugin = plugin;
        this.placeholderBridge = placeholderBridge;
        this.honorBridge = honorBridge;
    }

    public String resolve(Player player, String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String withRank = raw.replace("{rank}", honorBridge.getRankDisplay(player));
        String withWorld = withRank.contains("{world}") ? withRank.replace("{world}", resolveWorld(player)) : withRank;
        String withPlaceholders = placeholderBridge.resolve(player, withWorld);
        String legacyTranslated = ChatColor.translateAlternateColorCodes('&', withPlaceholders);
        return resolveMiniMessageTags(legacyTranslated);
    }

    /** Some placeholder VALUES (like a LuckPerms group prefix) can be
     *  authored in MiniMessage format (bold tags, hex color tags, etc.)
     *  rather than legacy '&' codes, depending on how the admin set them
     *  up - PlaceholderAPI just hands back whatever raw text is stored,
     *  tags and all, with no translation of its own. This catches any
     *  MiniMessage tags still present after legacy-code translation
     *  above and converts them to the same legacy-style output the rest
     *  of this plugin already works with, so mixed-format text (this
     *  plugin's own '&' templates combined with another plugin's
     *  MiniMessage-formatted values) renders correctly either way. Uses
     *  Paper's own bundled Adventure library, not a new dependency -
     *  Paper has required Adventure for Component-based APIs for years,
     *  so this doesn't add any new risk beyond paper-api itself. */
    private String resolveMiniMessageTags(String text) {
        if (!text.contains("<")) return text; // fast path - no tags present at all
        try {
            net.kyori.adventure.text.Component component =
                    net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(text);
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .serialize(component);
        } catch (Throwable t) {
            // Previously silent - logging this now since there was no way
            // to tell "the fix isn't deployed yet" apart from "the fix is
            // deployed but genuinely failing to parse this text" without
            // it. Check the server console/log for this warning after a
            // MiniMessage-formatted value shows up unconverted.
            plugin.getLogger().warning("Failed to parse MiniMessage tags in text '" + text + "': "
                    + t.getClass().getSimpleName() + " - " + t.getMessage());
            return text; // malformed/unexpected tags - show the raw text rather than break
        }
    }

    private String resolveWorld(Player player) {
        var section = plugin.getConfig().getConfigurationSection("scoreboard.world-display-by-world");
        String fallback = plugin.getConfig().getString("scoreboard.world-display-fallback", "");
        return WorldNameResolver.resolve(player, section, fallback);
    }
}
