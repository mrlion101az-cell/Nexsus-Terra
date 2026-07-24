package com.nexusuniverse.terra;

import com.nexusuniverse.terra.command.NexusTerraCommand;
import com.nexusuniverse.terra.generation.TerrainGenerator;
import com.nexusuniverse.terra.geo.ElevationClient;
import com.nexusuniverse.terra.geo.OverpassClient;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class NexusTerraPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        ElevationClient elevationClient = new ElevationClient(getLogger());
        OverpassClient overpassClient = new OverpassClient(getLogger());
        TerrainGenerator terrainGenerator = new TerrainGenerator(elevationClient, overpassClient, getLogger());

        PluginCommand command = getCommand("nexusterra");
        if (command != null) {
            command.setExecutor(new NexusTerraCommand(this, terrainGenerator));
        }

        getLogger().info("NexusTerra v" + getDescription().getVersion() + " enabled.");
    }
}
