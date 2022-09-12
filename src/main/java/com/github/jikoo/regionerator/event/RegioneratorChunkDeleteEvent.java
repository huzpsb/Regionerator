package com.github.jikoo.regionerator.event;

import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class RegioneratorChunkDeleteEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final World world;
    private final int chunkX;
    private final int chunkZ;

    public RegioneratorChunkDeleteEvent(World world, int chunkX, int chunkZ) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public World getWorld() {
        return this.world;
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }

    public HandlerList getHandlers() {
        return handlers;
    }
}
