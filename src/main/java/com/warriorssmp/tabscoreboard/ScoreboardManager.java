package com.warriorssmp.tabscoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A per-player sidebar scoreboard, refreshed on a timer straight from
 * config.yml's scoreboard.lines. Each line is rendered through a Team's
 * prefix rather than the raw scoreboard "entry" text - the long
 * established, broadly compatible technique for scoreboard lines that
 * need more than the very old 16-character entry limit, and for
 * avoiding collisions when two lines happen to resolve to identical
 * visible text (blank lines, for instance).
 */
public class ScoreboardManager {

    private static final String OBJECTIVE_NAME = "wsmp_sb";
    // Vanilla's own sidebar has never rendered more than 15 score lines
    // at once regardless of version, so this is a safe, real ceiling
    // rather than an arbitrary one.
    private static final int MAX_LINES = 15;

    private final TabScoreboardPlugin plugin;
    private final TextResolver textResolver;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public ScoreboardManager(TabScoreboardPlugin plugin, TextResolver textResolver) {
        this.plugin = plugin;
        this.textResolver = textResolver;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        long interval = plugin.getConfig().getLong("scoreboard.update-interval-ticks", 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, Math.max(1L, interval));
    }

    private void refreshAll() {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    /** Public entry point for an immediate, single-player refresh (used
     *  right on join, rather than waiting for the next periodic tick). */
    public void refreshOne(Player player) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        refresh(player);
    }

    /** Public entry point for an immediate, all-players refresh - used
     *  right after a console/in-game edit, so the change is visible
     *  straight away instead of waiting for the next periodic tick. */
    public void refreshAllNow() {
        refreshAll();
    }

    private void refresh(Player player) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), id -> {
            Scoreboard fresh = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(fresh);
            return fresh;
        });

        String title = textResolver.resolve(player, plugin.getConfig().getString("scoreboard.title", ""));
        if (title.isEmpty()) title = " "; // an empty objective display name isn't valid

        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_NAME, "dummy", title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            // Vanilla shows a number next to every sidebar line by
            // default - this is the confirmed, modern (1.20.3+) way to
            // remove that entirely, rather than the number just being an
            // unavoidable part of how scoreboards work.
            objective.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        } else if (!objective.getDisplayName().equals(title)) {
            objective.setDisplayName(title);
        }

        List<String> rawLines = plugin.getConfig().getStringList("scoreboard.lines");
        int lineCount = Math.min(rawLines.size(), MAX_LINES);

        // Clear anything left over from a previous, longer set of lines
        // before writing the current ones, so shrinking the config
        // doesn't leave stale lines behind at the bottom.
        for (String existingEntry : new ArrayList<>(board.getEntries())) {
            board.resetScores(existingEntry);
        }

        ChatColor[] colors = ChatColor.values();
        for (int i = 0; i < lineCount; i++) {
            String resolved = textResolver.resolve(player, rawLines.get(i));
            String entry = colors[i % colors.length].toString() + ChatColor.RESET;

            String teamName = "wsmp_sb_line" + i;
            Team team = board.getTeam(teamName);
            if (team == null) team = board.registerNewTeam(teamName);
            for (String oldEntry : new ArrayList<>(team.getEntries())) team.removeEntry(oldEntry);
            team.addEntry(entry);
            team.setPrefix(resolved.length() <= 64 ? resolved : resolved.substring(0, 64));

            Score score = objective.getScore(entry);
            score.setScore(lineCount - i);
        }
    }

    /** Called on quit so a departed player's UUID doesn't sit in memory
     *  forever - the Scoreboard object itself is discarded along with
     *  the player when they log out regardless. */
    public void forget(Player player) {
        boards.remove(player.getUniqueId());
    }
}
