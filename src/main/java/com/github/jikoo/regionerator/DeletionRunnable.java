package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.event.RegioneratorChunkDeleteEvent;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.ListIterator;

public class DeletionRunnable extends BukkitRunnable {
    private static final String STATS_FORMAT = "%s - Checked %s/%s, deleted %s regions and %s chunks";
    private final Regionerator plugin;
    private final World world;
    private final File regionFileFolder;
    private final String[] regions;
    private final int chunksPerCheck;
    private final ArrayList<Pair<Integer, Integer>> regionChunks = new ArrayList();
    private int count = -1;
    private int regionChunkX;
    private int regionChunkZ;
    private int dX = 0;
    private int dZ = 0;
    private int regionsDeleted = 0;
    private int chunksDeleted = 0;
    private long nextRun = Long.MAX_VALUE;

    public DeletionRunnable(Regionerator plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        File folder = new File(world.getWorldFolder(), "region");
        if (!folder.exists()) {
            folder = new File(world.getWorldFolder(), "DIM-1/region");
            if (!folder.exists()) {
                folder = new File(world.getWorldFolder(), "DIM1/region");
                if (!folder.exists()) {
                    throw new RuntimeException("World " + world.getName() + " has no generated terrain!");
                }
            }
        }
        this.regionFileFolder = folder;
        this.regions = this.regionFileFolder.list(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.matches("r\\.-?\\d+\\.-?\\d+\\.mca");
            }
        });
        this.chunksPerCheck = plugin.getChunksPerDeletionCheck();
        this.handleRegionCompletion();
    }

    public void run() {
        if (this.count >= this.regions.length) {
            this.plugin.getLogger().info("Regeneration cycle complete for " + this.getRunStats());
            this.nextRun = System.currentTimeMillis() + this.plugin.getMillisecondsBetweenDeletionCycles();
            this.cancel();
        } else if (!this.plugin.isPaused() || this.dX != 0 || this.dZ != 0) {
            for (int i = 0; i < this.chunksPerCheck && this.count < this.regions.length; ++i) {
                if (this.chunksPerCheck <= 1024 && i > 0 && this.dZ >= 32) {
                    return;
                }

                this.checkNextChunk();
                if (this.chunksPerCheck <= 1024 && this.dX == 0 && this.dZ == 0) {
                    return;
                }
            }

        }
    }

    private void checkNextChunk() {
        if (!this.plugin.isPaused() || this.dX != 0 || this.dZ != 0) {
            if (this.count < this.regions.length) {
                if (this.dX >= 32) {
                    this.dX = 0;
                    ++this.dZ;
                }

                if (this.dZ >= 32) {
                    this.handleRegionCompletion();
                    if (this.plugin.isPaused() || this.chunksPerCheck <= 1024) {
                        return;
                    }
                }

                int chunkX = this.regionChunkX + this.dX;
                int chunkZ = this.regionChunkZ + this.dZ;
                VisitStatus status = this.plugin.getFlagger().getChunkVisitStatus(this.world, chunkX, chunkZ);
                if (status.ordinal() < VisitStatus.GENERATED.ordinal() || status == VisitStatus.GENERATED) {
                    this.regionChunks.add(new ImmutablePair(chunkX, chunkZ));
                }

                ++this.dX;
            }
        }
    }

    private void handleRegionCompletion() {
        ListIterator iterator = this.regionChunks.listIterator();
        Pair regionChunkCoordinates;
        while (iterator.hasNext()) {
            regionChunkCoordinates = (Pair) iterator.next();
            if (this.world.isChunkLoaded((Integer) regionChunkCoordinates.getLeft(), (Integer) regionChunkCoordinates.getRight())) {
                iterator.remove();
            }
        }
        File regionFile;
        String regionFileName;
        if (this.regionChunks.size() == 1024) {
            regionFileName = this.regions[this.count];
            regionFile = new File(this.regionFileFolder, regionFileName);
            if (regionFile.exists() && regionFile.delete()) {
                ++this.regionsDeleted;
                if (this.plugin.debug(DebugLevel.MEDIUM)) {
                    this.plugin.debug(regionFileName + " deleted from " + this.world.getName());
                }
                this.plugin.getFlagger().unflagRegion(this.world.getName(), this.regionChunkX, this.regionChunkZ);
            } else if (this.plugin.debug(DebugLevel.MEDIUM)) {
                this.plugin.debug(String.format("Unable to delete %s from %s", regionFileName, this.world.getName()));
            }
            while (iterator.hasPrevious()) {
                Pair<Integer, Integer> chunkCoords = (Pair) iterator.previous();
                this.plugin.getServer().getPluginManager().callEvent(new RegioneratorChunkDeleteEvent(this.world, (Integer) chunkCoords.getLeft(), (Integer) chunkCoords.getRight()));
            }
        } else if (this.regionChunks.size() > 0) {
            regionFileName = this.regions[this.count];
            regionFile = new File(this.regionFileFolder, regionFileName);
            if (!regionFile.canWrite() && !regionFile.setWritable(true) && !regionFile.canWrite()) {
                if (this.plugin.debug(DebugLevel.MEDIUM)) {
                    this.plugin.debug(String.format("Unable to set %s in %s writable to delete %s chunks", regionFileName, this.world.getName(), this.regionChunks.size()));
                }
                return;
            }
            try {
                Throwable var23 = null;

                RandomAccessFile regionRandomAccess = new RandomAccessFile(regionFile, "rwd");
                try {
                    byte[] pointers = new byte[4096];
                    regionRandomAccess.read(pointers);
                    int chunkCount = 0;

                    while (iterator.hasPrevious()) {
                        Pair<Integer, Integer> chunkCoords = (Pair) iterator.previous();
                        this.plugin.getFlagger().unflagChunk(this.world.getName(), (Integer) chunkCoords.getLeft(), (Integer) chunkCoords.getRight());
                        int pointer = 4 * ((Integer) chunkCoords.getLeft() - this.regionChunkX + ((Integer) chunkCoords.getRight() - this.regionChunkZ) * 32);
                        boolean orphaned = true;
                        for (int i = pointer; i < pointer + 4; ++i) {
                            if (pointers[i] != 0) {
                                pointers[i] = 0;
                                orphaned = false;
                            }
                        }
                        if (!orphaned) {
                            this.plugin.getServer().getPluginManager().callEvent(new RegioneratorChunkDeleteEvent(this.world, (Integer) chunkCoords.getLeft(), (Integer) chunkCoords.getRight()));
                            if (this.plugin.debug(DebugLevel.HIGH)) {
                                this.plugin.debug(String.format("Wiping chunk %s, %s from %s in %s of %s", chunkCoords.getLeft(), chunkCoords.getRight(), pointer, regionFileName, this.world.getName()));
                            }
                            ++chunkCount;
                        }
                    }
                    regionRandomAccess.write(pointers, 0, 4096);
                    regionRandomAccess.close();
                    this.chunksDeleted += chunkCount;
                    if (this.plugin.debug(DebugLevel.MEDIUM)) {
                        this.plugin.debug(String.format("%s chunks deleted from %s of %s", chunkCount, regionFileName, this.world.getName()));
                    }
                } finally {
                    if (regionRandomAccess != null) {
                        regionRandomAccess.close();
                    }
                }
            } catch (IOException var21) {
                if (this.plugin.debug(DebugLevel.MEDIUM)) {
                    this.plugin.debug(String.format("Caught an IOException writing %s in %s to delete %s chunks", regionFileName, this.world.getName(), this.regionChunks.size()));
                }
            }
        }

        this.regionChunks.clear();
        ++this.count;
        if (this.plugin.debug(DebugLevel.LOW) && this.count % 20 == 0 && this.count > 0) {
            this.plugin.debug(this.getRunStats());
        }

        if (this.count < this.regions.length) {
            this.dX = 0;
            this.dZ = 0;
            this.regionChunks.clear();
            regionChunkCoordinates = this.parseRegion(this.regions[this.count]);
            this.regionChunkX = (Integer) regionChunkCoordinates.getLeft();
            this.regionChunkZ = (Integer) regionChunkCoordinates.getRight();
            if (this.plugin.debug(DebugLevel.HIGH)) {
                this.plugin.debug(String.format("Checking %s:%s (%s/%s)", this.world.getName(), this.regions[this.count], this.count, this.regions.length));
            }

        }
    }

    public String getRunStats() {
        return String.format("%s - Checked %s/%s, deleted %s regions and %s chunks", this.world.getName(), this.count, this.regions.length, this.regionsDeleted, this.chunksDeleted);
    }

    public long getNextRun() {
        return this.nextRun;
    }

    private Pair<Integer, Integer> parseRegion(String regionFile) {
        String[] split = regionFile.split("\\.");
        return new ImmutablePair(Integer.parseInt(split[1]) << 5, Integer.parseInt(split[2]) << 5);
    }
}
