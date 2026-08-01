package com.nexusuniverse.terra.generation;

import com.nexusuniverse.terra.TerraConfig;
import com.nexusuniverse.terra.geo.GeoPoint;
import com.nexusuniverse.terra.geo.GeoProjection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Drives a continuous, sequential expansion outward from a single origin coordinate: a spiral of
 * tiles, each one generated with the existing (unchanged) single-tile TerrainGenerator pipeline,
 * one tile fully cleared-and-placed before the next tile's OSM/elevation fetch even starts. This
 * intentionally does NOT rewrite core generation -- it repeats the same request TerrainGenerator
 * has always served, just centred on a different lat/lon each time and offset to the correct
 * world-block position for that tile.
 *
 * This is deliberately NOT unbounded. Two independent caps apply:
 *  - expansion.max-radius-metres: the real dead-man switch, checked as an actual straight-line
 *    distance from the origin coordinate to each candidate tile's centre, not a tile count. This
 *    stays correct no matter what tileRadius (and therefore tile spacing) a run uses.
 *  - expansion.max-tiles: a hard backstop in case of a config mistake (e.g. a tiny tileRadius
 *    with a huge max-radius-metres would otherwise enumerate an enormous tile count).
 *
 * Only one expansion may run per player at a time. /nexusterra stop cancels it -- either between
 * tiles (during the pacing delay) or mid-tile (cancels the in-flight BlockPlacementTask, leaving
 * that tile partially built rather than trying to undo it).
 */
public class ExpansionManager {
    private final JavaPlugin plugin;
    private final TerrainGenerator terrainGenerator;
    private final Map<UUID, ExpansionSession> sessions = new ConcurrentHashMap<>();

    public ExpansionManager(JavaPlugin plugin, TerrainGenerator terrainGenerator) {
        this.plugin = plugin;
        this.terrainGenerator = terrainGenerator;
    }

    public boolean isExpanding(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void start(Player player, double originLat, double originLon, int tileRadius, int worldBaseY, int originBlockX, int originBlockZ, World world, TerraConfig config) {
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage(warn("An expansion is already running. Use /nexusterra stop first."));
            return;
        }
        List<int[]> tiles = buildSpiral(tileRadius, config.expansionMaxRadiusMetres(), config.expansionMaxTiles());
        ExpansionSession session = new ExpansionSession(player, originLat, originLon, tileRadius, worldBaseY, originBlockX, originBlockZ, world, config, tiles);
        sessions.put(player.getUniqueId(), session);
        player.sendMessage(info("NexusTerra: starting expansion from (" + originLat + ", " + originLon + "), " + tileRadius + "m tiles, up to " + tiles.size() + " tile(s) (dead-man cap " + config.expansionMaxRadiusMetres() + "m). Use /nexusterra stop to end it early."));
        advance(session);
    }

