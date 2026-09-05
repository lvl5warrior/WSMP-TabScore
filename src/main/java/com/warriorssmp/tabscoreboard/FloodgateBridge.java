package com.warriorssmp.tabscoreboard;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * NEW FILE.
 *
 * Soft dependency on Floodgate, used to tell whether a connected player is coming in
 * through Bedrock (Geyser/Floodgate) rather than native Java. Mirrors the same
 * reflection-based pattern PlaceholderBridge and HonorBridge already use elsewhere in
 * this plugin, so the jar still loads fine on servers that don't have Floodgate
 * installed - isBedrockPlayer(...) just always returns false in that case.
 *
 * NOTE: Floodgate-Spigot registers itself with the plugin name "floodgate" (lowercase).
 * If your install uses a differently-cased name, adjust the getPlugin(...) lookup below.
 */
public class FloodgateBridge {

    private final JavaPlugin plugin;
    private boolean attemptedLookup;
    private Object apiInstance;
    private Method isFloodgatePlayerMethod;

    FloodgateBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("floodgate") != null
                && resolveMethod() != null;
    }

    public boolean isBedrockPlayer(Player player) {
        Method method = resolveMethod();
        if (method == null) {
            return false;
        }
        try {
            Object result = method.invoke(apiInstance, player.getUniqueId());
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Failed checking Floodgate status for " + player.getName(), e);
            return false;
        }
    }

    private Method resolveMethod() {
        if (attemptedLookup) {
            return isFloodgatePlayerMethod;
        }
        attemptedLookup = true;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            apiInstance = getInstance.invoke(null);
            isFloodgatePlayerMethod = apiClass.getMethod("isFloodgatePlayer", UUID.class);
        } catch (Exception e) {
            apiInstance = null;
            isFloodgatePlayerMethod = null;
        }
        return isFloodgatePlayerMethod;
    }
}
