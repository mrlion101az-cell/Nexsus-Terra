/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  java.lang.Object
 *  java.lang.RuntimeException
 *  java.lang.String
 *  java.lang.Throwable
 *  java.net.URI
 *  java.net.URLEncoder
 *  java.net.http.HttpClient
 *  java.net.http.HttpRequest
 *  java.net.http.HttpRequest$BodyPublishers
 *  java.net.http.HttpResponse$BodyHandlers
 *  java.nio.charset.Charset
 *  java.nio.charset.StandardCharsets
 *  java.time.Duration
 *  java.util.ArrayList
 *  java.util.Collection
 *  java.util.HashMap
 *  java.util.List
 *  java.util.Map
 *  java.util.concurrent.CompletableFuture
 *  java.util.logging.Logger
 */
package com.nexusuniverse.terra.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusuniverse.terra.geo.GeoCache;
import com.nexusuniverse.terra.geo.GeoPoint;
import com.nexusuniverse.terra.geo.OsmFeature;
import java.lang.Object;
import java.lang.RuntimeException;
import java.lang.String;
import java.lang.Throwable;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class OverpassClient {
    private static final List<String> ENDPOINTS = List.of("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter");
    private final HttpClient httpClient;
    private final Logger logger;
    private final GeoCache cache;

    public OverpassClient(Logger logger, GeoCache cache) {
        this.logger = logger;
        this.cache = cache;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds((long)20L)).build();
    }

    public CompletableFuture<List<OsmFeature>> queryBoundingBox(double south, double west, double north, double east) {
        String bboxKey = GeoCache.bboxKey(south, west, north, east);
        String cachedBody = this.cache.getOverpassBody(bboxKey);
        if (cachedBody != null) {
            this.logger.info("[NexusTerra] Using cached OSM data for bounding box " + bboxKey + ".");
            try {
                return CompletableFuture.completedFuture(this.parse(cachedBody));
            }
            catch (RuntimeException ex) {
                this.logger.warning("[NexusTerra] Cached OSM data was unreadable, re-fetching: " + ex.getMessage());
            }
        }
        String query = "[out:json][timeout:90];\n(\n  way[\"building\"](%1$f,%2$f,%3$f,%4$f);\n  relation[\"building\"](%1$f,%2$f,%3$f,%4$f);\n  way[\"highway\"](%1$f,%2$f,%3$f,%4$f);\n  way[\"railway\"](%1$f,%2$f,%3$f,%4$f);\n  way[\"barrier\"](%1$f,%2$f,%3$f,%4$f);\n  way[\"natural\"=\"water\"](%1$f,%2$f,%3$f,%4$f);\n  relation[\"natural\"=\"water\"](%1$f,%2$f,%3$f,%4$f);\n  way[\"waterway\"](%1$f,%2$f,%3$f,%4$f);\n  relation[\"waterway\"](%1$f,%2$f,%3$f,%4$f);\n  way[\"landuse\"](%1$f,%2$f,%3$f,%4$f);\n  relation[\"landuse\"](%1$f,%2$f,%3$f,%4$f);\n  way[\"leisure\"](%1$f,%2$f,%3$f,%4$f);\n  relation[\"leisure\"](%1$f,%2$f,%3$f,%4$f);\n  way[\"amenity\"=\"parking\"](%1$f,%2$f,%3$f,%4$f);\n);\nout geom;\n".formatted(new Object[]{south, west, north, east});
        return this.tryEndpoint(query, bboxKey, 0);
    }

    private CompletableFuture<List<OsmFeature>> tryEndpoint(String query, String bboxKey, int endpointIndex) {
        if (endpointIndex >= ENDPOINTS.size()) {
            this.logger.warning("[NexusTerra] Every Overpass endpoint failed or timed out. Continuing with elevation-only terrain.");
            return CompletableFuture.completedFuture(List.of());
        }
        String endpoint = (String)ENDPOINTS.get(endpointIndex);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create((String)endpoint)).header("Content-Type", "application/x-www-form-urlencoded").header("User-Agent", "NexusTerra/0.1.7 (Minecraft terrain generator)").timeout(Duration.ofSeconds((long)105L)).POST(HttpRequest.BodyPublishers.ofString((String)("data=" + URLEncoder.encode((String)query, (Charset)StandardCharsets.UTF_8)))).build();
        this.logger.info("[NexusTerra] Querying Overpass endpoint " + (endpointIndex + 1) + "/" + ENDPOINTS.size() + ": " + endpoint);
        return this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).handle((response, throwable) -> {
            if (throwable != null) {
                this.logger.warning("[NexusTerra] Overpass request failed at " + endpoint + ": " + this.rootMessage((Throwable)throwable));
                return null;
            }
            if (response.statusCode() != 200) {
                this.logger.warning("[NexusTerra] Overpass endpoint " + endpoint + " returned status " + response.statusCode() + ". Body: " + this.truncate((String)response.body()));
                return null;
            }
            String remark = this.extractRemark((String)response.body());
            if (remark != null) {
                this.logger.warning("[NexusTerra] Overpass endpoint " + endpoint + " returned an error remark (likely a server-side timeout): " + remark);
                return null;
            }
            try {
                List<OsmFeature> features = this.parse((String)response.body());
                this.logger.info("[NexusTerra] Overpass returned " + features.size() + " feature(s).");
                this.cache.putOverpassBody(bboxKey, (String)response.body());
                return features;
            }
            catch (RuntimeException ex) {
                this.logger.warning("[NexusTerra] Could not parse Overpass response from " + endpoint + ": " + this.rootMessage(ex));
                return null;
            }
        }).thenCompose(features -> {
            if (features != null) {
                return CompletableFuture.completedFuture(features);
            }
            return this.tryEndpoint(query, bboxKey, endpointIndex + 1);
        });
    }

    private String extractRemark(String responseBody) {
        try {
            JsonElement root = JsonParser.parseString((String)responseBody);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonElement remark = root.getAsJsonObject().get("remark");
            return remark != null && !remark.isJsonNull() ? remark.getAsString() : null;
        }
        catch (RuntimeException ex) {
            return null;
        }
    }

    private List<OsmFeature> parse(String responseBody) {
        ArrayList features = new ArrayList();
        JsonElement root = JsonParser.parseString((String)responseBody);
        if (!root.isJsonObject()) {
            return features;
        }
        JsonArray elements = root.getAsJsonObject().getAsJsonArray("elements");
        if (elements == null) {
            return features;
        }
        for (JsonElement rawElement : elements) {
            JsonObject element;
            if (!rawElement.isJsonObject() || !(element = rawElement.getAsJsonObject()).has("type")) continue;
            String type = element.get("type").getAsString();
            JsonObject tags = element.has("tags") && element.get("tags").isJsonObject() ? element.getAsJsonObject("tags") : new JsonObject();
            Classification classification = this.classify(tags);
            if (classification == null) continue;
            ArrayList rings = new ArrayList();
            if ("way".equals((Object)type)) {
                List<GeoPoint> ring = this.readGeometry(element.getAsJsonArray("geometry"));
                if (ring.size() >= 2) {
                    rings.add(ring);
                }
            } else {
                JsonArray members;
                if (!"relation".equals((Object)type) || (members = element.getAsJsonArray("members")) == null) continue;
                for (JsonElement rawMember : members) {
                    List<GeoPoint> ring;
                    String role;
                    JsonObject member;
                    if (!rawMember.isJsonObject() || !(member = rawMember.getAsJsonObject()).has("type") || !"way".equals((Object)member.get("type").getAsString())) continue;
                    String string = role = member.has("role") ? member.get("role").getAsString() : "";
                    if (!role.isEmpty() && !"outer".equals((Object)role) && !"inner".equals((Object)role) || (ring = this.readGeometry(member.getAsJsonArray("geometry"))).size() < 3) continue;
                    rings.add(ring);
                }
            }
            if (rings.isEmpty()) continue;
            HashMap<String, String> tagMap = new HashMap<>();
            for (String key : tags.keySet()) {
                JsonElement value = tags.get(key);
                if (value == null || !value.isJsonPrimitive()) continue;
                tagMap.put(key, value.getAsString());
            }
            features.add(new OsmFeature(classification.category, classification.subtype, (List<List<GeoPoint>>)List.copyOf((Collection)rings), (Map<String, String>)Map.copyOf((Map)tagMap)));
        }
        return features;
    }

    private List<GeoPoint> readGeometry(JsonArray geometry) {
        ArrayList points = new ArrayList();
        if (geometry == null) {
            return points;
        }
        for (JsonElement rawPoint : geometry) {
            JsonObject point;
            if (!rawPoint.isJsonObject() || !(point = rawPoint.getAsJsonObject()).has("lat") || !point.has("lon")) continue;
            points.add(new GeoPoint(point.get("lat").getAsDouble(), point.get("lon").getAsDouble()));
        }
        return points;
    }

    private Classification classify(JsonObject tags) {
        if (tags.has("building")) {
            return new Classification(OsmFeature.Category.BUILDING, this.safeString(tags, "building", "yes"));
        }
        if (tags.has("highway")) {
            return new Classification(OsmFeature.Category.ROAD, this.safeString(tags, "highway", "road"));
        }
        if (tags.has("railway")) {
            return new Classification(OsmFeature.Category.RAILWAY, this.safeString(tags, "railway", "rail"));
        }
        if (tags.has("barrier")) {
            return new Classification(OsmFeature.Category.BARRIER, this.safeString(tags, "barrier", "fence"));
        }
        if (tags.has("natural") && "water".equals((Object)this.safeString(tags, "natural", "")) || tags.has("waterway")) {
            String subtype = tags.has("waterway") ? this.safeString(tags, "waterway", "water") : "water";
            return new Classification(OsmFeature.Category.WATER, subtype);
        }
        if (tags.has("landuse")) {
            return new Classification(OsmFeature.Category.LANDUSE, this.safeString(tags, "landuse", "unknown"));
        }
        if (tags.has("leisure")) {
            return new Classification(OsmFeature.Category.LANDUSE, this.safeString(tags, "leisure", "unknown"));
        }
        if (tags.has("amenity") && "parking".equals((Object)this.safeString(tags, "amenity", ""))) {
            return new Classification(OsmFeature.Category.LANDUSE, "parking");
        }
        return null;
    }

    private String safeString(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    private String truncate(String value) {
        if (value == null) {
            return "<no response body>";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 240 ? normalized.substring(0, 240) + "..." : normalized;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }

    private record Classification(OsmFeature.Category category, String subtype) {
    }
}
