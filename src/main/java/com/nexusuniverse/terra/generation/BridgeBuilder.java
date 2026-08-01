package com.nexusuniverse.terra.generation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import org.bukkit.Material;

public class BridgeBuilder {
    private static final int PILLAR_SPACING = 9;
    private static final int MAX_PILLAR_DROP = 40;
    // Maximum change in deck height per block travelled along the path. Keeps ramps sloping
    // gradually down to grade instead of the deck sitting at one flat height for the whole way.
    private static final double MAX_GRADE_PER_BLOCK = 0.35;

    public static Deck build(List<double[]> centreline, int width, Material deckMaterial, Material railMaterial, BiFunction<Integer, Integer, Integer> groundHeightAt, int clearance, int worldMaxY) {
        List<BlockPlacement> out = new ArrayList<>();
        Set<Long> deckCells = new HashSet<>();
        if (centreline.size() < 2) {
            return new Deck(out, deckCells, 0);
        }

        List<int[]> path = new ArrayList<>();
        List<Integer> pathGround = new ArrayList<>();
        for (int i = 0; i + 1 < centreline.size(); i++) {
            double[] a = centreline.get(i);
            double[] b = centreline.get(i + 1);
            double dx = b[0] - a[0];
            double dz = b[1] - a[1];
            double length = Math.sqrt(dx * dx + dz * dz);
            int steps = Math.max(1, (int) Math.ceil(length));
            int startS = i == 0 ? 0 : 1;
            for (int s = startS; s <= steps; s++) {
                double t = (double) s / steps;
                int cx = (int) Math.round(a[0] + dx * t);
                int cz = (int) Math.round(a[1] + dz * t);
                path.add(new int[]{cx, cz});
                pathGround.add(groundHeightAt.apply(cx, cz));
            }
        }

        int n = path.size();
        if (n == 0) {
            return new Deck(out, deckCells, 0);
        }

        double[] target = new double[n];
        for (int i = 0; i < n; i++) {
            target[i] = Math.min(worldMaxY - 6, pathGround.get(i) + clearance);
        }

        double[] smoothed = new double[n];
        smoothed[0] = target[0];
        for (int i = 1; i < n; i++) {
            double prev = smoothed[i - 1];
            smoothed[i] = Math.max(prev - MAX_GRADE_PER_BLOCK, Math.min(prev + MAX_GRADE_PER_BLOCK, target[i]));
        }
        for (int i = n - 2; i >= 0; i--) {
            double next = smoothed[i + 1];
            smoothed[i] = Math.max(next - MAX_GRADE_PER_BLOCK, Math.min(next + MAX_GRADE_PER_BLOCK, smoothed[i]));
        }

        int[] deckY = new int[n];
        for (int i = 0; i < n; i++) {
            int minY = pathGround.get(i) + 1;
            deckY[i] = Math.max(minY, (int) Math.round(smoothed[i]));
        }

        int lo = -((width - 1) / 2);
        int hi = width / 2;
        int railLo = lo - 1;
        int railHi = hi + 1;

        Map<Long, Integer> surfaceHeight = new LinkedHashMap<>();
        Map<Long, Integer> railOnlyHeight = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int cx = path.get(i)[0];
            int cz = path.get(i)[1];
            int y = deckY[i];
            for (int ox = railLo; ox <= railHi; ox++) {
                for (int oz = railLo; oz <= railHi; oz++) {
                    int x = cx + ox;
                    int z = cz + oz;
                    long key = pack(x, z);
                    boolean isSurface = ox >= lo && ox <= hi && oz >= lo && oz <= hi;
                    if (isSurface) {
                        surfaceHeight.putIfAbsent(key, y);
                    } else {
                        railOnlyHeight.putIfAbsent(key, y);
                    }
                }
            }
        }

        for (Map.Entry<Long, Integer> entry : surfaceHeight.entrySet()) {
            int[] xz = unpack(entry.getKey());
            int y = entry.getValue();
            out.add(new BlockPlacement(xz[0], y, xz[1], deckMaterial));
            out.add(new BlockPlacement(xz[0], y - 1, xz[1], railMaterial));
            deckCells.add(entry.getKey());
        }
        for (Map.Entry<Long, Integer> entry : railOnlyHeight.entrySet()) {
            long key = entry.getKey();
            if (surfaceHeight.containsKey(key)) continue;
            int[] xz = unpack(key);
            int y = entry.getValue();
            out.add(new BlockPlacement(xz[0], y, xz[1], railMaterial));
            if (y + 1 < worldMaxY - 1) {
                out.add(new BlockPlacement(xz[0], y + 1, xz[1], Material.IRON_BARS));
            }
            deckCells.add(key);
        }

