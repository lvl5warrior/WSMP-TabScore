package com.warriorssmp.tabscoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MODIFIED FILE - reconstructed from bytecode (no decompiler/network was available in
 * the environment this was written in), then extended for Bedrock support. The overall
 * shape/logic matches the original exactly; the only functional addition is choosing
 * between the "scoreboard" and "scoreboard-bedrock" config sections per player. One
 * cosmetic detail could not be recovered exactly and was reconstructed as an
 * equivalent: the internal per-line scoreboard team name ("line" + i below) - this is
 * a purely internal identifier never shown to players, so any unique-per-line naming
 * works the same either way.
 */
public class ScoreboardManager {

    private static final String OBJECTIVE_NAME = "wsmp_sb";
    private static final int MAX_LINES = 15;

    private final TabScoreboardPlugin plugin;
    private final TextResolver textResolver;
    private final FloodgateBridge floodgateBridge;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    ScoreboardManager(TabScoreboardPlugin plugin, TextResolver textResolver, FloodgateBridge floodgateBridge) {
        this.plugin = plugin;
        this.textResolver = textResolver;
        this.floodgateBridge = floodgateBridge;
    }

    void start() {
        if (!anyEnabled()) {
            return;
        }
        long interval = plugin.getConfig().getLong("scoreboard.update-interval-ticks", 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, Math.max(1L, interval));
    }

    void refreshAll() {
        if (!anyEnabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    void refreshOne(Player player) {
        if (!anyEnabled()) {
            return;
        }
        refresh(player);
    }

    void refreshAllNow() {
        refreshAll();
    }

    /**
     * NEW. The scheduler (and manual refreshes) should keep running as long as EITHER
     * audience has their board turned on, since a single pass of refreshAll() handles
     * both Java and Bedrock players, just picking a different section per player.
     */
    private boolean anyEnabled() {
        return plugin.getConfig().getBoolean("scoreboard.enabled", true)
                || plugin.getConfig().getBoolean("scoreboard-bedrock.enabled", false);
    }

    void refresh(Player player) {
        // NEW: pick which config section drives this player's board. The Bedrock
        // section only actually applies if the player is really connecting through
        // Floodgate AND that section has been turned on - so a server that hasn't
        // touched scoreboard-bedrock at all sees zero behavior change here.
        boolean useBedrock = plugin.getConfig().getBoolean("scoreboard-bedrock.enabled", false)
                && floodgateBridge.isBedrockPlayer(player);
        String section = useBedrock ? "scoreboard-bedrock" : "scoreboard";

        if (!plugin.getConfig().getBoolean(section + ".enabled", section.equals("scoreboard"))) {
            return;
        }

        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), uuid -> {
            Scoreboard newBoard = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(newBoard);
            return newBoard;
        });

        String title = textResolver.resolve(player, plugin.getConfig().getString(section + ".title", ""), section);
        if (title.isEmpty()) {
            title = " ";
        }

        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_NAME, "dummy", title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.numberFormat(NumberFormat.blank());
        } else if (!objective.getDisplayName().equals(title)) {
            objective.setDisplayName(title);
        }

        List<String> lines = plugin.getConfig().getStringList(section + ".lines");
        int count = Math.min(lines.size(), MAX_LINES);

        for (String entry : new ArrayList<>(board.getEntries())) {
            board.resetScores(entry);
        }

        ChatColor[] colors = ChatColor.values();
        for (int i = 0; i < count; i++) {
            String resolvedLine = textResolver.resolve(player, lines.get(i), section);
            String entry = colors[i % colors.length].toString() + ChatColor.RESET;
            String teamName = "line" + i;

            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
            }
            for (String existing : new ArrayList<>(team.getEntries())) {
                team.removeEntry(existing);
            }
            team.addEntry(entry);
            team.setPrefix(resolvedLine.length() > 64 ? resolvedLine.substring(0, 64) : resolvedLine);
            objective.getScore(entry).setScore(count - i);
        }
    }

    void forget(Player player) {
        boards.remove(player.getUniqueId());
    }
}
