package com.nexusuniverse.terra.generation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Applies a generation result on the main thread (block.setType() is not
 * safe off-thread), in two phases:
 *
 *   Phase 1 (clearing): walks every column in the generation circle and
 *   removes whatever pre-existing world sits above the new ground level
 *   -- trees, hillsides, leftover structures. Without this, the old
 *   terrain punches straight through the generated roads and buildings,
 *   which is exactly what the v0.1.4 test render showed.
 *
 *   Phase 2 (placement): applies the computed block list in order.
 *
 * Clearing reads far more blocks than it writes (most columns are
 * already air above ground), so it gets a much larger per-tick budget
 * than placement does.
 */
public class BlockPlacementTask extends BukkitRunnable {

    private static final int CLEAR_HEIGHT = 24;
    private static final int CLEAR_CHECKS_PER_TICK = 12000;

    private final World world;
    private final int originBlockX;
    private final int originBlockZ;
    private final GenerationResult result;
    private final List<BlockPlacement> placements;
    private final CommandSender notifyTarget;
    private final int placementsPerTick;

    private boolean clearingDone = false;
    private int clearX;
    private int placementIndex = 0;
    private int clearedCount = 0;

    public BlockPlacementTask(World world, int originBlockX, int originBlockZ, GenerationResult result,
                               CommandSender notifyTarget, int placementsPerTick) {
        this.world = world;
        this.originBlockX = originBlockX;
        this.originBlockZ = originBlockZ;
        this.result = result;
        this.placements = result.placements();
        this.notifyTarget = notifyTarget;
        this.placementsPerTick = placementsPerTick;
        this.clearX = -result.radius();
    }

    @Override
    public void run() {
        if (!clearingDone) {
            runClearingSlice();
            return;
        }
        runPlacementSlice();
    }

    private void runClearingSlice() {
        int radius = result.radius();
        int checks = 0;

        while (clearX <= radius && checks < CLEAR_CHECKS_PER_TICK) {
            for (int z = -radius; z <= radius; z++) {
                int groundY = result.heightAt(clearX, z);
                if (groundY == GenerationResult.OUTSIDE) {
                    continue;
                }
                int worldX = originBlockX + clearX;
                int worldZ = originBlockZ + z;
                int top = Math.min(world.getMaxHeight() - 1, groundY + CLEAR_HEIGHT);

                for (int y = groundY + 1; y <= top; y++) {
                    Block block = world.getBlockAt(worldX, y, worldZ);
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR, false);
                        clearedCount++;
                    }
                }
                checks += (top - groundY);
            }
            clearX++;
        }

        if (clearX > radius) {
            clearingDone = true;
            notifyTarget.sendMessage(Component.text(
                    "NexusTerra: site cleared (" + clearedCount + " block(s) removed). Building now...",
                    NamedTextColor.AQUA));
        }
    }

    private void runPlacementSlice() {
        int placedThisTick = 0;
        while (placedThisTick < placementsPerTick && placementIndex < placements.size()) {
            BlockPlacement placement = placements.get(placementIndex);
            world.getBlockAt(originBlockX + placement.x(), placement.y(), originBlockZ + placement.z())
                    .setType(placement.material(), false);
            placementIndex++;
            placedThisTick++;
        }

        if (placementIndex >= placements.size()) {
            notifyTarget.sendMessage(Component.text(
                    "NexusTerra: generation complete. " + placements.size() + " block(s) placed.",
                    NamedTextColor.GREEN));
            cancel();
        }
    }
}
