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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
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
 * v0.1.2: previously ran every batch strictly sequentially with no
 * retry and no cache, which for a 300m-radius request (~29 batches)
 * could take 10+ minutes against a slow/overloaded public instance and
 * would silently give up on any batch that failed. Now: results are
 * cached to disk by exact point (repeat requests at the same origin
 * skip the network entirely), only genuinely missing points are
 * fetched, a bounded number of batches run concurrently instead of one
 * at a time, and a failed batch gets one retry before giving up.
 */
public class ElevationClient {

    private static final String ENDPOINT = "https://api.open-elevation.com/api/v1/lookup";
    private static final int BATCH_SIZE = 200;
    private static final int MAX_CONCURRENT_BATCHES = 4;

    private final HttpClient httpClient;
    private final Logger logger;
    private final GeoCache cache;

    public ElevationClient(Logger logger, GeoCache cache) {
        this.logger = logger;
        this.cache = cache;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public CompletableFuture<Map<GeoPoint, Double>> lookup(List<GeoPoint> points) {
        return lookup(points, null);
    }

    /**
     * Looks up elevation (in meters) for every point given. Returns a map
     * keyed by the same GeoPoint objects passed in. progressCallback (if
     * non-null) is invoked with a running count of points resolved so far,
     * from whatever thread the last batch in a wave completes on.
     */
    public CompletableFuture<Map<GeoPoint, Double>> lookup(List<GeoPoint> points, IntConsumer progressCallback) {
        Map<GeoPoint, Double> results = new ConcurrentHashMap<>();

        List<GeoPoint> missing = new ArrayList<>();
        for (GeoPoint point : points) {
            Double cached = cache.getElevation(point);
            if (cached != null) {
                results.put(point, cached);
            } else {
                missing.add(point);
            }
        }

        if (!missing.isEmpty()) {
            logger.info("[NexusTerra] Elevation: " + (points.size() - missing.size()) + "/" + points.size()
                    + " point(s) already cached, fetching " + missing.size() + " new point(s).");
        }

        List<List<GeoPoint>> batches = partition(missing, BATCH_SIZE);
        java.util.concurrent.atomic.AtomicInteger resolved = new java.util.concurrent.atomic.AtomicInteger(points.size() - missing.size());

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int i = 0; i < batches.size(); i += MAX_CONCURRENT_BATCHES) {
            List<List<GeoPoint>> wave = batches.subList(i, Math.min(i + MAX_CONCURRENT_BATCHES, batches.size()));
            chain = chain.thenCompose(ignored -> {
                List<CompletableFuture<Void>> waveFutures = new ArrayList<>();
                for (List<GeoPoint> batch : wave) {
                    waveFutures.add(fetchBatchWithRetry(batch, results, 1)
                            .thenRun(() -> {
                                if (progressCallback != null) {
                                    progressCallback.accept(resolved.addAndGet(batch.size()));
                                }
                            }));
                }
                return CompletableFuture.allOf(waveFutures.toArray(new CompletableFuture[0]));
            });
        }
        return chain.thenApply(ignored -> results);
    }

    private CompletableFuture<Void> fetchBatchWithRetry(List<GeoPoint> batch, Map<GeoPoint, Double> results, int retriesLeft) {
        return fetchBatch(batch, results).thenCompose(succeeded -> {
            if (succeeded || retriesLeft <= 0) {
                return CompletableFuture.completedFuture(null);
            }
            logger.info("[NexusTerra] Retrying an elevation batch (" + batch.size() + " point(s))...");
            CompletableFuture<Void> delayed = new CompletableFuture<>();
            CompletableFuture.delayedExecutor(2, java.util.concurrent.TimeUnit.SECONDS)
                    .execute(() -> fetchBatchWithRetry(batch, results, retriesLeft - 1).thenRun(() -> delayed.complete(null)));
            return delayed;
        });
    }

    /** Returns a future of whether the batch succeeded (so the caller can decide to retry). */
    private CompletableFuture<Boolean> fetchBatch(List<GeoPoint> batch, Map<GeoPoint, Double> results) {
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
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        logger.warning("[NexusTerra] Elevation batch request failed: " + throwable.getMessage());
                        return false;
                    }
                    if (response.statusCode() != 200) {
                        logger.warning("[NexusTerra] Elevation API returned status " + response.statusCode());
                        return false;
                    }
                    try {
                        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                        JsonArray resultsArray = json.getAsJsonArray("results");
                        Map<GeoPoint, Double> batchResults = new java.util.HashMap<>();
                        for (int i = 0; i < resultsArray.size() && i < batch.size(); i++) {
                            JsonObject entry = resultsArray.get(i).getAsJsonObject();
                            double elevation = entry.get("elevation").getAsDouble();
                            batchResults.put(batch.get(i), elevation);
                        }
                        results.putAll(batchResults);
                        cache.putElevation(batchResults);
                        return true;
                    } catch (RuntimeException ex) {
                        logger.warning("[NexusTerra] Could not parse elevation response: " + ex.getMessage());
                        return false;
                    }
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
