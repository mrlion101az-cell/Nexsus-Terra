package com.nexusuniverse.terra.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Queries real-world elevation for a set of lat/lon points using the
 * free public Open-Elevation API (https://open-elevation.com). No API
 * key required for reasonable use.
 *
 * IMPORTANT for anyone planning to sell/distribute this: the public
 * instance is a shared, rate-limited community resource, not meant for
 * production load from many servers at once. Self-hosting Open-Elevation
 * (it's open source, Docker image available) is the real answer before
 * this goes anywhere near paying customers -- same caution applies to
 * OverpassClient's public endpoint.
 *
 * Requests are batched (default 200 points/request) and sent
 * sequentially, not in parallel, to stay polite to the shared public
 * instance.
 */
public class ElevationClient {

    private static final String ENDPOINT = "https://api.open-elevation.com/api/v1/lookup";
    private static final int BATCH_SIZE = 200;

    private final HttpClient httpClient;
    private final Logger logger;

    public ElevationClient(Logger logger) {
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Looks up elevation (in meters) for every point given. Returns a map
     * keyed by the same GeoPoint objects passed in, so callers can match
     * results back to their original request points.
     */
    public CompletableFuture<Map<GeoPoint, Double>> lookup(List<GeoPoint> points) {
        Map<GeoPoint, Double> results = new java.util.concurrent.ConcurrentHashMap<>();
        List<List<GeoPoint>> batches = partition(points, BATCH_SIZE);

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (List<GeoPoint> batch : batches) {
            chain = chain.thenCompose(ignored -> fetchBatch(batch, results));
        }
        return chain.thenApply(ignored -> results);
    }

    private CompletableFuture<Void> fetchBatch(List<GeoPoint> batch, Map<GeoPoint, Double> results) {
        JsonArray locations = new JsonArray();
        for (GeoPoint point : batch) {
            JsonObject loc = new JsonObject();
            loc.addProperty("latitude", point.lat());
            loc.addProperty("longitude", point.lon());
            locations.add(loc);
        }
        JsonObject body = new JsonObject();
        body.add("locations", locations);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        logger.warning("[NexusTerra] Elevation API returned status " + response.statusCode());
                        return;
                    }
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonArray resultsArray = json.getAsJsonArray("results");
                    for (int i = 0; i < resultsArray.size() && i < batch.size(); i++) {
                        JsonObject entry = resultsArray.get(i).getAsJsonObject();
                        double elevation = entry.get("elevation").getAsDouble();
                        results.put(batch.get(i), elevation);
                    }
                })
                .exceptionally(ex -> {
                    logger.warning("[NexusTerra] Elevation batch request failed: " + ex.getMessage());
                    return null;
                });
    }

    private List<List<GeoPoint>> partition(List<GeoPoint> points, int size) {
        List<List<GeoPoint>> batches = new ArrayList<>();
        for (int i = 0; i < points.size(); i += size) {
            batches.add(points.subList(i, Math.min(i + size, points.size())));
        }
        return batches;
    }
}
