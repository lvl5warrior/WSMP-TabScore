package com.warriorssmp.tabscoreboard;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/** Turns a raw config line into what actually gets shown: {rank} and
 *  {world} resolved first (both handled directly by this plugin, not
 *  PlaceholderAPI - {rank} via WSMP-Duels reflection, {world} via a
 *  config-driven per-world display name lookup), then everything else
 *  through PlaceholderAPI, then any MiniMessage tags (some plugins,
 *  like LuckPerms, can store a prefix in MiniMessage format rather
 *  than legacy codes) converted to match, and only THEN '&' color
 *  codes translated - that specific order matters, see
 *  resolveMiniMessageTags for why. Shared by the scoreboard and
 *  tablist managers so a line means the same thing wherever it's
 *  used. */
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
        // MiniMessage tags MUST be resolved before '&' gets translated to
        // '§' below, not after - confirmed directly from a server log:
        // MiniMessage's parser throws ParsingException outright the
        // moment it sees ANY '§' character already present in the text,
        // even completely unrelated to the actual tags it's trying to
        // parse. Since this plugin's own templates go through '&'
        // first, resolving MiniMessage after that translation meant
        // every single line with a MiniMessage-formatted placeholder
        // value failed to parse - not because of the placeholder's own
        // tags, but because of this plugin's own, completely unrelated
        // color codes sitting right next to them in the same string.
        // Running MiniMessage first avoids this entirely: at that
        // point the string only has raw '&' characters (which
        // MiniMessage treats as harmless plain text, since '&' isn't
        // its syntax) mixed with real <tag> syntax - nothing for it to
        // reject.
        String withMiniMessage = resolveMiniMessageTags(withPlaceholders);
        return ChatColor.translateAlternateColorCodes('&', withMiniMessage);
    }

    /** Some placeholder VALUES (like a LuckPerms group prefix) can be
     *  authored in MiniMessage format (bold tags, hex color tags, etc.)
     *  rather than legacy '&' codes, depending on how the admin set them
     *  up - PlaceholderAPI just hands back whatever raw text is stored,
     *  tags and all, with no translation of its own. This catches those
     *  MiniMessage tags and converts them to the same legacy-style
     *  output the rest of this plugin already works with, so mixed-
     *  format text (this plugin's own '&' templates combined with
     *  another plugin's MiniMessage-formatted values) renders correctly
     *  either way. Uses Paper's own bundled Adventure library, not a
     *  new dependency - Paper has required Adventure for Component-
     *  based APIs for years, so this doesn't add any new risk beyond
     *  paper-api itself. */
    private String resolveMiniMessageTags(String text) {
        if (!text.contains("<")) return text; // fast path - no tags present at all
        try {
            net.kyori.adventure.text.Component component =
                    net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(text);
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .serialize(component);
        } catch (Throwable t) {
            // Confirmed via server log to genuinely fire (not just a
            // theoretical safety net) before the fix above - kept as a
            // real diagnostic tool for any future case this doesn't
            // cover, rather than silently swallowing it again.
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
