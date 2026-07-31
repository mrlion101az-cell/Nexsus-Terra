/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  java.lang.Object
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.PluginCommand
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.nexusuniverse.terra;

import com.nexusuniverse.terra.command.NexusTerraCommand;
import com.nexusuniverse.terra.generation.TerrainGenerator;
import com.nexusuniverse.terra.geo.ElevationClient;
import com.nexusuniverse.terra.geo.GeoCache;
import com.nexusuniverse.terra.geo.OverpassClient;
import java.lang.Object;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class NexusTerraPlugin
extends JavaPlugin {
    public void onEnable() {
        this.getDataFolder().mkdirs();
        this.saveDefaultConfig();
        GeoCache geoCache = new GeoCache(this.getDataFolder(), this.getLogger());
        ElevationClient elevationClient = new ElevationClient(this.getLogger(), geoCache);
        OverpassClient overpassClient = new OverpassClient(this.getLogger(), geoCache);
        TerrainGenerator terrainGenerator = new TerrainGenerator(elevationClient, overpassClient, this.getLogger());
        PluginCommand command = this.getCommand("nexusterra");
        if (command != null) {
            command.setExecutor((CommandExecutor)new NexusTerraCommand(this, terrainGenerator));
        }
        this.getLogger().info("NexusTerra enabled.");
    }
}
