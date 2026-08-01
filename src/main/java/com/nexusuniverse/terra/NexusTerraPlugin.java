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
import com.nexusuniverse.terra.generation.ExpansionManager;
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
        ExpansionManager expansionManager = new ExpansionManager(this, terrainGenerator);
        PluginCommand command = this.getCommand("nexusterra");
        if (command != null) {
            command.setExecutor((CommandExecutor)new NexusTerraCommand(this, terrainGenerator, expansionManager));
        }
        this.getLogger().info("NexusTerra enabled.");

        int clearHeight = this.getConfig().getInt("generation.clear-height", -1);
        if (clearHeight < 0) {
            this.getLogger().info("NexusTerra: generation.clear-height is -1 -- clearing goes all the way to this world's build height limit.");
        } else {
            this.getLogger().warning("NexusTerra: generation.clear-height is currently " + clearHeight + " (not -1), so clearing only reaches "
                    + clearHeight + " block(s) above the sampled ground height -- tall existing terrain/trees above that will be left standing. "
                    + "If you want full-height clearing, open config.yml and set generation.clear-height to -1, then re-run the command "
                    + "(no restart needed -- it's re-read from disk on every /nexusterra command). This is very likely stale from before that "
                    + "setting's default changed in v0.1.27 -- Bukkit never rewrites an existing config.yml on its own, only creates one if it's missing.");
        }
    }
}
