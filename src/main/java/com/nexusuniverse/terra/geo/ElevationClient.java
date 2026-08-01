/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  java.lang.Double
 *  java.lang.Math
 *  java.lang.Number
 *  java.lang.Object
 *  java.lang.RuntimeException
 *  java.lang.String
 *  java.lang.System
 *  java.lang.Void
 *  java.net.URI
 *  java.net.http.HttpClient
 *  java.net.http.HttpRequest
 *  java.net.http.HttpRequest$BodyPublishers
 *  java.net.http.HttpResponse$BodyHandlers
 *  java.time.Duration
 *  java.util.ArrayDeque
 *  java.util.ArrayList
 *  java.util.Deque
 *  java.util.HashMap
 *  java.util.List
 *  java.util.Map
 *  java.util.concurrent.CompletableFuture
 *  java.util.concurrent.ConcurrentHashMap
 *  java.util.concurrent.TimeUnit
 *  java.util.concurrent.atomic.AtomicInteger
 *  java.util.function.IntConsumer
 *  java.util.function.Supplier
 *  java.util.logging.Logger
 */
package com.nexusuniverse.terra.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusuniverse.terra.geo.GeoCache;
import com.nexusuniverse.terra.geo.GeoPoint;
import java.lang.Double;
import java.lang.Math;
import java.lang.Number;
import java.lang.Object;
import java.lang.RuntimeException;
import java.lang.String;
import java.lang.System;
import java.lang.Void;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class ElevationClient {
    private static final String ENDPOINT = "https://api.open-elevation.com/api/v1/lookup";
    private static final int BATCH_SIZE = 200;
    private static final int REQUEST_CONCURRENCY = 1;
    private static final long MIN_DELAY_BETWEEN_REQUESTS_MS = 1200L;
    private static final int MAX_RATE_LIMIT_RETRIES = 5;
    private static final int MAX_OTHER_RETRIES = 2;
    private final HttpClient httpClient;
    private final Logger logger;
    private final GeoCache cache;
    private volatile long lastRequestStartedAt = 0L;

    public ElevationClient(Logger logger, GeoCache cache) {
        this.logger = logger;
        this.cache = cache;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds((long)15L)).build();
    }

    public CompletableFuture<Map<GeoPoint, Double>> lookup(List<GeoPoint> points) {
        return this.lookup(points, null);
    }

    public CompletableFuture<Map<GeoPoint, Double>> lookup(List<GeoPoint> points, IntConsumer progressCallback) {
        ConcurrentHashMap<GeoPoint, Double> results = new ConcurrentHashMap<>();
        ArrayList<GeoPoint> missing = new ArrayList<>();
        for (GeoPoint point : points) {
            Double cached = this.cache.getElevation(point);
            if (cached != null) {
                results.put(point, cached);
                continue;
            }
            missing.add(point);
        }
        if (!missing.isEmpty()) {
            this.logger.info("[NexusTerra] Elevation: " + (points.size() - missing.size()) + "/" + points.size() + " point(s) already cached, fetching " + missing.size() + " new point(s) (this is paced to stay under the public API's rate limit, so it will take a while for a large radius).");
        }
        ArrayDeque<List<GeoPoint>> queue = new ArrayDeque<>(this.partition(missing, 200));
        AtomicInteger resolved = new AtomicInteger(points.size() - missing.size());
        ArrayList<CompletableFuture<Void>> workers = new ArrayList<>();
        for (int i = 0; i < 1; ++i) {
            workers.add(this.drainQueue(queue, results, resolved, progressCallback));
        }
        return CompletableFuture.allOf(workers.toArray(new CompletableFuture[0])).thenApply(ignored -> results);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private CompletableFuture<Void> drainQueue(Deque<List<GeoPoint>> queue, Map<GeoPoint, Double> results, AtomicInteger resolved, IntConsumer progressCallback) {
        List<GeoPoint> batch;
        Deque<List<GeoPoint>> deque = queue;
        synchronized (deque) {
            batch = queue.poll();
        }
        if (batch == null) {
            return CompletableFuture.completedFuture(null);
        }
        return this.paced(() -> this.fetchBatchWithRetry(batch, results, 0, 0)).thenCompose(ignored -> {
            if (progressCallback != null) {
                progressCallback.accept(resolved.addAndGet(batch.size()));
            }
            return this.drainQueue(queue, results, resolved, progressCallback);
        });
    }

    private synchronized CompletableFuture<Void> paced(Supplier<CompletableFuture<Void>> action) {
        long now = System.currentTimeMillis();
        long wait = Math.max((long)0L, (long)(this.lastRequestStartedAt + 1200L - now));
        this.lastRequestStartedAt = now + wait;
        if (wait <= 0L) {
            return action.get();
        }
        CompletableFuture<Void> delayed = new CompletableFuture<>();
        CompletableFuture.delayedExecutor(wait, TimeUnit.MILLISECONDS).execute(() -> action.get().whenComplete((v, ex) -> {
            if (ex != null) {
                delayed.completeExceptionally(ex);
            } else {
                delayed.complete(null);
            }
        }));
        return delayed;
    }

    private CompletableFuture<Void> fetchBatchWithRetry(List<GeoPoint> batch, Map<GeoPoint, Double> results, int rateLimitRetries, int otherRetries) {
        return this.fetchBatch(batch, results).thenCompose(outcome -> {
            if (outcome == FetchOutcome.SUCCESS) {
                return CompletableFuture.completedFuture(null);
            }
            if (outcome == FetchOutcome.RATE_LIMITED) {
                if (rateLimitRetries >= 5) {
                    this.logger.warning("[NexusTerra] Giving up on a " + batch.size() + "-point elevation batch after repeated 429s. Those points will use base elevation.");
                    return CompletableFuture.completedFuture(null);
                }
                long backoffSeconds = 3L << rateLimitRetries;
                this.logger.info("[NexusTerra] Rate limited - waiting " + backoffSeconds + "s before retrying (" + (rateLimitRetries + 1) + "/5)...");
                CompletableFuture<Void> delayed = new CompletableFuture<>();
                CompletableFuture.delayedExecutor((long)backoffSeconds, (TimeUnit)TimeUnit.SECONDS).execute(() -> this.fetchBatchWithRetry(batch, results, rateLimitRetries + 1, otherRetries).thenRun(() -> delayed.complete(null)));
                return delayed;
            }
            if (otherRetries >= 2) {
                this.logger.warning("[NexusTerra] Giving up on a " + batch.size() + "-point elevation batch after repeated failures. Those points will use base elevation.");
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> delayed = new CompletableFuture<>();
            CompletableFuture.delayedExecutor((long)2L, (TimeUnit)TimeUnit.SECONDS).execute(() -> this.fetchBatchWithRetry(batch, results, rateLimitRetries, otherRetries + 1).thenRun(() -> delayed.complete(null)));
            return delayed;
        });
    }

    private CompletableFuture<FetchOutcome> fetchBatch(List<GeoPoint> batch, Map<GeoPoint, Double> results) {
        JsonArray locations = new JsonArray();
        for (GeoPoint point : batch) {
            JsonObject loc = new JsonObject();
            loc.addProperty("latitude", (Number)Double.valueOf((double)point.lat()));
            loc.addProperty("longitude", (Number)Double.valueOf((double)point.lon()));
            locations.add(loc);
        }
        JsonObject body = new JsonObject();
        body.add("locations", (JsonElement)locations);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create((String)ENDPOINT)).header("Content-Type", "application/json").timeout(Duration.ofSeconds((long)30L)).POST(HttpRequest.BodyPublishers.ofString((String)body.toString())).build();
        return this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).handle((response, throwable) -> {
            if (throwable != null) {
                this.logger.warning("[NexusTerra] Elevation batch request failed: " + throwable.getMessage());
                return FetchOutcome.FAILED;
            }
            if (response.statusCode() == 429) {
                this.logger.warning("[NexusTerra] Elevation API rate-limited us (429).");
                return FetchOutcome.RATE_LIMITED;
            }
            if (response.statusCode() != 200) {
                this.logger.warning("[NexusTerra] Elevation API returned status " + response.statusCode());
                return FetchOutcome.FAILED;
            }
            try {
                JsonObject json = JsonParser.parseString((String)((String)response.body())).getAsJsonObject();
                JsonArray resultsArray = json.getAsJsonArray("results");
                HashMap<GeoPoint, Double> batchResults = new HashMap<>();
                for (int i = 0; i < resultsArray.size() && i < batch.size(); ++i) {
                    JsonObject entry = resultsArray.get(i).getAsJsonObject();
                    double elevation = entry.get("elevation").getAsDouble();
                    batchResults.put(batch.get(i), elevation);
                }
                results.putAll(batchResults);
                this.cache.putElevation(batchResults);
                return FetchOutcome.SUCCESS;
            }
            catch (RuntimeException ex) {
                this.logger.warning("[NexusTerra] Could not parse elevation response: " + ex.getMessage());
                return FetchOutcome.FAILED;
            }
        });
    }

    private List<List<GeoPoint>> partition(List<GeoPoint> points, int size) {
        ArrayList batches = new ArrayList();
        for (int i = 0; i < points.size(); i += size) {
            batches.add(points.subList(i, Math.min((int)(i + size), (int)points.size())));
        }
        return batches;
    }

    private static /* synthetic */ Map lambda$lookup$0(Map results, Void ignored) {
        return results;
    }

    private static enum FetchOutcome {
        SUCCESS,
        RATE_LIMITED,
        FAILED;

    }
}
