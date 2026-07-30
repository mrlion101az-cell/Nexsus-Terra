package com.nexusuniverse.terra;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * A plain immutable snapshot of config.yml, read once per generation
 * request.
 *
 * The generation code runs off the main thread, and Bukkit's
 * FileConfiguration is not documented as thread-safe, so reading every
 * value up front on the calling thread and passing this object across
 * is both safer and easier to follow than threading the plugin
 * instance through every helper.
 */
public record TerraConfig(
        int maxRadiusMetres,
        int defaultRadiusMetres,
        int placementsPerTick,
        int clearHeight,
        int clearChecksPerTick,
        int elevationSampleStep,

        int defaultWallHeight,
        int blocksPerLevel,
        int maxWallHeight,
        int windowWidth,
        int windowSpacing,
        boolean shopfronts,
        boolean rooftopDetails,
        boolean pitchedRoofs,

        boolean sidewalks,
        int sidewalkMinRoadWidth,
        boolean centreMarkings,
        int centreMarkingMinWidth,
        boolean lampPosts,
        int lampSpacing,
        int bridgeClearance,
        boolean skipTunnels,

        boolean water,
        boolean landuse,
        boolean railways,
        boolean barriers,
        boolean trees,
        boolean bridges
) {

    public static TerraConfig from(FileConfiguration c) {
        return new TerraConfig(
                c.getInt("generation.max-radius-metres", 300),
                c.getInt("generation.default-radius-metres", 150),
                c.getInt("generation.placements-per-tick", 2000),
                c.getInt("generation.clear-height", 24),
                c.getInt("generation.clear-checks-per-tick", 12000),
                Math.max(2, c.getInt("generation.elevation-sample-step", 8)),

                c.getInt("buildings.default-wall-height", 8),
                Math.max(2, c.getInt("buildings.blocks-per-level", 4)),
                c.getInt("buildings.max-wall-height", 140),
                Math.max(1, c.getInt("buildings.window-width", 2)),
                Math.max(2, c.getInt("buildings.window-spacing", 5)),
                c.getBoolean("buildings.shopfronts", true),
                c.getBoolean("buildings.rooftop-details", true),
                c.getBoolean("buildings.pitched-roofs", true),

                c.getBoolean("roads.sidewalks", true),
                c.getInt("roads.sidewalk-min-road-width", 4),
                c.getBoolean("roads.centre-markings", true),
                c.getInt("roads.centre-marking-min-width", 7),
                c.getBoolean("roads.lamp-posts", true),
                Math.max(2, c.getInt("roads.lamp-spacing", 11)),
                c.getInt("roads.bridge-clearance", 6),
                c.getBoolean("roads.skip-tunnels", true),

                c.getBoolean("features.water", true),
                c.getBoolean("features.landuse", true),
                c.getBoolean("features.railways", true),
                c.getBoolean("features.barriers", true),
                c.getBoolean("features.trees", true),
                c.getBoolean("features.bridges", true)
        );
    }
}