    public boolean stop(Player player) {
        ExpansionSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }
        session.cancelled = true;
        if (session.currentTask != null) {
            session.currentTask.cancel();
        }
        return true;
    }

    private void advance(ExpansionSession session) {
        if (session.cancelled) {
            return;
        }
        if (session.tileIndex >= session.tiles.size()) {
            sessions.remove(session.player.getUniqueId());
            if (session.player.isOnline()) {
                session.player.sendMessage(info("NexusTerra: expansion complete -- " + session.tileIndex + " tile(s) generated."));
            }
            return;
        }
        if (!session.player.isOnline()) {
            sessions.remove(session.player.getUniqueId());
            return;
        }

        int[] tileOffset = session.tiles.get(session.tileIndex);
        int step = session.tileRadius * 2;
        int offsetX = tileOffset[0] * step;
        int offsetZ = tileOffset[1] * step;
        double distanceFromOrigin = Math.sqrt((double) offsetX * offsetX + (double) offsetZ * offsetZ);

        GeoProjection projection = new GeoProjection(session.originLat, session.originLon, 1.0);
        GeoPoint tileCenter = projection.toLatLon(offsetX, offsetZ);
        int tileBaseX = session.originBlockX + offsetX;
        int tileBaseZ = session.originBlockZ + offsetZ;

        session.player.sendActionBar(Component.text("Expansion: tile " + (session.tileIndex + 1) + "/" + session.tiles.size() + " (~" + Math.round(distanceFromOrigin) + "m from origin) -- fetching data...", NamedTextColor.AQUA));

        terrainGenerator.generate(tileCenter.lat(), tileCenter.lon(), session.tileRadius, session.worldBaseY, session.world.getMaxHeight(), session.config, resolved -> {
        }, session.sharedBaseElevation).thenAccept(result -> plugin.getServer().getScheduler().runTask((Plugin) plugin, () -> {
            if (session.cancelled) {
                return;
            }
            if (!session.player.isOnline()) {
                sessions.remove(session.player.getUniqueId());
                return;
            }
            if (session.sharedBaseElevation == null) {
                // This was the origin tile (offset 0,0 -- always tileIndex 0 in the spiral). Its
                // real-world elevation becomes the shared "ground zero" every later tile in this
                // run is built relative to, instead of each tile independently zeroing itself to
                // its own centre -- see the comment on TerrainGenerator.generate's
                // baseElevationOverride param for why that was the actual cause of tiles sitting
                // at mismatched heights with gaps between them.
                session.sharedBaseElevation = result.baseElevation();
            }
            BlockPlacementTask task = new BlockPlacementTask(session.world, tileBaseX, tileBaseZ, result, session.player, session.config.placementsPerTick(), session.config.clearHeight(), session.config.clearChecksPerTick(), () -> onTileComplete(session));
            session.currentTask = task;
            task.runTaskTimer((Plugin) plugin, 0L, 1L);
        })).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "[NexusTerra] Expansion tile failed", ex);
            plugin.getServer().getScheduler().runTask((Plugin) plugin, () -> {
                if (session.player.isOnline()) {
                    session.player.sendMessage(warn("NexusTerra: a tile failed during expansion, stopping here (" + session.tileIndex + " tile(s) completed). Check console."));
                }
            });
            sessions.remove(session.player.getUniqueId());
            return null;
        });
    }

    private void onTileComplete(ExpansionSession session) {
        session.tileIndex++;
        session.currentTask = null;
        if (session.cancelled) {
            sessions.remove(session.player.getUniqueId());
            return;
        }
        long delayTicks = session.config.expansionPacingSeconds() * 20L;
        plugin.getServer().getScheduler().runTaskLater((Plugin) plugin, () -> advance(session), delayTicks);
    }

    /**
     * Enumerates tile offsets (in tile-grid units, not blocks) in a square spiral out from the
     * origin tile, ring by ring. A ring's minimum possible real-world distance is exactly
     * ring * step (its edge-centre tiles); that's monotonically increasing with ring, so once a
     * ring's minimum distance exceeds the cap, every further ring is guaranteed to as well and
     * enumeration stops there -- individual corner tiles of a ring can still be filtered out one
     * by one before that point since a ring's corners sit farther out than its edge centres.
     */
    private List<int[]> buildSpiral(int tileRadius, int maxRadiusMetres, int maxTiles) {
        List<int[]> tiles = new ArrayList<>();
        int step = tileRadius * 2;
        tiles.add(new int[]{0, 0});
        int ring = 1;
        while (tiles.size() < maxTiles) {
            double ringMinDistance = (double) ring * step;
            if (ringMinDistance > maxRadiusMetres) {
                break;
            }
            for (int[] cell : ringCells(ring)) {
                if (tiles.size() >= maxTiles) {
                    break;
                }
                double distance = Math.sqrt((double) cell[0] * cell[0] + (double) cell[1] * cell[1]) * step;
                if (distance <= maxRadiusMetres) {
                    tiles.add(cell);
                }
            }
            ring++;
        }
        return tiles;
    }

    private List<int[]> ringCells(int r) {
        List<int[]> cells = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            cells.add(new int[]{x, -r});
        }
        for (int z = -r + 1; z <= r; z++) {
            cells.add(new int[]{r, z});
        }
        for (int x = r - 1; x >= -r; x--) {
            cells.add(new int[]{x, r});
        }
        for (int z = r - 1; z >= -r + 1; z--) {
            cells.add(new int[]{-r, z});
        }
        return cells;
    }

    private static Component info(String text) {
        return Component.text(text, (TextColor) NamedTextColor.AQUA);
    }

    private static Component warn(String text) {
        return Component.text(text, (TextColor) NamedTextColor.RED);
    }

    private static final class ExpansionSession {
        final Player player;
        final double originLat;
        final double originLon;
        final int tileRadius;
        final int worldBaseY;
        final int originBlockX;
        final int originBlockZ;
        final World world;
        final TerraConfig config;
        final List<int[]> tiles;
        int tileIndex = 0;
        volatile boolean cancelled = false;
        volatile BlockPlacementTask currentTask;
        volatile Double sharedBaseElevation = null;

        ExpansionSession(Player player, double originLat, double originLon, int tileRadius, int worldBaseY, int originBlockX, int originBlockZ, World world, TerraConfig config, List<int[]> tiles) {
            this.player = player;
            this.originLat = originLat;
            this.originLon = originLon;
            this.tileRadius = tileRadius;
            this.worldBaseY = worldBaseY;
            this.originBlockX = originBlockX;
            this.originBlockZ = originBlockZ;
            this.world = world;
            this.config = config;
            this.tiles = tiles;
        }
    }
}
