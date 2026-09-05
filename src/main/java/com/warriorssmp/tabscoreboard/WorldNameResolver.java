package com.warriorssmp.tabscoreboard;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Looks up a display name for whichever world a player is currently
 * in, from a config.yml map of world name -> display text. Used by
 * both the tab-list header (tablist.header-by-world) and the
 * scoreboard's {world} token (scoreboard.world-display-by-world) -
 * factored out here since both need the exact same matching rules:
 * an EXACT match first (so the order entries happen to be listed in
 * config.yml never matters for real, non-cloned world names), and
 * only a prefix match as a fallback for cloned match instances
 * (world_arena_instance_ab12cd34 and the like, whose exact name is
 * never the same twice) - picking the LONGEST matching prefix so a
 * more specific entry can never lose out to a shorter, coincidentally
 * matching one.
 */
public class WorldNameResolver {

    private WorldNameResolver() {
    }

    public static String resolve(Player viewer, ConfigurationSection section, String fallback) {
        String worldName = viewer.getWorld().getName();
        if (section != null) {
            if (section.contains(worldName)) return section.getString(worldName, fallback);
            String bestKey = null;
            for (String key : section.getKeys(false)) {
                if (worldName.startsWith(key) && (bestKey == null || key.length() > bestKey.length())) {
                    bestKey = key;
                }
            }
            if (bestKey != null) return section.getString(bestKey, fallback);
        }
        return fallback;
    }
}
