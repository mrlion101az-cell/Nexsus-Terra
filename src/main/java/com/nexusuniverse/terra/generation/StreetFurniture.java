package com.nexusuniverse.terra.generation;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

public class StreetFurniture {
    // A real street light silhouette -- pole, then a horizontal cross-arm reaching out to one
    // side, with the light hanging at the end of the arm rather than sitting directly on top of
    // the pole like a torch. This one detail is most of what separates "a lit stick" from
    // something that reads as an actual street light fixture.
    public static List<BlockPlacement> lampPost(int x, int groundY, int z, int worldMaxY, int variant) {
        List<BlockPlacement> out = new ArrayList<>();
        for (int i = 1; i <= 3; ++i) {
            int y = groundY + i;
            if (y >= worldMaxY - 1) {
                return out;
            }
            out.add(new BlockPlacement(x, y, z, Material.IRON_BARS));
        }
        int armY = groundY + 4;
        if (armY >= worldMaxY - 1) {
            return out;
        }
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] dir = directions[Math.abs(variant) % directions.length];
        int armX = x + dir[0];
        int armZ = z + dir[1];
        out.add(new BlockPlacement(x, armY, z, Material.IRON_BARS));
        out.add(new BlockPlacement(armX, armY, armZ, Material.CHAIN));
        int lampY = armY - 1;
        if (lampY >= worldMaxY - 1) {
            return out;
        }
        out.add(new BlockPlacement(armX, lampY, armZ, Material.SEA_LANTERN));
        return out;
    }

    // Wooden utility pole with a crossbar, standing taller than a lamp post so it reads as a
    // distinct piece of street furniture rather than another light.
    public static List<BlockPlacement> telephonePole(int x, int groundY, int z, int worldMaxY, int variant) {
        List<BlockPlacement> out = new ArrayList<>();
        int height = 7 + Math.abs(variant) % 2;
        for (int i = 1; i <= height; ++i) {
            int y = groundY + i;
            if (y >= worldMaxY - 1) {
                return out;
            }
            out.add(new BlockPlacement(x, y, z, Material.OAK_LOG));
        }
        int crossbarY = groundY + height - 1;
        if (crossbarY < worldMaxY - 1) {
            for (int dx = -1; dx <= 1; ++dx) {
                if (dx == 0) continue;
                out.add(new BlockPlacement(x + dx, crossbarY, z, Material.OAK_FENCE));
            }
        }
        return out;
    }

    // Pole-mounted traffic signal head at a genuine multi-way road intersection.
    public static List<BlockPlacement> trafficLight(int x, int groundY, int z, int worldMaxY) {
        List<BlockPlacement> out = new ArrayList<>();
        int poleHeight = 5;
        for (int i = 1; i <= poleHeight; ++i) {
            int y = groundY + i;
            if (y >= worldMaxY - 1) {
                return out;
            }
            out.add(new BlockPlacement(x, y, z, Material.IRON_BARS));
        }
        Material[] signal = {Material.RED_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE};
        for (int i = 0; i < signal.length; ++i) {
            int y = groundY + poleHeight - i;
            if (y >= worldMaxY - 1 || y < groundY + 1) continue;
            out.add(new BlockPlacement(x, y, z, signal[i]));
        }
        return out;
    }

    // A park-bench silhouette: two fence/wall "legs" flanking a facing stair as the seat.
    // Orientation (which way the seat faces, and whether it runs along X or Z) is picked from
    // the hash so a street full of benches doesn't all face the exact same way.
    private enum BenchMaterial {
        OAK(Material.OAK_STAIRS, Material.OAK_FENCE),
        SPRUCE(Material.SPRUCE_STAIRS, Material.SPRUCE_FENCE),
        DARK_OAK(Material.DARK_OAK_STAIRS, Material.DARK_OAK_FENCE),
        STONE(Material.STONE_STAIRS, Material.COBBLESTONE_WALL);

        final Material seat;
        final Material leg;

        BenchMaterial(Material seat, Material leg) {
            this.seat = seat;
            this.leg = leg;
        }
    }

    public static List<BlockPlacement> bench(int x, int groundY, int z, int worldMaxY, int variant) {
        List<BlockPlacement> out = new ArrayList<>();
        int y = groundY + 1;
        if (y >= worldMaxY - 1) {
            return out;
        }
        int v = Math.abs(variant);
        BenchMaterial material = BenchMaterial.values()[v % BenchMaterial.values().length];
        boolean alongX = (v / 4) % 2 == 0;
        boolean flipped = (v / 8) % 2 == 0;
        BlockFace seatFacing = alongX
                ? (flipped ? BlockFace.SOUTH : BlockFace.NORTH)
                : (flipped ? BlockFace.WEST : BlockFace.EAST);
        if (alongX) {
            out.add(new BlockPlacement(x - 1, y, z, material.leg));
            out.add(BlockPlacement.facing(x, y, z, material.seat, seatFacing));
            out.add(new BlockPlacement(x + 1, y, z, material.leg));
        } else {
            out.add(new BlockPlacement(x, y, z - 1, material.leg));
            out.add(BlockPlacement.facing(x, y, z, material.seat, seatFacing));
            out.add(new BlockPlacement(x, y, z + 1, material.leg));
        }
        return out;
    }

    // A small planter box along a sidewalk -- a low rim with a flower rising out of it. Kept to
    // a single column deliberately: sidewalk width isn't known at the call site, so this has to
    // work regardless of how narrow the available space is.
    public static List<BlockPlacement> planter(int x, int groundY, int z, int worldMaxY, int variant) {
        List<BlockPlacement> out = new ArrayList<>();
        int y = groundY + 1;
        if (y >= worldMaxY - 1) {
            return out;
        }
        out.add(new BlockPlacement(x, y, z, Material.STONE_BRICK_WALL));
        int plantY = y + 1;
        if (plantY < worldMaxY - 1) {
            out.add(new BlockPlacement(x, plantY, z, FLOWERS[Math.abs(variant) % FLOWERS.length]));
        }
        return out;
    }

    // A small circular fountain -- raised rim, a shallow water basin, and a light at the centre.
    // Meant for plaza/park centroids, not scattered like street furniture.
    public static List<BlockPlacement> fountain(int cx, int groundY, int cz, int worldMaxY, int variant) {
        List<BlockPlacement> out = new ArrayList<>();
        int y = groundY + 1;
        if (y >= worldMaxY - 1) {
            return out;
        }
        Material rim = Math.abs(variant) % 2 == 0 ? Material.QUARTZ_BLOCK : Material.STONE_BRICKS;
        int radius = 2;
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius + 0.3) continue;
                if (dist >= radius - 0.7) {
                    out.add(new BlockPlacement(cx + dx, y, cz + dz, rim));
                } else {
                    out.add(new BlockPlacement(cx + dx, groundY, cz + dz, Material.WATER));
                    out.add(new BlockPlacement(cx + dx, y, cz + dz, Material.WATER));
                }
            }
        }
        if (y + 1 < worldMaxY - 1) {
            out.add(new BlockPlacement(cx, y + 1, cz, Material.SEA_LANTERN));
        }
        return out;
    }

    // A thin, tall, slightly leaning trunk with fronds spread flat only at the very top -- a
    // proper palm silhouette rather than a round canopy, since a real palm doesn't look
    // remotely like an oak with different leaves.
    private static void buildPalmTree(List<BlockPlacement> out, int x, int groundY, int z, int worldMaxY, int variant) {
        int trunkHeight = 6 + (variant / 11) % 3;
        int lean = (variant / 13) % 3 - 1;
        int cx = x;
        for (int i = 1; i <= trunkHeight; ++i) {
            if (i > trunkHeight * 2 / 3) {
                cx = x + lean;
            }
            if (!placeLog(out, cx, groundY + i, z, worldMaxY, Material.JUNGLE_LOG)) {
                return;
            }
        }
        int topY = groundY + trunkHeight + 1;
        placeLeaf(out, cx, topY, z, worldMaxY, Material.JUNGLE_LEAVES);
        int[][] fronds = {{2, 0}, {1, 0}, {-1, 0}, {-2, 0}, {0, 2}, {0, 1}, {0, -1}, {0, -2}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] f : fronds) {
            placeLeaf(out, cx + f[0], topY, z + f[1], worldMaxY, Material.JUNGLE_LEAVES);
        }
    }

    // Landmark-scale tree: a thick 2x2 trunk and a wide, multi-layer canopy, towering well
    // above a normal street tree. Always oak/dark-oak coloured -- the point is scale, not species.
    private static void buildGiantTree(List<BlockPlacement> out, int x, int groundY, int z, int worldMaxY, int variant) {
        boolean darkOak = (variant / 25) % 2 == 0;
        Material log = darkOak ? Material.DARK_OAK_LOG : Material.OAK_LOG;
        Material leaves = darkOak ? Material.DARK_OAK_LEAVES : Material.OAK_LEAVES;
        int trunkHeight = 9 + (variant / 50) % 4;

        for (int i = 1; i <= trunkHeight; ++i) {
            int y = groundY + i;
            if (y >= worldMaxY - 1) return;
            for (int dx = 0; dx <= 1; ++dx) {
                for (int dz = 0; dz <= 1; ++dz) {
                    out.add(new BlockPlacement(x + dx, y, z + dz, log));
                }
            }
        }

        int crownBase = groundY + trunkHeight - 3;
        int maxRadius = 5;
        for (int dy = 0; dy <= 6; ++dy) {
            int y = crownBase + dy;
            if (y >= worldMaxY - 1) break;
            // Wide near the base of the crown, tapering to a rounded top -- a dome silhouette
            // rather than a normal tree's simple ball, since this is meant to read as ancient/huge.
            int layerRadius = dy <= 2 ? maxRadius : Math.max(1, maxRadius - (dy - 2));
            for (int dx = -layerRadius; dx <= layerRadius; ++dx) {
                for (int dz = -layerRadius; dz <= layerRadius; ++dz) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > layerRadius + 0.3) continue;
                    if (dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1 && dy < 2) continue; // leave trunk clear low down
                    placeLeaf(out, x + dx, y, z + dz, worldMaxY, leaves);
                }
            }
        }
    }

    private enum Species {
        OAK(Material.OAK_LOG, Material.OAK_LEAVES),
        BIRCH(Material.BIRCH_LOG, Material.BIRCH_LEAVES),
        SPRUCE(Material.SPRUCE_LOG, Material.SPRUCE_LEAVES),
        DARK_OAK(Material.DARK_OAK_LOG, Material.DARK_OAK_LEAVES),
        ACACIA(Material.ACACIA_LOG, Material.ACACIA_LEAVES),
        CHERRY(Material.CHERRY_LOG, Material.CHERRY_LEAVES),
        JUNGLE(Material.JUNGLE_LOG, Material.JUNGLE_LEAVES);

        final Material log;
        final Material leaves;

        Species(Material log, Material leaves) {
            this.log = log;
            this.leaves = leaves;
        }
    }

    public static List<BlockPlacement> tree(int x, int groundY, int z, int worldMaxY, int variant) {
        List<BlockPlacement> out = new ArrayList<>();
        int v = Math.abs(variant);

        // Roughly 1 in 25 trees is a landmark-scale giant instead of a normal street tree --
        // a real neighbourhood has the occasional huge old oak that towers over everything
        // else, and that variety reads as far more "real" than every tree being the same size.
        if (v % 25 == 0) {
            buildGiantTree(out, x, groundY, z, worldMaxY, v);
            return out;
        }

        // A palm gets its own roll rather than being folded into the species list -- there's no
        // OSM climate data driving this (this plugin has no way to know if a given spot is
        // actually somewhere palms would grow), so it's a stylistic variety roll, not a claim of
        // geographic accuracy.
        if (v % 17 == 5) {
            buildPalmTree(out, x, groundY, z, worldMaxY, v);
            return out;
        }

        Species species = Species.values()[v % Species.values().length];
        int sizeRoll = (v / 7) % 3;

        switch (species) {
            case SPRUCE:
                buildConiferTree(out, x, groundY, z, worldMaxY, species, sizeRoll);
                break;
            case ACACIA:
                buildUmbrellaTree(out, x, groundY, z, worldMaxY, species, sizeRoll);
                break;
            default:
                buildRoundTree(out, x, groundY, z, worldMaxY, species, sizeRoll);
                break;
        }
        return out;
    }

    private static boolean placeLog(List<BlockPlacement> out, int x, int y, int z, int worldMaxY, Material material) {
        if (y >= worldMaxY - 1) {
            return false;
        }
        out.add(new BlockPlacement(x, y, z, material));
        return true;
    }

    private static void placeLeaf(List<BlockPlacement> out, int x, int y, int z, int worldMaxY, Material material) {
        if (y >= worldMaxY - 1) {
            return;
        }
        out.add(new BlockPlacement(x, y, z, material));
    }

    // Classic round-crowned tree (oak, birch, dark oak) with a size-varying trunk and canopy.
    private static void buildRoundTree(List<BlockPlacement> out, int x, int groundY, int z, int worldMaxY, Species species, int sizeRoll) {
        int trunkHeight = 4 + sizeRoll;
        for (int i = 1; i <= trunkHeight; ++i) {
            if (!placeLog(out, x, groundY + i, z, worldMaxY, species.log)) {
                return;
            }
        }
        int crownBase = groundY + trunkHeight - 1;
        int radius = 2 + (sizeRoll == 2 ? 1 : 0);
        for (int dy = 0; dy <= 3; ++dy) {
            int layerRadius = dy >= 2 ? radius - 1 : radius;
            if (layerRadius < 1) layerRadius = 1;
            for (int dx = -layerRadius; dx <= layerRadius; ++dx) {
                for (int dz = -layerRadius; dz <= layerRadius; ++dz) {
                    if (dx == 0 && dz == 0 && dy < 3) continue;
                    if (Math.abs(dx) == layerRadius && Math.abs(dz) == layerRadius && layerRadius > 1) continue;
                    int y = crownBase + dy;
                    placeLeaf(out, x + dx, y, z + dz, worldMaxY, species.leaves);
                }
            }
        }
    }

    // Tall, tapering conifer silhouette for spruce.
    private static void buildConiferTree(List<BlockPlacement> out, int x, int groundY, int z, int worldMaxY, Species species, int sizeRoll) {
        int trunkHeight = 6 + sizeRoll * 2;
        for (int i = 1; i <= trunkHeight; ++i) {
            if (!placeLog(out, x, groundY + i, z, worldMaxY, species.log)) {
                return;
            }
        }
        int layers = 4 + sizeRoll;
        int baseY = groundY + 2;
        for (int layer = 0; layer < layers; ++layer) {
            int y = baseY + (layer * (trunkHeight - 2)) / Math.max(1, layers - 1);
            int radius = Math.max(1, (layers - layer) / 2 + 1);
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (Math.abs(dx) + Math.abs(dz) > radius) continue;
                    if (dx == 0 && dz == 0) continue;
                    placeLeaf(out, x + dx, y, z + dz, worldMaxY, species.leaves);
                }
            }
        }
        placeLeaf(out, x, groundY + trunkHeight + 1, z, worldMaxY, species.leaves);
    }

    // Flat, wide umbrella canopy for acacia.
    private static void buildUmbrellaTree(List<BlockPlacement> out, int x, int groundY, int z, int worldMaxY, Species species, int sizeRoll) {
        int trunkHeight = 4 + sizeRoll;
        int lean = sizeRoll % 2 == 0 ? 1 : -1;
        int cx = x;
        for (int i = 1; i <= trunkHeight; ++i) {
            if (i > trunkHeight / 2) {
                cx = x + lean;
            }
            if (!placeLog(out, cx, groundY + i, z, worldMaxY, species.log)) {
                return;
            }
        }
        int radius = 3 + sizeRoll;
        int canopyY = groundY + trunkHeight;
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                if (dx * dx + dz * dz > radius * radius) continue;
                placeLeaf(out, cx + dx, canopyY, z + dz, worldMaxY, species.leaves);
            }
        }
    }

    public static int hash(int x, int z) {
        int h = x * 73856093 ^ z * 19349663;
        return Math.abs(h);
    }

    private static final Material[] FLOWERS = {
            Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.OXEYE_DAISY, Material.AZURE_BLUET,
            Material.CORNFLOWER, Material.ALLIUM, Material.LILY_OF_THE_VALLEY,
            Material.WHITE_TULIP, Material.RED_TULIP, Material.ORANGE_TULIP, Material.PINK_TULIP
    };

    // A small irregular clump with no trunk -- a shrub, not a tree. Sometimes a literal berry
    // bush instead of a leaf clump, for a bit of real "bush" block variety rather than every
    // piece of undergrowth being either a flat flower or a full tree.
    public static List<BlockPlacement> bush(int x, int groundY, int z, int worldMaxY, int variant) {
        List<BlockPlacement> out = new ArrayList<>();
        int y = groundY + 1;
        if (y >= worldMaxY - 1) {
            return out;
        }
        int v = Math.abs(variant);
        if (v % 4 == 0) {
            out.add(new BlockPlacement(x, y, z, Material.SWEET_BERRY_BUSH));
            return out;
        }
        Material leaf = v % 2 == 0 ? Material.OAK_LEAVES : Material.AZALEA_LEAVES;
        out.add(new BlockPlacement(x, y, z, leaf));
        if (v % 3 != 0 && y + 1 < worldMaxY - 1) {
            out.add(new BlockPlacement(x, y + 1, z, leaf));
        }
        int[][] neighbours = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] n : neighbours) {
            if (Math.floorMod(v + n[0] * 3 + n[1] * 7, 3) == 0) {
                out.add(new BlockPlacement(x + n[0], y, z + n[1], leaf));
            }
        }
        return out;
    }

    // Ground cover for grass/wood surfaces: mostly short grass or fern, with an occasional flower.
    public static Material undergrowth(Material surface, int variant) {
        int v = Math.abs(variant);
        if (surface == Material.PODZOL) {
            return v % 6 == 0 ? Material.OXEYE_DAISY : Material.FERN;
        }
        if (v % 5 == 0) {
            return FLOWERS[(v / 5) % FLOWERS.length];
        }
        return Material.SHORT_GRASS;
    }
}
