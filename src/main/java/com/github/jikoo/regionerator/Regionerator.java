package com.github.jikoo.regionerator;

import com.google.common.collect.ImmutableList;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

public class Regionerator extends JavaPlugin {
    private long flagDuration;
    private long ticksPerFlag;
    private long ticksPerFlagAutosave;
    private List<String> worlds;
    private ChunkFlagger chunkFlagger;
    private HashMap<String, DeletionRunnable> deletionRunnables;
    private long millisBetweenCycles;
    private DebugLevel debugLevel;
    private boolean paused;

    public void onEnable() {
        this.saveDefaultConfig();
        this.paused = false;
        List<String> worldList = this.getConfig().getStringList("worlds");
        if (worldList.isEmpty()) {
            this.getLogger().severe("No worlds are enabled. Disabling!");
            this.getServer().getPluginManager().disablePlugin(this);
        } else {
            boolean dirtyConfig = false;

            try {
                this.debugLevel = DebugLevel.valueOf(this.getConfig().getString("debug-level", "OFF").toUpperCase());
            } catch (IllegalArgumentException var9) {
                this.debugLevel = DebugLevel.OFF;
                this.getConfig().set("debug-level", "OFF");
                dirtyConfig = true;
            }

            if (this.debug(DebugLevel.LOW)) {
                this.debug("Debug level: " + this.debugLevel.name());
            }

            this.worlds = new ArrayList();
            Iterator var4 = Bukkit.getWorlds().iterator();

            while (true) {
                while (var4.hasNext()) {
                    World world = (World) var4.next();
                    if (worldList.contains(world.getName())) {
                        this.worlds.add(world.getName());
                    } else {
                        Iterator var6 = worldList.iterator();

                        while (var6.hasNext()) {
                            String name = (String) var6.next();
                            if (world.getName().equalsIgnoreCase(name)) {
                                this.worlds.add(world.getName());
                                dirtyConfig = true;
                                break;
                            }
                        }
                    }
                }

                this.worlds = ImmutableList.copyOf(this.worlds);
                if (dirtyConfig) {
                    this.getConfig().set("worlds", this.worlds);
                }

                if (this.getConfig().getInt("days-till-flag-expires") < 1) {
                    this.getConfig().set("days-till-flag-expires", 1);
                    dirtyConfig = true;
                }

                this.flagDuration = 86400000L * (long) this.getConfig().getInt("days-till-flag-expires");
                var4 = this.worlds.iterator();

                String pluginName;
                while (var4.hasNext()) {
                    pluginName = (String) var4.next();
                    if (this.getConfig().getLong("delete-this-to-reset-plugin." + pluginName, 0L) == 0L) {
                        this.getConfig().set("delete-this-to-reset-plugin." + pluginName, System.currentTimeMillis() + this.flagDuration);
                        dirtyConfig = true;
                    }
                }

                if (this.getConfig().getInt("chunk-flag-radius") < 0) {
                    this.getConfig().set("chunk-flag-radius", 4);
                    dirtyConfig = true;
                }

                if (this.getConfig().getInt("seconds-per-flag") < 1) {
                    this.getConfig().set("seconds-per-flag", 10);
                    dirtyConfig = true;
                }

                this.ticksPerFlag = (long) this.getConfig().getInt("seconds-per-flag") * 20L;
                if (this.getConfig().getInt("minutes-per-flag-autosave") < 1) {
                    this.getConfig().set("minutes-per-flag-autosave", 5);
                    dirtyConfig = true;
                }

                this.ticksPerFlagAutosave = (long) this.getConfig().getInt("minutes-per-flag-autosave") * 1200L;
                if (this.getConfig().getLong("ticks-per-deletion") < 1L) {
                    this.getConfig().set("ticks-per-deletion", 20L);
                    dirtyConfig = true;
                }

                if (this.getConfig().getInt("chunks-per-deletion") < 1) {
                    this.getConfig().set("chunks-per-deletion", 20);
                    dirtyConfig = true;
                }

                if (this.getConfig().getInt("hours-between-cycles") < 0) {
                    this.getConfig().set("hours-between-cycles", 0);
                    dirtyConfig = true;
                }
                this.millisBetweenCycles = (long) this.getConfig().getInt("hours-between-cycles") * 3600000L;
                if (dirtyConfig) {
                    this.getConfig().options().copyHeader(true);
                    this.saveConfig();
                }
                this.chunkFlagger = new ChunkFlagger(this);
                this.chunkFlagger.scheduleSaving();
                this.deletionRunnables = new HashMap();
                (new FlaggingRunnable(this)).runTaskTimer(this, 0L, this.getTicksPerFlag());
                this.getServer().getPluginManager().registerEvents(new FlaggingListener(this), this);
                if (this.debug(DebugLevel.LOW)) {
                    this.onCommand(Bukkit.getConsoleSender(), (Command) null, (String) null, new String[0]);
                }
                return;
            }
        }
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        this.attemptDeletionActivation();
        if (args.length > 0) {
            args[0] = args[0].toLowerCase();
            if (args[0].equals("reload")) {
                this.reloadConfig();
                this.onDisable();
                this.onEnable();
                sender.sendMessage("Regionerator configuration reloaded, all tasks restarted!");
                return true;
            } else if (!args[0].equals("pause") && !args[0].equals("stop")) {
                if (!args[0].equals("resume") && !args[0].equals("unpause") && !args[0].equals("start")) {
                    return false;
                } else {
                    this.paused = false;
                    sender.sendMessage("Resumed Regionerator. Use /regionerator pause to pause.");
                    return true;
                }
            } else {
                this.paused = true;
                sender.sendMessage("Paused Regionerator. Use /regionerator resume to resume.");
                return true;
            }
        } else {
            SimpleDateFormat format = new SimpleDateFormat("HH:mm 'on' dd/MM");
            boolean running = false;
            Iterator var8 = this.worlds.iterator();

            while (true) {
                while (var8.hasNext()) {
                    String worldName = (String) var8.next();
                    long activeAt = this.getConfig().getLong("delete-this-to-reset-plugin." + worldName);
                    if (activeAt > System.currentTimeMillis()) {
                        sender.sendMessage(worldName + " - Gathering data, regeneration starts " + format.format(new Date(activeAt)));
                    } else if (this.deletionRunnables.containsKey(worldName)) {
                        DeletionRunnable runnable = (DeletionRunnable) this.deletionRunnables.get(worldName);
                        sender.sendMessage(runnable.getRunStats());
                        if (runnable.getNextRun() < Long.MAX_VALUE) {
                            sender.sendMessage("Cycle is finished. Next run scheduled for " + format.format(runnable.getNextRun()));
                        } else if (!this.getConfig().getBoolean("allow-concurrent-cycles")) {
                            running = true;
                        }
                    } else if (running && !this.getConfig().getBoolean("allow-concurrent-cycles")) {
                        sender.sendMessage("Cycle for " + worldName + " is ready to start.");
                    } else if (!running) {
                        this.getLogger().severe("Deletion cycle failed to start for " + worldName + "! Please report this issue if you see any errors!");
                    }
                }

                return true;
            }
        }
    }

