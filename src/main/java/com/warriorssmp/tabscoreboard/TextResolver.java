package com.warriorssmp.tabscoreboard;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns a raw config line into what actually gets shown: {rank} and
 *  {world} resolved first (both handled directly by this plugin, not
 *  PlaceholderAPI - {rank} via WSMP-Duels reflection, {world} via a
 *  config-driven per-world display name lookup), then {strip:%...%}
 *  placeholders resolved and stripped of their own embedded colors,
 *  then everything else through PlaceholderAPI, then any MiniMessage
 *  tags converted to match, and only THEN '&' color codes translated -
 *  that specific order matters, see resolveMiniMessageTags for why.
 *  Shared by the scoreboard and tablist managers so a line means the
 *  same thing wherever it's used. */
public class TextResolver {

    // Matches {strip:%some_placeholder%} - a config author's way of
    // saying "resolve this one placeholder and throw away whatever
    // color it comes with, so my own surrounding color code is the
    // only one that actually applies".
    private static final Pattern STRIP_PATTERN = Pattern.compile("\\{strip:(%[^%]+%)\\}");

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
        String withStripped = resolveStrippedPlaceholders(player, withWorld);
        String withPlaceholders = placeholderBridge.resolve(player, withStripped);
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

    /** Handles {strip:%placeholder%} tokens specifically: some
     *  placeholders (a job/item "highest level" stat, for instance)
     *  embed their own color codes in their output, chosen by whichever
     *  plugin registered them - completely reasonably from that
     *  plugin's own point of view, but it means two different stats
     *  from two different plugins can come out in two different,
     *  clashing colors no matter what color this plugin's own template
     *  puts around them, since the embedded color simply overrides it
     *  partway through the line. Wrapping a placeholder in
     *  {strip:...} resolves it in isolation and strips every color/
     *  formatting code from the result before it goes anywhere near the
     *  rest of the line, so this plugin's own surrounding color is the
     *  only one left standing. Left as an explicit, opt-in wrapper
     *  rather than something applied globally, since {rank} and
     *  %luckperms_prefix% specifically rely on keeping their own colors
     *  (earned rank tier, personalized Discord color) - stripping
     *  everything by default would break those on purpose-built
     *  features, not just accidentally-inconsistent ones. */
    private String resolveStrippedPlaceholders(Player player, String text) {
        if (!text.contains("{strip:")) return text; // fast path - token not present at all
        Matcher matcher = STRIP_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String resolved = placeholderBridge.resolve(player, placeholder);
            String stripped = ChatColor.stripColor(resolved);
            matcher.appendReplacement(result, Matcher.quoteReplacement(stripped == null ? "" : stripped));
        }
        matcher.appendTail(result);
        return result.toString();
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
