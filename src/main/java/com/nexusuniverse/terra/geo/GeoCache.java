package com.nexusuniverse.terra.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Two independent disk-backed caches:
 *  - elevation: keyed by exact GeoPoint (the projection math is
 *    deterministic, so repeat requests at the same origin produce
 *    bit-identical grid points - no rounding needed for hits).
 *  - overpass: keyed by a rounded bounding-box string, since OSM feature
 *    queries are per-area rather than per-point.
 *
 * Both are loaded once at startup and flushed to disk after each new
 * batch of results, so a server restart doesn't lose the cache and a
 * crash mid-generation only loses the most recent unsaved batch.
 */
public class GeoCache {

    private final Gson gson = new GsonBuilder().create();
    private final Logger logger;
    private final File elevationFile;
    private final File overpassFile;

    private final Map<GeoPoint, Double> elevationCache = new ConcurrentHashMap<>();
    private final Map<String, String> overpassCache = new ConcurrentHashMap<>(); // bbox key -> raw JSON body

    public GeoCache(File dataFolder, Logger logger) {
        this.logger = logger;
        File cacheDir = new File(dataFolder, "cache");
        cacheDir.mkdirs();
        this.elevationFile = new File(cacheDir, "elevation.json");
        this.overpassFile = new File(cacheDir, "overpass.json");
        load();
    }

    /* ---------------- elevation ---------------- */

    public Double getElevation(GeoPoint point) {
        return elevationCache.get(point);
    }

    public void putElevation(Map<GeoPoint, Double> batch) {
        elevationCache.putAll(batch);
        saveElevation();
    }

    /* ---------------- overpass ---------------- */

    public static String bboxKey(double south, double west, double north, double east) {
        return "%.4f,%.4f,%.4f,%.4f".formatted(south, west, north, east);
    }

    public String getOverpassBody(String bboxKey) {
        return overpassCache.get(bboxKey);
    }

    public void putOverpassBody(String bboxKey, String rawJsonBody) {
        overpassCache.put(bboxKey, rawJsonBody);
        saveOverpass();
    }

    /* ---------------- persistence ---------------- */

    private void load() {
        loadElevation();
        loadOverpass();
    }

    private void loadElevation() {
        if (!elevationFile.exists()) return;
        try (Reader reader = Files.newBufferedReader(elevationFile.toPath(), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Double>>() {}.getType();
            Map<String, Double> flat = gson.fromJson(reader, type);
            if (flat != null) {
                for (Map.Entry<String, Double> e : flat.entrySet()) {
                    String[] parts = e.getKey().split(",");
                    if (parts.length != 2) continue;
                    elevationCache.put(new GeoPoint(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])), e.getValue());
                }
            }
            logger.info("[NexusTerra] Loaded " + elevationCache.size() + " cached elevation point(s).");
        } catch (IOException | RuntimeException e) {
            logger.warning("[NexusTerra] Could not load elevation cache: " + e.getMessage());
        }
    }

    private void loadOverpass() {
        if (!overpassFile.exists()) return;
        try (Reader reader = Files.newBufferedReader(overpassFile.toPath(), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = gson.fromJson(reader, type);
            if (loaded != null) overpassCache.putAll(loaded);
            logger.info("[NexusTerra] Loaded " + overpassCache.size() + " cached OSM bounding-box result(s).");
        } catch (IOException | RuntimeException e) {
            logger.warning("[NexusTerra] Could not load OSM cache: " + e.getMessage());
        }
    }

    private synchronized void saveElevation() {
        Map<String, Double> flat = new java.util.HashMap<>();
        for (Map.Entry<GeoPoint, Double> e : elevationCache.entrySet()) {
            flat.put(e.getKey().lat() + "," + e.getKey().lon(), e.getValue());
        }
        try (Writer writer = Files.newBufferedWriter(elevationFile.toPath(), StandardCharsets.UTF_8)) {
            gson.toJson(flat, writer);
        } catch (IOException e) {
            logger.warning("[NexusTerra] Could not save elevation cache: " + e.getMessage());
        }
    }

    private synchronized void saveOverpass() {
        try (Writer writer = Files.newBufferedWriter(overpassFile.toPath(), StandardCharsets.UTF_8)) {
            gson.toJson(overpassCache, writer);
        } catch (IOException e) {
            logger.warning("[NexusTerra] Could not save OSM cache: " + e.getMessage());
        }
    }
}
