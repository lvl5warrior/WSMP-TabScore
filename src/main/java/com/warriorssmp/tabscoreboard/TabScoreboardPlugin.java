package com.warriorssmp.tabscoreboard;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class TabScoreboardPlugin extends JavaPlugin implements Listener {

    private PlaceholderBridge placeholderBridge;
    private HonorBridge honorBridge;
    private TextResolver textResolver;
    private ScoreboardManager scoreboardManager;
    private TablistManager tablistManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        placeholderBridge = new PlaceholderBridge(this);
        honorBridge = new HonorBridge(this);
        textResolver = new TextResolver(this, placeholderBridge, honorBridge);
        scoreboardManager = new ScoreboardManager(this, textResolver);
        tablistManager = new TablistManager(this, textResolver);

        getServer().getPluginManager().registerEvents(this, this);

        scoreboardManager.start();
        tablistManager.start();

        if (!placeholderBridge.isAvailable()) {
            getLogger().warning("PlaceholderAPI isn't installed/enabled - any %placeholder% text in "
                    + "config.yml will show up unresolved until it's added.");
        }
        if (!honorBridge.isAvailable()) {
            getLogger().warning("WSMP-Duels isn't installed/enabled - {rank} will show blank until it's added.");
        }

        var command = getCommand("tabscoreboard");
        if (command != null) {
            command.setExecutor(new TabScoreboardCommand(this));
        }

        getLogger().info("WSMP-TabScoreboard enabled.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Immediate refresh rather than waiting for the next periodic
        // tick, so a joining player (and everyone already online, whose
        // tab list now needs this new name added) doesn't see a blank or
        // stale sidebar/tablist for up to a second.
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

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public TablistManager getTablistManager() {
        return tablistManager;
    }
}
