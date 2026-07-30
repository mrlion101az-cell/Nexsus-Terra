package com.nexusuniverse.terra;

import com.nexusuniverse.terra.command.NexusTerraCommand;
import com.nexusuniverse.terra.generation.TerrainGenerator;
import com.nexusuniverse.terra.geo.ElevationClient;
import com.nexusuniverse.terra.geo.GeoCache;
import com.nexusuniverse.terra.geo.OverpassClient;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class NexusTerraPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();

        GeoCache geoCache = new GeoCache(getDataFolder(), getLogger());
        ElevationClient elevationClient = new ElevationClient(getLogger(), geoCache);
        OverpassClient overpassClient = new OverpassClient(getLogger(), geoCache);
        TerrainGenerator terrainGenerator = new TerrainGenerator(elevationClient, overpassClient, getLogger());

        PluginCommand command = getCommand("nexusterra");
        if (command != null) {
            command.setExecutor(new NexusTerraCommand(this, terrainGenerator));
        }

        getLogger().info("NexusTerra enabled.");
    }
}
