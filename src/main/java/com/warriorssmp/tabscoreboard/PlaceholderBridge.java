package com.warriorssmp.tabscoreboard;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Resolves %placeholder% text through PlaceholderAPI using pure
 * reflection - deliberately avoiding a compile-time dependency on
 * PlaceholderAPI's own API jar, since that would mean this project's
 * build depends on being able to reach PlaceholderAPI's Maven
 * repository, which hasn't been verified as reachable from wherever
 * this gets built. PlaceholderAPI is already confirmed running on the
 * actual server this plugin targets, so calling its one static method
 * reflectively at runtime is all that's actually needed here.
 *
 * Fails soft everywhere: if PlaceholderAPI isn't installed, or the
 * reflective call fails for any reason, the original text is returned
 * completely unresolved rather than throwing - a scoreboard line that
 * still shows a raw %placeholder% is a far better failure mode than a
 * broken scoreboard or a plugin that won't load.
 */
public class PlaceholderBridge {

    private final JavaPlugin plugin;
    private Method setPlaceholdersMethod;
    private boolean attemptedLookup = false;

    public PlaceholderBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null
                && resolveMethod() != null;
    }

    public String resolve(OfflinePlayer player, String text) {
        if (text == null || text.isEmpty()) return text;
        Method method = resolveMethod();
        if (method == null) return text;
        try {
            Object result = method.invoke(null, player, text);
            return result instanceof String ? (String) result : text;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "PlaceholderAPI call failed for text '" + text + "'", t);
            return text;
        }
    }

    private Method resolveMethod() {
        if (attemptedLookup) return setPlaceholdersMethod;
        attemptedLookup = true;
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            setPlaceholdersMethod = papiClass.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (Throwable t) {
            setPlaceholdersMethod = null;
        }
        return setPlaceholdersMethod;
    }
}
