package com.nexusuniverse.terra.generation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Applies a pre-computed BlockPlacement list a batch at a time, on the
 * main thread (block.setType() is not safe to call off-thread). This is
 * the piece that fulfills "slowly generate it if they have the
 * processing power" -- placements per tick is tunable; higher values
 * finish faster but risk visible server lag on weaker hardware.
 */
public class BlockPlacementTask extends BukkitRunnable {

    private final World world;
    private final int originBlockX;
    private final int originBlockZ;
    private final List<BlockPlacement> placements;
    private final CommandSender notifyTarget;
    private final int placementsPerTick;

    private int index = 0;

    public BlockPlacementTask(World world, int originBlockX, int originBlockZ, List<BlockPlacement> placements,
                               CommandSender notifyTarget, int placementsPerTick) {
        this.world = world;
        this.originBlockX = originBlockX;
        this.originBlockZ = originBlockZ;
        this.placements = placements;
        this.notifyTarget = notifyTarget;
        this.placementsPerTick = placementsPerTick;
    }

    @Override
    public void run() {
        int placedThisTick = 0;
        while (placedThisTick < placementsPerTick && index < placements.size()) {
            BlockPlacement placement = placements.get(index);
            world.getBlockAt(originBlockX + placement.x(), placement.y(), originBlockZ + placement.z())
                    .setType(placement.material(), false);
            index++;
            placedThisTick++;
        }

        if (index >= placements.size()) {
            notifyTarget.sendMessage(Component.text(
                    "NexusTerra: generation complete. " + placements.size() + " block(s) placed.",
                    NamedTextColor.GREEN));
            cancel();
        }
    }
}