    public void onDisable() {
        this.getServer().getScheduler().cancelTasks(this);
        if (this.chunkFlagger != null) {
            this.chunkFlagger.save();
        }
    }

    public long getVisitFlag() {
        return this.flagDuration + System.currentTimeMillis();
    }

    public int getChunkFlagRadius() {
        return this.getConfig().getInt("chunk-flag-radius");
    }

    public long getTicksPerFlag() {
        return this.ticksPerFlag;
    }

    public long getTicksPerFlagAutosave() {
        return this.ticksPerFlagAutosave;
    }

    public int getChunksPerDeletionCheck() {
        return this.getConfig().getInt("chunks-per-deletion");
    }

    public long getTicksPerDeletionCheck() {
        return this.getConfig().getLong("ticks-per-deletion");
    }

    public long getMillisecondsBetweenDeletionCycles() {
        return this.millisBetweenCycles;
    }

    public void attemptDeletionActivation() {
        Iterator iterator = this.deletionRunnables.entrySet().iterator();

        while (iterator.hasNext()) {
            if (((DeletionRunnable) ((Entry) iterator.next()).getValue()).getNextRun() < System.currentTimeMillis()) {
                iterator.remove();
            }
        }

        if (!this.isPaused()) {
            Iterator var3 = this.worlds.iterator();

            String worldName;
            do {
                while (true) {
                    do {
                        if (!var3.hasNext()) {
                            return;
                        }

                        worldName = (String) var3.next();
                    } while (this.getConfig().getLong("delete-this-to-reset-plugin." + worldName) > System.currentTimeMillis());

                    if (this.deletionRunnables.containsKey(worldName)) {
                        break;
                    }

                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        DeletionRunnable runnable;
                        try {
                            runnable = new DeletionRunnable(this, world);
                        } catch (RuntimeException var7) {
                            if (this.debug(DebugLevel.HIGH)) {
                                this.debug(var7.getMessage());
                            }
                            continue;
                        }

                        runnable.runTaskTimer(this, 0L, this.getTicksPerDeletionCheck());
                        this.deletionRunnables.put(worldName, runnable);
                        if (this.debug(DebugLevel.LOW)) {
                            this.debug("Deletion run scheduled for " + world.getName());
                        }

                        if (!this.getConfig().getBoolean("allow-concurrent-cycles")) {
                            return;
                        }
                    }
                }
            } while (this.getConfig().getBoolean("allow-concurrent-cycles") || ((DeletionRunnable) this.deletionRunnables.get(worldName)).getNextRun() != Long.MAX_VALUE);

        }
    }

    public List<String> getActiveWorlds() {
        return this.worlds;
    }

    public ChunkFlagger getFlagger() {
        return this.chunkFlagger;
    }

    public boolean isPaused() {
        return this.paused;
    }

    public boolean debug(DebugLevel level) {
        return this.debugLevel.ordinal() >= level.ordinal();
    }

    public void debug(String message) {
        this.getLogger().info(message);
    }
}
