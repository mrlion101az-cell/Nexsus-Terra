# NexusTerra v0.1.2 patch notes

## What was going wrong

1. **Elevation lookups ran fully sequentially with no retry.** For a
   300m-radius request that's roughly 5,700 sample points / 200 per
   batch = ~29 HTTP round trips chained one after another
   (`ElevationClient.lookup`), each able to take up to 30s. Against the
   free public Open-Elevation instance (a shared, sometimes-overloaded
   resource) this could realistically take 10+ minutes, and any single
   dropped batch just silently defaulted those points to base elevation
   with no retry and no visible error. This was almost certainly the
   main "not fast enough" bottleneck.

2. **Overpass can return HTTP 200 while having actually timed out.**
   `OverpassClient` only checked `statusCode() != 200`. Overpass
   frequently embeds a `"remark"` field describing a server-side
   timeout in an otherwise-200 response, which the old code never
   inspected -- so a timed-out query looked like "0 buildings found
   here" instead of triggering a retry or a fallback to the second
   mirror. This is the most likely explanation for structures
   specifically failing while flat terrain (which only needs
   elevation) still worked.

3. **Nothing was cached.** Every `/nexusterra generate` call re-hit
   both public APIs from scratch, even for the exact same spot,
   multiplying load and wait time on every retry during testing.

## What changed

- **New `GeoCache`** (`geo/GeoCache.java`): disk-backed cache under
  `plugins/NexusTerra/cache/`. Elevation is cached by exact
  coordinate (the projection math is deterministic, so repeat runs at
  the same origin hit the cache directly, no rounding needed).
  Overpass results are cached by bounding box. A repeat generate at
  the same spot is now near-instant.
- **`ElevationClient`**: only fetches points not already cached; runs
  up to 4 batches concurrently instead of one at a time; retries a
  failed batch once after a 2s backoff; reports progress via an
  optional callback.
- **`OverpassClient`**: detects the `remark` timeout field and falls
  back to the second mirror instead of silently accepting an empty
  result; raised the query's internal timeout budget (45s -> 55s) and
  the HTTP timeout (60s -> 70s) to give genuinely large/dense-area
  queries more room to actually finish instead of getting cut off.
- **`/nexusterra generate`**: now shows a live action-bar percentage
  while elevation data comes in, so it's clear it's working rather
  than looking stuck.

## Still worth knowing

Both APIs remain the free public community instances -- this patch
makes them fail loudly and retry sensibly instead of failing silently,
and caching means you stop re-paying the cost on every test run, but
it doesn't remove the fact that they're shared, rate-limited services.
If generation is still too slow/unreliable for regular use once this
patch is in, self-hosting Overpass and Open-Elevation (both are open
source, both have Docker images) is the real long-term fix -- happy to
help wire that up if/when you want to go there.
