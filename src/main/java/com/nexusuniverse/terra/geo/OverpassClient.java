package com.nexusuniverse.terra.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Queries public Overpass API mirrors for OpenStreetMap features.
 *
 * Public Overpass servers are shared resources and can occasionally return
 * 429/502/503/504 responses. This client therefore tries mirrors in order
 * and fails gracefully if every mirror is unavailable.
 *
 * v0.1.2: Overpass frequently returns HTTP 200 even when the query itself
 * timed out server-side - the timeout shows up as a "remark" field in the
 * JSON body instead of an HTTP error, which the old code never checked, so
 * a timed-out query silently looked like "0 buildings here" instead of
 * falling back to the next mirror. Now checked explicitly. Successful
 * results are also cached to disk by bounding box, so repeat requests for
 * the same area don't hit the network at all.
 */
public class OverpassClient {

    private static final List<String> ENDPOINTS = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
    );

    private final HttpClient httpClient;
    private final Logger logger;
    private final GeoCache cache;

    public OverpassClient(Logger logger, GeoCache cache) {
        this.logger = logger;
        this.cache = cache;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public CompletableFuture<List<OsmFeature>> queryBoundingBox(
            double south, double west, double north, double east) {

        String bboxKey = GeoCache.bboxKey(south, west, north, east);
        String cachedBody = cache.getOverpassBody(bboxKey);
        if (cachedBody != null) {
            logger.info("[NexusTerra] Using cached OSM data for bounding box " + bboxKey + ".");
            try {
                return CompletableFuture.completedFuture(parse(cachedBody));
            } catch (RuntimeException ex) {
                logger.warning("[NexusTerra] Cached OSM data was unreadable, re-fetching: " + ex.getMessage());
            }
        }

        String query = """
                [out:json][timeout:90];
                (
                  way["building"](%1$f,%2$f,%3$f,%4$f);
                  relation["building"](%1$f,%2$f,%3$f,%4$f);
                  way["highway"](%1$f,%2$f,%3$f,%4$f);
                  way["railway"](%1$f,%2$f,%3$f,%4$f);
                  way["barrier"](%1$f,%2$f,%3$f,%4$f);
                  way["natural"="water"](%1$f,%2$f,%3$f,%4$f);
                  relation["natural"="water"](%1$f,%2$f,%3$f,%4$f);
                  way["waterway"](%1$f,%2$f,%3$f,%4$f);
                  relation["waterway"](%1$f,%2$f,%3$f,%4$f);
                  way["landuse"](%1$f,%2$f,%3$f,%4$f);
                  relation["landuse"](%1$f,%2$f,%3$f,%4$f);
                  way["leisure"](%1$f,%2$f,%3$f,%4$f);
                  relation["leisure"](%1$f,%2$f,%3$f,%4$f);
                  way["amenity"="parking"](%1$f,%2$f,%3$f,%4$f);
                );
                out geom;
                """.formatted(south, west, north, east);

        return tryEndpoint(query, bboxKey, 0);
    }

    private CompletableFuture<List<OsmFeature>> tryEndpoint(String query, String bboxKey, int endpointIndex) {
        if (endpointIndex >= ENDPOINTS.size()) {
            logger.warning("[NexusTerra] Every Overpass endpoint failed or timed out. Continuing with elevation-only terrain.");
            return CompletableFuture.completedFuture(List.of());
        }

        String endpoint = ENDPOINTS.get(endpointIndex);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "NexusTerra/0.1.7 (Minecraft terrain generator)")
                .timeout(Duration.ofSeconds(105))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8)))
                .build();

        logger.info("[NexusTerra] Querying Overpass endpoint " + (endpointIndex + 1)
                + "/" + ENDPOINTS.size() + ": " + endpoint);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        logger.warning("[NexusTerra] Overpass request failed at " + endpoint + ": "
                                + rootMessage(throwable));
                        return null;
                    }

                    if (response.statusCode() != 200) {
                        logger.warning("[NexusTerra] Overpass endpoint " + endpoint + " returned status "
                                + response.statusCode() + ". Body: " + truncate(response.body()));
                        return null;
                    }

                    String remark = extractRemark(response.body());
                    if (remark != null) {
                        logger.warning("[NexusTerra] Overpass endpoint " + endpoint
                                + " returned an error remark (likely a server-side timeout): " + remark);
                        return null;
                    }

                    try {
                        List<OsmFeature> features = parse(response.body());
                        logger.info("[NexusTerra] Overpass returned " + features.size() + " feature(s).");
                        cache.putOverpassBody(bboxKey, response.body());
                        return features;
                    } catch (RuntimeException ex) {
                        logger.warning("[NexusTerra] Could not parse Overpass response from " + endpoint
                                + ": " + rootMessage(ex));
                        return null;
                    }
                })
                .thenCompose(features -> {
                    if (features != null) {
                        return CompletableFuture.completedFuture(features);
                    }
                    return tryEndpoint(query, bboxKey, endpointIndex + 1);
                });
    }

    /** Overpass embeds server-side errors (including timeouts) as a "remark" field even on HTTP 200. */
    private String extractRemark(String responseBody) {
        try {
            JsonElement root = JsonParser.parseString(responseBody);
            if (!root.isJsonObject()) return null;
            JsonElement remark = root.getAsJsonObject().get("remark");
            return remark != null && !remark.isJsonNull() ? remark.getAsString() : null;
        } catch (RuntimeException ex) {
            return null; // malformed body entirely - let the normal parse path report that
        }
    }

    private List<OsmFeature> parse(String responseBody) {
        List<OsmFeature> features = new ArrayList<>();
        JsonElement root = JsonParser.parseString(responseBody);
        if (!root.isJsonObject()) {
            return features;
        }

        JsonArray elements = root.getAsJsonObject().getAsJsonArray("elements");
        if (elements == null) {
            return features;
        }

        for (JsonElement rawElement : elements) {
            if (!rawElement.isJsonObject()) {
                continue;
            }
            JsonObject element = rawElement.getAsJsonObject();
            if (!element.has("type")) {
                continue;
            }
            String type = element.get("type").getAsString();

            JsonObject tags = element.has("tags") && element.get("tags").isJsonObject()
                    ? element.getAsJsonObject("tags")
                    : new JsonObject();

            Classification classification = classify(tags);
            if (classification == null) {
                continue;
            }

            List<List<GeoPoint>> rings = new ArrayList<>();

            if ("way".equals(type)) {
                List<GeoPoint> ring = readGeometry(element.getAsJsonArray("geometry"));
                if (ring.size() >= 2) {
                    rings.add(ring);
                }
            } else if ("relation".equals(type)) {
                // Multipolygon relations arrive as a member list. Both outer
                // boundaries and inner holes are kept: the rasterizer applies
                // the even-odd rule across every ring at once, which carves
                // the holes out without any extra bookkeeping here.
                JsonArray members = element.getAsJsonArray("members");
                if (members == null) {
                    continue;
                }
                for (JsonElement rawMember : members) {
                    if (!rawMember.isJsonObject()) {
                        continue;
                    }
                    JsonObject member = rawMember.getAsJsonObject();
                    if (!member.has("type") || !"way".equals(member.get("type").getAsString())) {
                        continue;
                    }
                    String role = member.has("role") ? member.get("role").getAsString() : "";
                    if (!role.isEmpty() && !"outer".equals(role) && !"inner".equals(role)) {
                        continue;
                    }
                    List<GeoPoint> ring = readGeometry(member.getAsJsonArray("geometry"));
                    if (ring.size() >= 3) {
                        rings.add(ring);
                    }
                }
            } else {
                continue;
            }

            if (rings.isEmpty()) {
                continue;
            }

            Map<String, String> tagMap = new java.util.HashMap<>();
            for (String key : tags.keySet()) {
                JsonElement value = tags.get(key);
                if (value != null && value.isJsonPrimitive()) {
                    tagMap.put(key, value.getAsString());
                }
            }

            features.add(new OsmFeature(classification.category, classification.subtype,
                    List.copyOf(rings), Map.copyOf(tagMap)));
        }

        return features;
    }

    private List<GeoPoint> readGeometry(JsonArray geometry) {
        List<GeoPoint> points = new ArrayList<>();
        if (geometry == null) {
            return points;
        }
        for (JsonElement rawPoint : geometry) {
            if (!rawPoint.isJsonObject()) {
                continue;
            }
            JsonObject point = rawPoint.getAsJsonObject();
            if (!point.has("lat") || !point.has("lon")) {
                continue;
            }
            points.add(new GeoPoint(point.get("lat").getAsDouble(), point.get("lon").getAsDouble()));
        }
        return points;
    }

    private record Classification(OsmFeature.Category category, String subtype) {}

    private Classification classify(JsonObject tags) {
        if (tags.has("building")) {
            return new Classification(OsmFeature.Category.BUILDING, safeString(tags, "building", "yes"));
        }
        if (tags.has("highway")) {
            return new Classification(OsmFeature.Category.ROAD, safeString(tags, "highway", "road"));
        }
        if (tags.has("railway")) {
            return new Classification(OsmFeature.Category.RAILWAY, safeString(tags, "railway", "rail"));
        }
        if (tags.has("barrier")) {
            return new Classification(OsmFeature.Category.BARRIER, safeString(tags, "barrier", "fence"));
        }
        if ((tags.has("natural") && "water".equals(safeString(tags, "natural", "")))
                || tags.has("waterway")) {
            String subtype = tags.has("waterway") ? safeString(tags, "waterway", "water") : "water";
            return new Classification(OsmFeature.Category.WATER, subtype);
        }
        if (tags.has("landuse")) {
            return new Classification(OsmFeature.Category.LANDUSE, safeString(tags, "landuse", "unknown"));
        }
        if (tags.has("leisure")) {
            return new Classification(OsmFeature.Category.LANDUSE, safeString(tags, "leisure", "unknown"));
        }
        if (tags.has("amenity") && "parking".equals(safeString(tags, "amenity", ""))) {
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
}
