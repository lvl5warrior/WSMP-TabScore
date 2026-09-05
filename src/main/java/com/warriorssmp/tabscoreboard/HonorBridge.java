package com.warriorssmp.tabscoreboard;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Resolves the {rank} token by reading WSMP-Duels' own Honor Rank data
 * directly, through reflection - WSMP-Duels isn't published as a Maven
 * artifact anywhere, so a normal compile-time dependency on it isn't
 * even an option here, only ever a runtime one. Every step is wrapped so
 * a missing plugin, a renamed method, or any other mismatch just yields
 * an empty/neutral result rather than breaking the rest of the
 * scoreboard/tablist.
 */
public class HonorBridge {

    /** Both pieces of rank data a single reflective lookup produces -
     *  the display text for {rank}, and the ordinal (0 = Private, higher
     *  = better) used purely for sorting players into tab-list groups by
     *  rank tier. Bundled together since they always come from the same
     *  lookup and it'd be wasteful to do that reflection twice. */
    public record RankInfo(String displayName, int ordinal) {
        static final RankInfo UNKNOWN = new RankInfo("", -1);
    }

    private final JavaPlugin plugin;

    public HonorBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        Plugin duels = plugin.getServer().getPluginManager().getPlugin("WSMP-Duels");
        return duels != null && duels.isEnabled();
    }

    /** The player's current Honor Rank display name (with its own color
     *  codes already baked in by WSMP-Duels, e.g. "&6Marshal"), or an
     *  empty string if WSMP-Duels isn't installed/enabled or anything
     *  about the reflective call chain doesn't line up. */
    public String getRankDisplay(Player player) {
        return getRankInfo(player).displayName();
    }

    public RankInfo getRankInfo(Player player) {
        try {
            Plugin duels = plugin.getServer().getPluginManager().getPlugin("WSMP-Duels");
            if (duels == null || !duels.isEnabled()) return RankInfo.UNKNOWN;

            Object playerDataManager = duels.getClass().getMethod("getPlayerDataManager").invoke(duels);
            Object playerData = playerDataManager.getClass().getMethod("get", UUID.class)
                    .invoke(playerDataManager, player.getUniqueId());
            int lifetimeHonor = (int) playerData.getClass().getMethod("getLifetimeHonor").invoke(playerData);

            Class<?> honorRankClass = Class.forName("com.warriorssmp.duels.HonorRank");
            Object rank = honorRankClass.getMethod("forLifetimeHonor", int.class).invoke(null, lifetimeHonor);
            Method getDisplayName = honorRankClass.getMethod("getDisplayName");
            Object displayName = getDisplayName.invoke(rank);
            int ordinal = (int) rank.getClass().getMethod("ordinal").invoke(rank);
            return new RankInfo(displayName instanceof String ? (String) displayName : "", ordinal);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Could not read Honor Rank for " + player.getName()
                    + " from WSMP-Duels", t);
            return RankInfo.UNKNOWN;
        }
    }
}
