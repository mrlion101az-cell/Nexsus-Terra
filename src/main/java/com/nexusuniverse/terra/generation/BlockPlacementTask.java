/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  java.lang.Integer
 *  java.lang.Math
 *  java.lang.Object
 *  java.lang.String
 *  java.util.List
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.command.CommandSender
 *  org.bukkit.scheduler.BukkitRunnable
 */
package com.nexusuniverse.terra.generation;

import com.nexusuniverse.terra.generation.BlockPlacement;
import com.nexusuniverse.terra.generation.GenerationResult;
import java.lang.Integer;
import java.lang.Math;
import java.lang.Object;
import java.lang.String;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

public class BlockPlacementTask
extends BukkitRunnable {
    private final World world;
    private final int originBlockX;
    private final int originBlockZ;
    private final GenerationResult result;
    private final List<BlockPlacement> placements;
    private final CommandSender notifyTarget;
    private final int placementsPerTick;
    private final int clearHeight;
    private final int clearChecksPerTick;
    private boolean clearingDone = false;
    private int clearX;
    private int placementIndex = 0;
    private int clearedCount = 0;

    public BlockPlacementTask(World world, int originBlockX, int originBlockZ, GenerationResult result, CommandSender notifyTarget, int placementsPerTick, int clearHeight, int clearChecksPerTick) {
        this.world = world;
        this.originBlockX = originBlockX;
        this.originBlockZ = originBlockZ;
        this.result = result;
        this.placements = result.placements();
        this.notifyTarget = notifyTarget;
        this.placementsPerTick = placementsPerTick;
        this.clearHeight = clearHeight;
        this.clearChecksPerTick = clearChecksPerTick;
        this.clearX = -result.radius();
    }

    public void run() {
        if (!this.clearingDone) {
            this.runClearingSlice();
            return;
        }
        this.runPlacementSlice();
    }

    private void runClearingSlice() {
        int radius = this.result.radius();
        int checks = 0;
        while (this.clearX <= radius && checks < this.clearChecksPerTick) {
            for (int z = -radius; z <= radius; ++z) {
                int groundY = this.result.heightAt(this.clearX, z);
                if (groundY == Integer.MIN_VALUE) continue;
                int worldX = this.originBlockX + this.clearX;
                int worldZ = this.originBlockZ + z;
                int top = Math.min((int)(this.world.getMaxHeight() - 1), (int)(groundY + this.clearHeight));
                for (int y = groundY + 1; y <= top; ++y) {
                    Block block = this.world.getBlockAt(worldX, y, worldZ);
                    if (block.getType() == Material.AIR) continue;
                    block.setType(Material.AIR, false);
                    ++this.clearedCount;
                }
                checks += top - groundY;
            }
            ++this.clearX;
        }
        if (this.clearX > radius) {
            this.clearingDone = true;
            this.notifyTarget.sendMessage((Component)Component.text((String)("NexusTerra: site cleared (" + this.clearedCount + " block(s) removed). Building now..."), (TextColor)NamedTextColor.AQUA));
        }
    }

    private void runPlacementSlice() {
        for (int placedThisTick = 0; placedThisTick < this.placementsPerTick && this.placementIndex < this.placements.size(); ++placedThisTick) {
            BlockPlacement placement = this.placements.get(this.placementIndex);
            Block block = this.world.getBlockAt(this.originBlockX + placement.x(), placement.y(), this.originBlockZ + placement.z());
            block.setType(placement.material(), false);
            if (placement.label() != null) {
                BlockState state = block.getState();
                if (state instanceof Sign sign) {
                    sign.setLine(0, placement.label());
                    sign.update(true, false);
                }
            }
            ++this.placementIndex;
        }
        if (this.placementIndex >= this.placements.size()) {
            this.notifyTarget.sendMessage((Component)Component.text((String)("NexusTerra: generation complete. " + this.placements.size() + " block(s) placed."), (TextColor)NamedTextColor.GREEN));
            this.cancel();
        }
    }
}
