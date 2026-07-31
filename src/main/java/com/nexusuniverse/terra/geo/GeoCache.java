/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.reflect.TypeToken
 *  java.io.BufferedReader
 *  java.io.BufferedWriter
 *  java.io.File
 *  java.io.IOException
 *  java.io.Reader
 *  java.lang.Appendable
 *  java.lang.Double
 *  java.lang.Object
 *  java.lang.RuntimeException
 *  java.lang.String
 *  java.lang.reflect.Type
 *  java.nio.charset.Charset
 *  java.nio.charset.StandardCharsets
 *  java.nio.file.Files
 *  java.nio.file.OpenOption
 *  java.nio.file.Path
 *  java.util.HashMap
 *  java.util.Map
 *  java.util.Map$Entry
 *  java.util.concurrent.ConcurrentHashMap
 *  java.util.logging.Logger
 */
package com.nexusuniverse.terra.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nexusuniverse.terra.geo.GeoPoint;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.Appendable;
import java.lang.Double;
import java.lang.Object;
import java.lang.RuntimeException;
import java.lang.String;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class GeoCache {
    private final Gson gson = new GsonBuilder().create();
    private final Logger logger;
    private final File elevationFile;
    private final File overpassFile;
    private final Map<GeoPoint, Double> elevationCache = new ConcurrentHashMap();
    private final Map<String, String> overpassCache = new ConcurrentHashMap();

    public GeoCache(File dataFolder, Logger logger) {
        this.logger = logger;
        File cacheDir = new File(dataFolder, "cache");
        cacheDir.mkdirs();
        this.elevationFile = new File(cacheDir, "elevation.json");
        this.overpassFile = new File(cacheDir, "overpass.json");
        this.load();
    }

    public Double getElevation(GeoPoint point) {
        return this.elevationCache.get(point);
    }

    public void putElevation(Map<GeoPoint, Double> batch) {
        this.elevationCache.putAll(batch);
        this.saveElevation();
    }

    public static String bboxKey(double south, double west, double north, double east) {
        return "%.4f,%.4f,%.4f,%.4f".formatted(new Object[]{south, west, north, east});
    }

    public String getOverpassBody(String bboxKey) {
        return this.overpassCache.get(bboxKey);
    }

    public void putOverpassBody(String bboxKey, String rawJsonBody) {
        this.overpassCache.put(bboxKey, rawJsonBody);
        this.saveOverpass();
    }

    private void load() {
        this.loadElevation();
        this.loadOverpass();
    }

    private void loadElevation() {
        if (!this.elevationFile.exists()) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader((Path)this.elevationFile.toPath(), (Charset)StandardCharsets.UTF_8);){
            Type type = new TypeToken<Map<String, Double>>(){}.getType();
            Map<String, Double> flat = this.gson.fromJson(reader, type);
            if (flat != null) {
                for (Map.Entry<String, Double> e : flat.entrySet()) {
                    String[] parts = ((String)e.getKey()).split(",");
                    if (parts.length != 2) continue;
                    this.elevationCache.put(new GeoPoint(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])), e.getValue());
                }
            }
            this.logger.info("[NexusTerra] Loaded " + this.elevationCache.size() + " cached elevation point(s).");
        }
        catch (IOException | RuntimeException e) {
            this.logger.warning("[NexusTerra] Could not load elevation cache: " + e.getMessage());
        }
    }

    private void loadOverpass() {
        if (!this.overpassFile.exists()) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader((Path)this.overpassFile.toPath(), (Charset)StandardCharsets.UTF_8);){
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> loaded = this.gson.fromJson(reader, type);
            if (loaded != null) {
                this.overpassCache.putAll(loaded);
            }
            this.logger.info("[NexusTerra] Loaded " + this.overpassCache.size() + " cached OSM bounding-box result(s).");
        }
        catch (IOException | RuntimeException e) {
            this.logger.warning("[NexusTerra] Could not load OSM cache: " + e.getMessage());
        }
    }

    private synchronized void saveElevation() {
        HashMap<String, Double> flat = new HashMap<>();
        for (Map.Entry<GeoPoint, Double> e : this.elevationCache.entrySet()) {
            flat.put(e.getKey().lat() + "," + e.getKey().lon(), e.getValue());
        }
        try (BufferedWriter writer = Files.newBufferedWriter((Path)this.elevationFile.toPath(), (Charset)StandardCharsets.UTF_8, (OpenOption[])new OpenOption[0]);){
            this.gson.toJson(flat, writer);
        }
        catch (IOException e) {
            this.logger.warning("[NexusTerra] Could not save elevation cache: " + e.getMessage());
        }
    }

    private synchronized void saveOverpass() {
        try (BufferedWriter writer = Files.newBufferedWriter((Path)this.overpassFile.toPath(), (Charset)StandardCharsets.UTF_8, (OpenOption[])new OpenOption[0]);){
            this.gson.toJson(this.overpassCache, (Appendable)writer);
        }
        catch (IOException e) {
            this.logger.warning("[NexusTerra] Could not save OSM cache: " + e.getMessage());
        }
    }
}
