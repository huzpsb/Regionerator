package com.github.jikoo.regionerator;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Iterator;

public class FlaggingRunnable extends BukkitRunnable {
    private final Regionerator plugin;

    public FlaggingRunnable(Regionerator plugin) {
        this.plugin = plugin;
    }

    public void run() {
        Iterator var2 = Bukkit.getOnlinePlayers().iterator();

        while (var2.hasNext()) {
            Player player = (Player) var2.next();
            if (player.getGameMode() != GameMode.SPECTATOR && this.plugin.getActiveWorlds().contains(player.getWorld().getName())) {
                Chunk chunk = player.getLocation().getChunk();
                this.plugin.getFlagger().flagChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
            }
        }

        this.plugin.attemptDeletionActivation();
    }
}
