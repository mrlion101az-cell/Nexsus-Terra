# NexusTerra v0.1.3 patch notes

## What the log showed

Real testing against `35.6895 139.6917 300` (central Tokyo, a 300m
radius) showed v0.1.2's fix was incomplete:

- The 4-way concurrent elevation batching added in v0.1.2 immediately
  tripped Open-Elevation's rate limiter -- nearly every batch came
  back `429 Too Many Requests`.
- The single flat 2-second retry wasn't nearly enough of a backoff to
  recover from a rate limit (as opposed to a one-off network blip,
  which is what that retry was actually designed for), so batches
  kept failing repeatedly.
- Overpass, on the other hand, worked exactly as designed: mirror 1
  returned a 504, and the client correctly fell through to mirror 2
  rather than giving up. No change needed there.

## What changed

- **Elevation dispatch is back to one request in flight at a time**
  (`REQUEST_CONCURRENCY = 1`). The concurrency added in v0.1.2 was the
  wrong lever to pull for a rate-limited API -- more parallelism just
  means more requests hit the limiter in the same window.
- **Proactive pacing**: a minimum 1.2s gap is now enforced between the
  *start* of consecutive requests, whether or not the previous one
  failed. This means we mostly stop tripping the rate limit in the
  first place, instead of just reacting to it after the fact.
- **429s get their own retry budget with real exponential backoff**:
  3s, 6s, 12s, 24s, 48s across up to 5 attempts, separate from the
  generic 2-retry/2s path used for plain network errors or bad
  responses. A rate limit needs the request rate to actually drop for
  a while, not a quick retry.
- Console logging now says plainly when it's giving up on a batch
  after exhausting retries, so a partial/imperfect result is visible
  rather than silently blending into "normal" output.

## The honest tradeoff here

This makes large-radius generation **slower but far more likely to
actually finish successfully**, which is the right tradeoff for a free
rate-limited API -- there's no way to have both "many points fetched
in parallel" and "stop getting 429'd" against the same shared public
instance. A 300m-radius request (~5,900 points, ~30 batches) will now
take roughly 30 * 1.2s = ~36s minimum even in the best case, more if
any batches hit a real rate-limit backoff.

If that's still too slow for how you want to use this day to day, the
cache from v0.1.2 means it's a one-time cost per area -- but the real
fix for consistently fast generation across many different areas is
still self-hosting Open-Elevation, which removes the rate limit
entirely since it'd be your own server. Happy to help set that up
when you're ready to go there.
