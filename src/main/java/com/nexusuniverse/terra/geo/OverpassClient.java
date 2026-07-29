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
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Queries public Overpass API mirrors for OpenStreetMap features.
 *
 * Public Overpass servers are shared resources and can occasionally return
 * 429/502/503/504 responses. This client therefore tries mirrors in order
 * and fails gracefully if every mirror is unavailable.
 */
public class OverpassClient {

    private static final List<String> ENDPOINTS = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
    );

    private final HttpClient httpClient;
    private final Logger logger;

    public OverpassClient(Logger logger) {
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public CompletableFuture<List<OsmFeature>> queryBoundingBox(
            double south, double west, double north, double east) {

        String query = """
                [out:json][timeout:45];
                (
                  way["building"](%f,%f,%f,%f);
                  way["highway"](%f,%f,%f,%f);
                  way["natural"="water"](%f,%f,%f,%f);
                  way["waterway"](%f,%f,%f,%f);
                  way["landuse"](%f,%f,%f,%f);
                );
                out geom;
                """.formatted(
                south, west, north, east,
                south, west, north, east,
                south, west, north, east,
                south, west, north, east,
                south, west, north, east
        );

        return tryEndpoint(query, 0);
    }

    private CompletableFuture<List<OsmFeature>> tryEndpoint(String query, int endpointIndex) {
        if (endpointIndex >= ENDPOINTS.size()) {
            logger.warning("[NexusTerra] Every Overpass endpoint failed. Continuing with elevation-only terrain.");
            return CompletableFuture.completedFuture(List.of());
        }

        String endpoint = ENDPOINTS.get(endpointIndex);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "NexusTerra/0.1.1 (Minecraft terrain generator)")
                .timeout(Duration.ofSeconds(60))
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

                    try {
                        List<OsmFeature> features = parse(response.body());
                        logger.info("[NexusTerra] Overpass returned " + features.size() + " feature(s).");
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
                    return tryEndpoint(query, endpointIndex + 1);
                });
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
            if (!element.has("type") || !"way".equals(element.get("type").getAsString())) {
                continue;
            }

            JsonArray geometry = element.getAsJsonArray("geometry");
            if (geometry == null || geometry.size() < 2) {
                continue;
            }

            JsonObject tags = element.has("tags") && element.get("tags").isJsonObject()
                    ? element.getAsJsonObject("tags")
                    : new JsonObject();

            OsmFeature.Category category;
            String subtype;

            if (tags.has("building")) {
                category = OsmFeature.Category.BUILDING;
                subtype = safeString(tags, "building", "yes");
            } else if (tags.has("highway")) {
                category = OsmFeature.Category.ROAD;
                subtype = safeString(tags, "highway", "road");
            } else if ((tags.has("natural") && "water".equals(safeString(tags, "natural", "")))
                    || tags.has("waterway")) {
                category = OsmFeature.Category.WATER;
                subtype = tags.has("waterway") ? safeString(tags, "waterway", "water") : "water";
            } else if (tags.has("landuse")) {
                category = OsmFeature.Category.LANDUSE;
                subtype = safeString(tags, "landuse", "unknown");
            } else {
                continue;
            }

            List<GeoPoint> vertices = new ArrayList<>(geometry.size());
            for (JsonElement rawPoint : geometry) {
                if (!rawPoint.isJsonObject()) {
                    continue;
                }
                JsonObject point = rawPoint.getAsJsonObject();
                if (!point.has("lat") || !point.has("lon")) {
                    continue;
                }
                vertices.add(new GeoPoint(point.get("lat").getAsDouble(), point.get("lon").getAsDouble()));
            }

            if (vertices.size() >= 2) {
                features.add(new OsmFeature(category, subtype, List.copyOf(vertices)));
            }
        }

        return features;
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
