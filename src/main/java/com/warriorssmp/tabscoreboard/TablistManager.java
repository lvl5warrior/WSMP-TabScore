package com.warriorssmp.tabscoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tab-list header, footer, and per-player name prefix/suffix. Getting
 * this right involves two separate subtleties.
 *
 * First: a player's tab-list name is formatted according to the TEAM
 * they belong to on whichever scoreboard the VIEWER is looking at - not
 * a team on the target player's own scoreboard. Since ScoreboardManager
 * gives every player their own individual Scoreboard object (needed for
 * personalized sidebar content like each player's own ping), every
 * target player's team has to be mirrored onto every online player's
 * own board, or most players would only ever see their own tab list
 * correctly and nobody else's.
 *
 * Second: to get players of the same rank to visually cluster together
 * (the closest achievable approximation to old TAB's grouped sections,
 * given the ProtocolLib-dependent alternative was deliberately left out
 * of scope - see below), each player still gets THEIR OWN team, not one
 * shared per rank tier - a shared team would force every member of a
 * rank to show identical prefix/suffix text, which breaks the moment
 * that text includes anything per-player like a class path or WSMP-Teams
 * team name. Instead, every player's own team NAME starts with a
 * rank-based sort key, so Minecraft's own alphabetical team-name sort
 * still clusters same-rank players together, while each one's prefix
 * and suffix stay fully personalized.
 *
 * A true section header - a label row with no player attached, players
 * indented underneath - isn't something plain Bukkit's Team API can do
 * at all; that needs packet-level fake entries (ProtocolLib or
 * similar), which was deliberately left out of scope here rather than
 * adding a new, unverified dependency.
 */
public class TablistManager {

    private final TabScoreboardPlugin plugin;
    private final TextResolver textResolver;

    public TablistManager(TabScoreboardPlugin plugin, TextResolver textResolver) {
        this.plugin = plugin;
        this.textResolver = textResolver;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("tablist.enabled", true)) return;
        long interval = plugin.getConfig().getLong("tablist.update-interval-ticks", 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, Math.max(1L, interval));
    }

    public void refreshAll() {
        if (!plugin.getConfig().getBoolean("tablist.enabled", true)) return;

        String footerRaw = plugin.getConfig().getString("tablist.footer", "");
        String prefixRaw = plugin.getConfig().getString("tablist.prefix", "");
        String suffixRaw = plugin.getConfig().getString("tablist.suffix", "");

        // Resolved once per target player, then reused across every
        // viewer's board below - each target's own prefix/suffix should
        // mean the same thing no matter whose screen it's rendered on.
        Map<UUID, String> teamNameByTarget = new HashMap<>();
        Map<UUID, String> prefixByTarget = new HashMap<>();
        Map<UUID, String> suffixByTarget = new HashMap<>();

        for (Player target : Bukkit.getOnlinePlayers()) {
            HonorBridge.RankInfo rank = plugin.getHonorBridge().getRankInfo(target);
            // Sort key: a higher ordinal (better rank) needs to sort
            // FIRST alphabetically, and unranked/unavailable (-1) needs
            // to sort LAST - subtracting from a fixed ceiling and
            // zero-padding keeps this a simple, reliable string sort no
            // matter how many rank tiers WSMP-Duels ever ends up with.
            String sortKey = String.format("%02d", rank.ordinal() < 0 ? 98 : 50 - rank.ordinal());
            teamNameByTarget.put(target.getUniqueId(), "wsmp_tl_" + sortKey + "_" + shortId(target.getUniqueId()));
            prefixByTarget.put(target.getUniqueId(), textResolver.resolve(target, prefixRaw));
            suffixByTarget.put(target.getUniqueId(), textResolver.resolve(target, suffixRaw));
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            // The header is the one thing that's genuinely per-viewer in
            // its raw text, not just its resolved placeholders - it
            // depends on which world THIS viewer is currently standing
            // in, so it has to be looked up fresh for each one rather
            // than resolved once and reused like the footer/prefix above.
            String headerRaw = headerFor(viewer);
            viewer.setPlayerListHeaderFooter(
                    textResolver.resolve(viewer, headerRaw),
                    textResolver.resolve(viewer, footerRaw));

            Scoreboard board = viewer.getScoreboard();
            // If this viewer doesn't have their own individual board yet
            // (e.g. the sidebar is disabled in config, or hasn't run its
            // first tick yet), fall back to creating one here - prefixes
            // need somewhere of the viewer's own to live regardless of
            // whether the sidebar feature is on.
            if (board == null || board.equals(Bukkit.getScoreboardManager().getMainScoreboard())) {
                board = Bukkit.getScoreboardManager().getNewScoreboard();
                viewer.setScoreboard(board);
            }

            for (Player target : Bukkit.getOnlinePlayers()) {
                String teamName = teamNameByTarget.get(target.getUniqueId());
                Team team = board.getTeam(teamName);
                if (team == null) {
                    // A player can only ever be in one team per
                    // scoreboard - if a rank change since the last
                    // refresh means their team name is now different,
                    // clear their old one first.
                    Team existing = board.getEntryTeam(target.getName());
                    if (existing != null) existing.removeEntry(target.getName());
                    team = board.registerNewTeam(teamName);
                }
                String prefix = prefixByTarget.getOrDefault(target.getUniqueId(), "");
                if (prefix.length() > 64) prefix = prefix.substring(0, 64);
                if (!prefix.equals(team.getPrefix())) team.setPrefix(prefix);
                String suffix = suffixByTarget.getOrDefault(target.getUniqueId(), "");
                if (suffix.length() > 64) suffix = suffix.substring(0, 64);
                if (!suffix.equals(team.getSuffix())) team.setSuffix(suffix);
                if (!team.hasEntry(target.getName())) team.addEntry(target.getName());
            }
        }
    }

    private String shortId(UUID id) {
        return id.toString().replace("-", "").substring(0, 8);
    }

    /** Which raw header text applies to a viewer, based on the exact
     *  name of the world they're currently in - checked in config.yml's
     *  tablist.header-by-world map. Tries an EXACT match first (which is
     *  what every real, non-cloned world name needs, and means the order
     *  entries happen to be listed in config.yml never matters for
     *  those), and only falls back to a prefix match for cloned match
     *  instances (world_arena_instance_ab12cd34 and the like, whose
     *  exact name is never the same twice) - picking the LONGEST
     *  matching prefix specifically so a more specific entry like
     *  "world_arena" can never lose out to a shorter, coincidentally
     *  matching one like a bare "world". */
    /** The full header shown to a viewer - a fixed "Welcome to Warriors
     *  SMP" line on top, with the per-world label (which area they're
     *  currently in) on its own line underneath. Tab-list headers
     *  support embedded newlines natively, so this is genuinely two
     *  lines in one string rather than a visual trick. */
    private String headerFor(Player viewer) {
        String welcome = plugin.getConfig().getString("tablist.header-welcome", "");
        var section = plugin.getConfig().getConfigurationSection("tablist.header-by-world");
        String fallback = plugin.getConfig().getString("tablist.header-fallback", "");
        String worldLine = WorldNameResolver.resolve(viewer, section, fallback);
        if (welcome.isEmpty()) return worldLine;
        if (worldLine.isEmpty()) return welcome;
        return welcome + "\n" + worldLine;
    }
}
