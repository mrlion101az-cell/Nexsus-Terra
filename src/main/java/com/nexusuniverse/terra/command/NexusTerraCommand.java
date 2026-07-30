package com.nexusuniverse.terra.command;

import com.nexusuniverse.terra.TerraConfig;
import com.nexusuniverse.terra.generation.BlockPlacementTask;
import com.nexusuniverse.terra.generation.TerrainGenerator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class NexusTerraCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final TerrainGenerator terrainGenerator;

    public NexusTerraCommand(JavaPlugin plugin, TerrainGenerator terrainGenerator) {
        this.plugin = plugin;
        this.terrainGenerator = terrainGenerator;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command must be run in-game.", NamedTextColor.RED));
            return true;
        }

        // Read config fresh each run so retuning doesn't need a restart.
        plugin.reloadConfig();
        TerraConfig config = TerraConfig.from(plugin.getConfig());

        if (args.length < 3 || !args[0].equalsIgnoreCase("generate")) {
            sender.sendMessage(Component.text("Usage: /nexusterra generate <lat> <lon> [radiusMeters]", NamedTextColor.YELLOW));
            return true;
        }

        double lat, lon;
        int radius = config.defaultRadiusMetres();
        try {
            lat = Double.parseDouble(args[1]);
            lon = Double.parseDouble(args[2]);
            if (args.length >= 4) {
                radius = Integer.parseInt(args[3]);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Usage: /nexusterra generate <lat> <lon> [radiusMeters]", NamedTextColor.YELLOW));
            return true;
        }

        if (!Double.isFinite(lat) || !Double.isFinite(lon)
                || lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            sender.sendMessage(Component.text(
                    "Invalid coordinates. Latitude must be -90 to 90 and longitude must be -180 to 180.",
                    NamedTextColor.RED));
            return true;
        }

        if (radius < 10) {
            sender.sendMessage(Component.text("Radius must be at least 10 metres.", NamedTextColor.RED));
            return true;
        }

        if (radius > config.maxRadiusMetres()) {
            sender.sendMessage(Component.text(
                    "Radius capped at " + config.maxRadiusMetres() + "m by config (generation.max-radius-metres). "
                            + "Block count grows with the square of the radius, and the free public data sources "
                            + "aren't built for larger single requests.",
                    NamedTextColor.RED));
            return true;
        }

        org.bukkit.World targetWorld = player.getWorld();
        int worldBaseX = player.getLocation().getBlockX();
        int worldBaseY = player.getLocation().getBlockY();
        int worldBaseZ = player.getLocation().getBlockZ();

        player.sendMessage(Component.text(
                "Fetching real-world data for (" + lat + ", " + lon + "), radius " + radius + "m... this may take a moment.",
                NamedTextColor.AQUA));

        int totalSamplePoints = estimateSamplePointCount(radius, config.elevationSampleStep());
        java.util.concurrent.atomic.AtomicLong lastUpdate = new java.util.concurrent.atomic.AtomicLong(0);

        terrainGenerator.generate(lat, lon, radius, worldBaseY, targetWorld.getMaxHeight(), config, resolved -> {
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate.get() < 900) return;
                    lastUpdate.set(now);
                    int percent = totalSamplePoints > 0 ? Math.min(100, resolved * 100 / totalSamplePoints) : 0;
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.sendActionBar(Component.text(
                                    "Fetching elevation data... " + percent + "%", NamedTextColor.AQUA));
                        }
                    });
                })
                .thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        plugin.getLogger().warning("[NexusTerra] Generation completed, but the requesting player disconnected.");
                        return;
                    }
                    player.sendMessage(Component.text(
                            "Data received -- clearing the site, then placing " + result.placements().size() + " block(s).",
                            NamedTextColor.GREEN));
                    new BlockPlacementTask(targetWorld, worldBaseX, worldBaseZ, result, player,
                            config.placementsPerTick(), config.clearHeight(), config.clearChecksPerTick())
                            .runTaskTimer(plugin, 0L, 1L);
                }))
                .exceptionally(ex -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "[NexusTerra] Generation failed", ex);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.sendMessage(Component.text(
                                    "Generation failed -- check console for details.", NamedTextColor.RED));
                        }
                    });
                    return null;
                });

        return true;
    }

    private int estimateSamplePointCount(int radiusBlocks, int step) {
        int gridExtent = ((radiusBlocks + step - 1) / step) * step;
        int pointsPerAxis = (2 * gridExtent / step) + 1;
        return pointsPerAxis * pointsPerAxis;
    }
}
