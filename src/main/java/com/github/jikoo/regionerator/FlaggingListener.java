package com.github.jikoo.regionerator;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;

public class FlaggingListener implements Listener {
    private final Regionerator plugin;

    public FlaggingListener(Regionerator plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        this.plugin.getFlagger().flagChunk(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ(), 0, this.plugin.getVisitFlag());
    }
}
