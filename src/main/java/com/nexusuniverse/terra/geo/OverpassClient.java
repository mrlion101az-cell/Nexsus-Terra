package com.nexusuniverse.terra.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
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
 * Queries the free public Overpass API (https://overpass-api.de) for
 * OpenStreetMap features -- buildings, roads, water, and landuse areas --
 * within a bounding box. Uses Overpass's "out geom" output mode, which
 * attaches lat/lon coordinates directly to each way's geometry, avoiding
 * a separate node-resolution pass.
 *
 * Same production caution as ElevationClient: the public Overpass
 * instance is a shared community resource with real rate limits and an
 * explicit "fair use" policy. Self-hosting an Overpass instance (or
 * using a paid Overpass-compatible provider) is the real path before
 * this handles real user load.
 */
public class OverpassClient {

    private static final String ENDPOINT = "https://overpass-api.de/api/interpreter";

    private final HttpClient httpClient;
    private final Logger logger;

    public OverpassClient(Logger logger) {
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Fetches every building, road, water body, and landuse area whose
     * geometry falls within the given bounding box (south, west, north, east
     * in decimal degrees).
     */
    public CompletableFuture<List<OsmFeature>> queryBoundingBox(double south, double west, double north, double east) {
        String query = """
                [out:json][timeout:25];
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString("data=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.warning("[NexusTerra] Overpass API returned status " + response.statusCode() + ". Body: " + truncate(response.body()));
                        return List.<OsmFeature>of();
                    }
                    return parse(response.body());
                })
                .exceptionally(ex -> {
                    logger.warning("[NexusTerra] Overpass query failed: " + ex.getMessage());
                    return List.of();
                });
    }

    private List<OsmFeature> parse(String responseBody) {
        List<OsmFeature> features = new ArrayList<>();
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonArray elements = json.getAsJsonArray("elements");
        if (elements == null) {
            return features;
        }

        for (int i = 0; i < elements.size(); i++) {
            JsonObject element = elements.get(i).getAsJsonObject();
            if (!"way".equals(element.get("type").getAsString())) {
                continue;
            }
            JsonArray geometry = element.getAsJsonArray("geometry");
            if (geometry == null || geometry.isEmpty()) {
                continue;
            }

            JsonObject tags = element.has("tags") ? element.getAsJsonObject("tags") : new JsonObject();
            OsmFeature.Category category;
            String subtype;

            if (tags.has("building")) {
                category = OsmFeature.Category.BUILDING;
                subtype = tags.get("building").getAsString();
            } else if (tags.has("highway")) {
                category = OsmFeature.Category.ROAD;
                subtype = tags.get("highway").getAsString();
            } else if ((tags.has("natural") && "water".equals(tags.get("natural").getAsString())) || tags.has("waterway")) {
                category = OsmFeature.Category.WATER;
                subtype = tags.has("waterway") ? tags.get("waterway").getAsString() : "water";
            } else if (tags.has("landuse")) {
                category = OsmFeature.Category.LANDUSE;
                subtype = tags.get("landuse").getAsString();
            } else {
                continue;
            }

            List<GeoPoint> vertices = new ArrayList<>(geometry.size());
            for (int v = 0; v < geometry.size(); v++) {
                JsonObject point = geometry.get(v).getAsJsonObject();
                vertices.add(new GeoPoint(point.get("lat").getAsDouble(), point.get("lon").getAsDouble()));
            }

            features.add(new OsmFeature(category, subtype, vertices));
        }

        return features;
    }

    private String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
