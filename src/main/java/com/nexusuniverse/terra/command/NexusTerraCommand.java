/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  java.lang.Double
 *  java.lang.Integer
 *  java.lang.Math
 *  java.lang.NumberFormatException
 *  java.lang.Object
 *  java.lang.String
 *  java.lang.System
 *  java.util.concurrent.atomic.AtomicLong
 *  java.util.logging.Level
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  org.bukkit.World
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.nexusuniverse.terra.command;

import com.nexusuniverse.terra.TerraConfig;
import com.nexusuniverse.terra.generation.BlockPlacementTask;
import com.nexusuniverse.terra.generation.ExpansionManager;
import com.nexusuniverse.terra.generation.GenerationResult;
import com.nexusuniverse.terra.generation.TerrainGenerator;
import java.lang.Double;
import java.lang.Integer;
import java.lang.Math;
import java.lang.NumberFormatException;
import java.lang.Object;
import java.lang.String;
import java.lang.System;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class NexusTerraCommand
implements CommandExecutor {
    private final JavaPlugin plugin;
    private final TerrainGenerator terrainGenerator;
    private final ExpansionManager expansionManager;

    public NexusTerraCommand(JavaPlugin plugin, TerrainGenerator terrainGenerator, ExpansionManager expansionManager) {
        this.plugin = plugin;
        this.terrainGenerator = terrainGenerator;
        this.expansionManager = expansionManager;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        double lon;
        double lat;
        if (!(sender instanceof Player)) {
            sender.sendMessage((Component)Component.text((String)"This command must be run in-game.", (TextColor)NamedTextColor.RED));
            return true;
        }
        Player player = (Player)sender;
        this.plugin.reloadConfig();
        TerraConfig config = TerraConfig.from(this.plugin.getConfig());

        if (args.length >= 1 && args[0].equalsIgnoreCase("stop")) {
            boolean stopped = this.expansionManager.stop(player);
            player.sendMessage((Component)Component.text((String)(stopped ? "NexusTerra: expansion stopped." : "NexusTerra: no expansion is currently running."), (TextColor)(stopped ? NamedTextColor.AQUA : NamedTextColor.YELLOW)));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("expand")) {
            return this.handleExpand(player, config, args);
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("generate")) {
            sender.sendMessage((Component)Component.text((String)"Usage: /nexusterra generate <lat> <lon> [radiusMeters]  |  /nexusterra expand <lat> <lon> [tileRadiusMeters]  |  /nexusterra stop", (TextColor)NamedTextColor.YELLOW));
            return true;
        }
        int radius = config.defaultRadiusMetres();
        try {
            lat = Double.parseDouble((String)args[1]);
            lon = Double.parseDouble((String)args[2]);
            if (args.length >= 4) {
                radius = Integer.parseInt((String)args[3]);
            }
        }
        catch (NumberFormatException e) {
            sender.sendMessage((Component)Component.text((String)"Usage: /nexusterra generate <lat> <lon> [radiusMeters]", (TextColor)NamedTextColor.YELLOW));
            return true;
        }
        if (!Double.isFinite((double)lat) || !Double.isFinite((double)lon) || lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            sender.sendMessage((Component)Component.text((String)"Invalid coordinates. Latitude must be -90 to 90 and longitude must be -180 to 180.", (TextColor)NamedTextColor.RED));
            return true;
        }
        if (radius < 10) {
            sender.sendMessage((Component)Component.text((String)"Radius must be at least 10 metres.", (TextColor)NamedTextColor.RED));
            return true;
        }
        if (radius > config.maxRadiusMetres()) {
            sender.sendMessage((Component)Component.text((String)("Radius capped at " + config.maxRadiusMetres() + "m by config (generation.max-radius-metres). Block count grows with the square of the radius, and the free public data sources aren't built for larger single requests."), (TextColor)NamedTextColor.RED));
            return true;
        }
        World targetWorld = player.getWorld();
        int worldBaseX = player.getLocation().getBlockX();
        int worldBaseY = player.getLocation().getBlockY();
        int worldBaseZ = player.getLocation().getBlockZ();
        String starterName = player.getName();
        broadcast((Component)Component.text((String)(starterName + " started a NexusTerra generation at (" + lat + ", " + lon + "), radius " + radius + "m -- fetching real-world data, this may take a moment."), (TextColor)NamedTextColor.AQUA));
        int totalSamplePoints = this.estimateSamplePointCount(radius, TerrainGenerator.effectiveElevationStep(radius, config.elevationSampleStep()));
        AtomicLong lastUpdate = new AtomicLong(0L);
        this.terrainGenerator.generate(lat, lon, radius, worldBaseY, targetWorld.getMaxHeight(), config, resolved -> {
            long now = System.currentTimeMillis();
            if (now - lastUpdate.get() < 900L) {
                return;
            }
            lastUpdate.set(now);
            int percent = totalSamplePoints > 0 ? Math.min((int)100, (int)(resolved * 100 / totalSamplePoints)) : 0;
            this.plugin.getServer().getScheduler().runTask((Plugin)this.plugin, () -> broadcastActionBar((Component)Component.text((String)("NexusTerra (" + starterName + "): fetching elevation data... " + percent + "%"), (TextColor)NamedTextColor.AQUA)));
        }).thenAccept(result -> this.plugin.getServer().getScheduler().runTask((Plugin)this.plugin, () -> {
            // Deliberately does NOT check player.isOnline() here -- the build proceeds regardless
            // of whether the person who ran the command is still connected. It used to bail out
            // entirely at this point if they'd disconnected, silently abandoning the whole
            // generation after the (often slow) data fetch had already completed.
            broadcast((Component)Component.text((String)("NexusTerra (" + starterName + "): data received -- clearing the site, then placing " + result.placements().size() + " block(s)."), (TextColor)NamedTextColor.GREEN));
            new BlockPlacementTask(targetWorld, worldBaseX, worldBaseZ, (GenerationResult)((Object)result), this::broadcast, config.placementsPerTick(), config.clearHeight(), config.clearChecksPerTick()).runTaskTimer((Plugin)this.plugin, 0L, 1L);
        })).exceptionally(ex -> {
            this.plugin.getLogger().log(Level.SEVERE, "[NexusTerra] Generation failed", ex);
            this.plugin.getServer().getScheduler().runTask((Plugin)this.plugin, () -> broadcast((Component)Component.text((String)("NexusTerra (" + starterName + "): generation failed -- check console for details."), (TextColor)NamedTextColor.RED)));
            return null;
        });
        return true;
    }

    private boolean handleExpand(Player player, TerraConfig config, String[] args) {
        double lat;
        double lon;
        if (args.length < 3) {
            player.sendMessage((Component)Component.text((String)"Usage: /nexusterra expand <lat> <lon> [tileRadiusMeters]", (TextColor)NamedTextColor.YELLOW));
            return true;
        }
        int tileRadius = config.expansionDefaultTileRadiusMetres();
        try {
            lat = Double.parseDouble(args[1]);
            lon = Double.parseDouble(args[2]);
            if (args.length >= 4) {
                tileRadius = Integer.parseInt(args[3]);
            }
        } catch (NumberFormatException e) {
            player.sendMessage((Component)Component.text((String)"Usage: /nexusterra expand <lat> <lon> [tileRadiusMeters]", (TextColor)NamedTextColor.YELLOW));
            return true;
        }
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            player.sendMessage((Component)Component.text((String)"Invalid coordinates. Latitude must be -90 to 90 and longitude must be -180 to 180.", (TextColor)NamedTextColor.RED));
            return true;
        }
        if (tileRadius < 10) {
            player.sendMessage((Component)Component.text((String)"Tile radius must be at least 10 metres.", (TextColor)NamedTextColor.RED));
            return true;
        }
        if (tileRadius > config.maxRadiusMetres()) {
            player.sendMessage((Component)Component.text((String)("Tile radius capped at " + config.maxRadiusMetres() + "m by config (generation.max-radius-metres) -- each tile in the expansion still goes through the same single-tile pipeline and limit as /nexusterra generate."), (TextColor)NamedTextColor.RED));
            return true;
        }
        if (this.expansionManager.isExpanding(player)) {
            player.sendMessage((Component)Component.text((String)"An expansion is already running. Use /nexusterra stop first.", (TextColor)NamedTextColor.YELLOW));
            return true;
        }
        World targetWorld = player.getWorld();
        int worldBaseX = player.getLocation().getBlockX();
        int worldBaseY = player.getLocation().getBlockY();
        int worldBaseZ = player.getLocation().getBlockZ();
        this.expansionManager.start(player, lat, lon, tileRadius, worldBaseY, worldBaseX, worldBaseZ, targetWorld, config);
        return true;
    }

    private void broadcast(Component text) {
        for (Player p : this.plugin.getServer().getOnlinePlayers()) {
            p.sendMessage(text);
        }
    }

    private void broadcastActionBar(Component text) {
        for (Player p : this.plugin.getServer().getOnlinePlayers()) {
            p.sendActionBar(text);
        }
    }

    private int estimateSamplePointCount(int radiusBlocks, int step) {
        int gridExtent = (radiusBlocks + step - 1) / step * step;
        int pointsPerAxis = 2 * gridExtent / step + 1;
        return pointsPerAxis * pointsPerAxis;
    }
}