        for (int i = 0; i < n; i++) {
            int cx = path.get(i)[0];
            int cz = path.get(i)[1];
            if (Math.floorMod(cx, PILLAR_SPACING) != 0 || Math.floorMod(cz, PILLAR_SPACING) != 0) continue;
            int y = deckY[i];
            int ground = pathGround.get(i);
            int bottom = Math.max(ground, y - MAX_PILLAR_DROP);
            for (int py = y - 2; py >= bottom; py--) {
                out.add(new BlockPlacement(cx, py, cz, Material.STONE_BRICKS));
            }
        }

        buildArchSpans(out, path, pathGround, deckY, lo, hi, Material.STONE_BRICKS, worldMaxY);

        int endDeckY = deckY[n - 1];
        return new Deck(out, deckCells, endDeckY);
    }

    // Wherever two adjacent pillars have enough clearance underneath the deck, fill in a real
    // stone arch between them (a true semicircle, springing from just above the ground up to
    // just under the deck) instead of leaving the span as bare open air between bare posts.
    // Short spans or spans without much headroom are left as plain pillars -- there's no room
    // for an arch to read as an arch, so this only adds material where it will actually look
    // like one, never forcing the shape in somewhere it doesn't fit.
    private static final int MIN_ARCH_SPAN = 4;
    private static final int MIN_ARCH_CLEARANCE = 5;
    private static final int ARCH_SPRING_OFFSET = 2;

    private static void buildArchSpans(List<BlockPlacement> out, List<int[]> path, List<Integer> pathGround,
                                        int[] deckY, int lo, int hi, Material archMaterial, int worldMaxY) {
        int n = path.size();
        List<Integer> pillars = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int cx = path.get(i)[0];
            int cz = path.get(i)[1];
            if (Math.floorMod(cx, PILLAR_SPACING) == 0 && Math.floorMod(cz, PILLAR_SPACING) == 0) {
                pillars.add(i);
            }
        }

        Set<Long> filled = new HashSet<>();
        for (int p = 0; p + 1 < pillars.size(); p++) {
            int i0 = pillars.get(p);
            int i1 = pillars.get(p + 1);
            int span = i1 - i0;
            if (span < MIN_ARCH_SPAN) continue;

            int deckUnderside = Math.min(deckY[i0], deckY[i1]) - 1;
            int groundHere = Math.max(pathGround.get(i0), pathGround.get(i1));
            if (deckUnderside - groundHere < MIN_ARCH_CLEARANCE) continue;

            int springY = groundHere + ARCH_SPRING_OFFSET;
            double radius = span / 2.0;
            double centre = (i0 + i1) / 2.0;

            for (int i = i0; i <= i1; i++) {
                double dx = i - centre;
                double underSq = radius * radius - dx * dx;
                if (underSq < 0) continue; // shouldn't happen within [i0, i1], but stay safe
                int curveY = Math.min(deckUnderside, (int) Math.round(springY + Math.sqrt(underSq)));
                int cx = path.get(i)[0];
                int cz = path.get(i)[1];
                int localUnderside = deckY[i] - 1;

                for (int ox = lo; ox <= hi; ox++) {
                    for (int oz = lo; oz <= hi; oz++) {
                        int x = cx + ox;
                        int z = cz + oz;
                        long key = pack(x, z);
                        if (!filled.add(key)) continue;
                        for (int y = curveY + 1; y <= localUnderside && y < worldMaxY - 1; y++) {
                            out.add(new BlockPlacement(x, y, z, archMaterial));
                        }
                        if (curveY >= springY && curveY < worldMaxY - 1) {
                            out.add(new BlockPlacement(x, curveY, z, archMaterial));
                        }
                    }
                }
            }
        }
    }

    private static long pack(int x, int z) {
        return (long) (x & 0xFFFF) << 16 | (long) (z & 0xFFFF);
    }

    private static int[] unpack(long key) {
        int x = (int) (short) ((key >> 16) & 0xFFFF);
        int z = (int) (short) (key & 0xFFFF);
        return new int[]{x, z};
    }

    public record Deck(List<BlockPlacement> placements, Set<Long> deckCells, int deckY) {
    }
}
