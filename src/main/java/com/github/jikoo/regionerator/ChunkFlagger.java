package com.github.jikoo.regionerator;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChunkFlagger {
    private final Regionerator plugin;
    private final File flagsFile;
    private final YamlConfiguration flags;
    private final AtomicBoolean dirty;
    private final AtomicBoolean saving;
    private final Set<String> pendingFlag;
    private final Set<String> pendingUnflag;

    public ChunkFlagger(Regionerator plugin) {
        this.plugin = plugin;
        this.flagsFile = new File(plugin.getDataFolder(), "flags.yml");
        if (this.flagsFile.exists()) {
            this.flags = YamlConfiguration.loadConfiguration(this.flagsFile);
        } else {
            this.flags = new YamlConfiguration();
        }

        this.dirty = new AtomicBoolean(false);
        this.saving = new AtomicBoolean(false);
        this.pendingFlag = new HashSet();
        this.pendingUnflag = new HashSet();
    }

    public void flagChunk(String world, int chunkX, int chunkZ) {
        this.flagChunk(world, chunkX, chunkZ, this.plugin.getChunkFlagRadius(), this.plugin.getVisitFlag());
    }

    public void flagChunk(String world, int chunkX, int chunkZ, int radius, long flagTil) {
        for (int dX = -radius; dX <= radius; ++dX) {
            for (int dZ = -radius; dZ <= radius; ++dZ) {
                this.flag(this.getChunkString(world, chunkX + dX, chunkZ + dZ), flagTil, false);
            }
        }

    }

    private void flag(String chunkPath, boolean force) {
        this.flag(chunkPath, this.plugin.getVisitFlag(), force);
    }

    private void flag(String chunkPath, long flagTil, boolean force) {
        if (!force && this.saving.get()) {
            this.pendingFlag.add(chunkPath);
        } else {
            this.flags.set(chunkPath, flagTil);
            this.dirty.set(true);
        }

    }

    public void unflagRegion(String world, int regionX, int regionZ) {
        regionX <<= 5;
        regionZ <<= 5;

        for (int chunkX = regionX; chunkX < regionX + 32; ++chunkX) {
            for (int chunkZ = regionZ; chunkZ < regionZ + 32; ++chunkZ) {
                this.unflagChunk(world, chunkX, chunkZ);
            }
        }

    }

    public void unflagChunk(String world, int chunkX, int chunkZ) {
        this.unflag(this.getChunkString(world, chunkX, chunkZ), false);
    }

    private void unflag(String chunkPath, boolean force) {
        if (!force && this.saving.get()) {
            this.pendingUnflag.add(chunkPath);
        } else {
            this.flags.set(chunkPath, (Object) null);
            this.dirty.set(true);
        }

    }

    public void scheduleSaving() {
        (new BukkitRunnable() {
            public void run() {
                ChunkFlagger.this.saving.set(true);
                ChunkFlagger.this.save();
                ChunkFlagger.this.saving.set(false);
                (new BukkitRunnable() {
                    public void run() {
                        Iterator var2 = ChunkFlagger.this.pendingFlag.iterator();

                        String path;
                        while (var2.hasNext()) {
                            path = (String) var2.next();
                            ChunkFlagger.this.flag(path, true);
                        }

                        ChunkFlagger.this.pendingFlag.clear();
                        var2 = ChunkFlagger.this.pendingUnflag.iterator();

                        while (var2.hasNext()) {
                            path = (String) var2.next();
                            ChunkFlagger.this.unflag(path, true);
                        }

                        ChunkFlagger.this.pendingUnflag.clear();
                    }
                }).runTask(ChunkFlagger.this.plugin);
            }
        }).runTaskTimerAsynchronously(this.plugin, this.plugin.getTicksPerFlagAutosave(), this.plugin.getTicksPerFlagAutosave());
    }

    public void save() {
        if (this.dirty.get()) {
            if (!this.saving.get()) {
                Iterator var2 = this.pendingFlag.iterator();

                String path;
                while (var2.hasNext()) {
                    path = (String) var2.next();
                    this.flag(path, true);
                }

                this.pendingFlag.clear();
                var2 = this.pendingUnflag.iterator();

                while (var2.hasNext()) {
                    path = (String) var2.next();
                    this.unflag(path, true);
                }

                this.pendingUnflag.clear();
            }

            try {
                this.flags.save(this.flagsFile);
            } catch (IOException var3) {
                this.plugin.getLogger().severe("Could not save flags.yml!");
                var3.printStackTrace();
            }

        }
    }

    public VisitStatus getChunkVisitStatus(World world, int chunkX, int chunkZ) {
        String chunkString = this.getChunkString(world.getName(), chunkX, chunkZ);
        long visit = this.flags.getLong(chunkString, -1L);
        if (visit != Long.MAX_VALUE && visit > System.currentTimeMillis()) {
            if (this.plugin.debug(DebugLevel.HIGH)) {
                this.plugin.debug("Chunk " + chunkString + " is flagged.");
            }

            return VisitStatus.VISITED;
        } else {
            if (visit == Long.MAX_VALUE) {
                if (this.plugin.debug(DebugLevel.HIGH)) {
                    this.plugin.debug("Chunk " + chunkString + " has not been visited since it was generated.");
                }

                return VisitStatus.GENERATED;
            } else {
                return VisitStatus.UNKNOWN;
            }
        }
    }

    private String getChunkString(String world, int chunkX, int chunkZ) {
        return world + '.' + chunkX + '_' + chunkZ;
    }
}
