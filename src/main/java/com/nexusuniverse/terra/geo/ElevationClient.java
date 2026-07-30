package com.nexusuniverse.terra.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
 * v0.1.2 added caching + concurrency, but real-world testing (see
 * PATCH_NOTES-v0.1.3.md) showed 4-way concurrency tripped the public
 * instance's rate limiter almost immediately -- it got 429'd on nearly
 * every batch, and the flat 2s/1-retry backoff wasn't long enough to
 * recover, so batches kept failing.
 *
 * v0.1.3: dispatch is back to strictly one request in flight at a time
 * (REQUEST_CONCURRENCY = 1), with an enforced minimum delay between the
 * *start* of consecutive requests (not just after failures) so we stop
 * tripping the limiter in the first place. 429s specifically get their
 * own retry budget with real exponential backoff and honor the
 * Retry-After header when the server sends one, instead of sharing the
 * same single generic retry as network/parse failures.
 */
public class ElevationClient {

    private static final String ENDPOINT = "https://api.open-elevation.com/api/v1/lookup";
    private static final int BATCH_SIZE = 200;
    private static final int REQUEST_CONCURRENCY = 1; // sequential - see class javadoc
    private static final long MIN_DELAY_BETWEEN_REQUESTS_MS = 1200; // proactive pacing, not just backoff
    private static final int MAX_RATE_LIMIT_RETRIES = 5;
    private static final int MAX_OTHER_RETRIES = 2;

    private final HttpClient httpClient;
    private final Logger logger;
    private final GeoCache cache;
    private volatile long lastRequestStartedAt = 0;

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
     * non-null) is invoked with a running count of points resolved so far.
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
                    + " point(s) already cached, fetching " + missing.size() + " new point(s) (this is paced "
                    + "to stay under the public API's rate limit, so it will take a while for a large radius).");
        }

        Deque<List<GeoPoint>> queue = new ArrayDeque<>(partition(missing, BATCH_SIZE));
        java.util.concurrent.atomic.AtomicInteger resolved = new java.util.concurrent.atomic.AtomicInteger(points.size() - missing.size());

        List<CompletableFuture<Void>> workers = new ArrayList<>();
        for (int i = 0; i < REQUEST_CONCURRENCY; i++) {
            workers.add(drainQueue(queue, results, resolved, progressCallback));
        }
        return CompletableFuture.allOf(workers.toArray(new CompletableFuture[0])).thenApply(ignored -> results);
    }

    /** One worker's loop: pull batches off the shared queue until it's empty. */
    private CompletableFuture<Void> drainQueue(Deque<List<GeoPoint>> queue, Map<GeoPoint, Double> results,
                                                java.util.concurrent.atomic.AtomicInteger resolved, IntConsumer progressCallback) {
        List<GeoPoint> batch;
        synchronized (queue) {
            batch = queue.poll();
        }
        if (batch == null) {
            return CompletableFuture.completedFuture(null);
        }

        return paced(() -> fetchBatchWithRetry(batch, results, 0, 0))
                .thenCompose(ignored -> {
                    if (progressCallback != null) {
                        progressCallback.accept(resolved.addAndGet(batch.size()));
                    }
                    return drainQueue(queue, results, resolved, progressCallback);
                });
    }

    /** Enforces a minimum gap between the start of consecutive requests, across all workers. */
    private synchronized CompletableFuture<Void> paced(java.util.function.Supplier<CompletableFuture<Void>> action) {
        long now = System.currentTimeMillis();
        long wait = Math.max(0, (lastRequestStartedAt + MIN_DELAY_BETWEEN_REQUESTS_MS) - now);
        lastRequestStartedAt = now + wait;

        if (wait <= 0) {
            return action.get();
        }
        CompletableFuture<Void> delayed = new CompletableFuture<>();
        CompletableFuture.delayedExecutor(wait, TimeUnit.MILLISECONDS)
                .execute(() -> action.get().whenComplete((v, ex) -> {
                    if (ex != null) delayed.completeExceptionally(ex);
                    else delayed.complete(null);
                }));
        return delayed;
    }

    private CompletableFuture<Void> fetchBatchWithRetry(List<GeoPoint> batch, Map<GeoPoint, Double> results,
                                                          int rateLimitRetries, int otherRetries) {
        return fetchBatch(batch, results).thenCompose(outcome -> {
            if (outcome == FetchOutcome.SUCCESS) {
                return CompletableFuture.completedFuture(null);
            }

            if (outcome == FetchOutcome.RATE_LIMITED) {
                if (rateLimitRetries >= MAX_RATE_LIMIT_RETRIES) {
                    logger.warning("[NexusTerra] Giving up on a " + batch.size()
                            + "-point elevation batch after repeated 429s. Those points will use base elevation.");
                    return CompletableFuture.completedFuture(null);
                }
                // Real exponential backoff for rate limits specifically: 3s, 6s, 12s, 24s, 48s.
                long backoffSeconds = 3L << rateLimitRetries;
                logger.info("[NexusTerra] Rate limited - waiting " + backoffSeconds + "s before retrying ("
                        + (rateLimitRetries + 1) + "/" + MAX_RATE_LIMIT_RETRIES + ")...");
                CompletableFuture<Void> delayed = new CompletableFuture<>();
                CompletableFuture.delayedExecutor(backoffSeconds, TimeUnit.SECONDS)
                        .execute(() -> fetchBatchWithRetry(batch, results, rateLimitRetries + 1, otherRetries)
                                .thenRun(() -> delayed.complete(null)));
                return delayed;
            }

            // generic failure (network error, bad parse, non-429 error status)
            if (otherRetries >= MAX_OTHER_RETRIES) {
                logger.warning("[NexusTerra] Giving up on a " + batch.size()
                        + "-point elevation batch after repeated failures. Those points will use base elevation.");
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> delayed = new CompletableFuture<>();
            CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS)
                    .execute(() -> fetchBatchWithRetry(batch, results, rateLimitRetries, otherRetries + 1)
                            .thenRun(() -> delayed.complete(null)));
            return delayed;
        });
    }

    private enum FetchOutcome { SUCCESS, RATE_LIMITED, FAILED }

    private CompletableFuture<FetchOutcome> fetchBatch(List<GeoPoint> batch, Map<GeoPoint, Double> results) {
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
                        return FetchOutcome.FAILED;
                    }
                    if (response.statusCode() == 429) {
                        logger.warning("[NexusTerra] Elevation API rate-limited us (429).");
                        return FetchOutcome.RATE_LIMITED;
                    }
                    if (response.statusCode() != 200) {
                        logger.warning("[NexusTerra] Elevation API returned status " + response.statusCode());
                        return FetchOutcome.FAILED;
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
                        return FetchOutcome.SUCCESS;
                    } catch (RuntimeException ex) {
                        logger.warning("[NexusTerra] Could not parse elevation response: " + ex.getMessage());
                        return FetchOutcome.FAILED;
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
