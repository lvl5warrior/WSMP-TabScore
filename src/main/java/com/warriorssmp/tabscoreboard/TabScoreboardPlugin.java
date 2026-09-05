package com.warriorssmp.tabscoreboard;

import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MODIFIED FILE - reconstructed from bytecode. Functional change: constructs a
 * FloodgateBridge alongside the existing PlaceholderBridge/HonorBridge, passes it into
 * ScoreboardManager, and warns on startup if scoreboard-bedrock is turned on but
 * Floodgate isn't installed.
 */
public class TabScoreboardPlugin extends JavaPlugin implements Listener {

    private PlaceholderBridge placeholderBridge;
    private HonorBridge honorBridge;
    private FloodgateBridge floodgateBridge;
    private TextResolver textResolver;
    private ScoreboardManager scoreboardManager;
    private TablistManager tablistManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.placeholderBridge = new PlaceholderBridge(this);
        this.honorBridge = new HonorBridge(this);
        this.floodgateBridge = new FloodgateBridge(this); // NEW
        this.textResolver = new TextResolver(this, placeholderBridge, honorBridge);
        this.scoreboardManager = new ScoreboardManager(this, textResolver, floodgateBridge); // floodgateBridge added
        this.tablistManager = new TablistManager(this, textResolver);

        getServer().getPluginManager().registerEvents(this, this);

        scoreboardManager.start();
        tablistManager.start();

        if (!placeholderBridge.isAvailable()) {
            getLogger().warning("PlaceholderAPI isn't installed/enabled - any %placeholder% text in config.yml will show up unresolved until it's added.");
        }
        if (!honorBridge.isAvailable()) {
            getLogger().warning("WSMP-Duels isn't installed/enabled - {rank} will show blank until it's added.");
        }
        // NEW: only nag about Floodgate if the Bedrock section is actually turned on.
        if (getConfig().getBoolean("scoreboard-bedrock.enabled", false) && !floodgateBridge.isAvailable()) {
            getLogger().warning("scoreboard-bedrock.enabled is true but Floodgate isn't installed/enabled - Bedrock players will just see the normal Java scoreboard until it's added.");
        }

        PluginCommand command = getCommand("tabscoreboard");
        if (command != null) {
            command.setExecutor(new TabScoreboardCommand(this));
        }

        getLogger().info("WSMP-TabScoreboard enabled.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scoreboardManager.refreshOne(event.getPlayer());
        tablistManager.refreshAll();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        scoreboardManager.forget(event.getPlayer());
    }

    public PlaceholderBridge getPlaceholderBridge() {
        return placeholderBridge;
    }

    public HonorBridge getHonorBridge() {
        return honorBridge;
    }

    public FloodgateBridge getFloodgateBridge() { // NEW
        return floodgateBridge;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public TablistManager getTablistManager() {
        return tablistManager;
    }
}
