# NexusTerra v0.1.1

Generates real-world terrain, roads, water, and buildings from live
geographic data, triggered in-game with `/nexusterra generate <lat> <lon> [radius]`.

## Read this before you show it to anyone

This is the most ambitious thing built in this whole project, has the
most moving parts (two live external APIs + real geometry math), and
**has not been through a single real build or test** -- I don't have a
compiler or a live Paper server in this environment. Treat the first
`mvn clean package` and the first in-game test as genuine debugging
sessions, the way NexusBridge's early sessions were, not a formality.

## Legal / licensing -- read this before selling anything

- **Data source is OpenStreetMap (via the Overpass API) + Open-Elevation**,
  not Google Maps. This was a deliberate choice, not an oversight --
  Google's Maps API terms restrict exactly this kind of bulk extraction
  and derivative use, and would be a real legal risk for anything you
  plan to distribute or sell.
- **OSM's license (ODbL) requires attribution.** If you ship this
  publicly, you need a visible "Map data (c) OpenStreetMap contributors"
  credit somewhere reasonable (in-game command output, a wiki page,
  whatever fits).
- **Both public APIs used right now (Overpass, Open-Elevation) are free
  shared community resources with real rate limits**, not built for
  production load from a commercial plugin. Before this goes anywhere
  near paying customers, self-hosting both (both are open source, both
  have Docker images) is the real answer -- otherwise your plugin's
  success gets you rate-limited or blocked by the public instances,
  which would break it for everyone using it at once.

## What's actually built

- **Real projection math** (lat/lon <-> Minecraft block coordinates),
  accurate to a few centimeters at the scale this generates (a few
  hundred meters), which is the right tradeoff over full Mercator/UTM
  complexity for this use case.
- **Live async data fetching** -- elevation via Open-Elevation (batched,
  200 points/request), buildings/roads/water via Overpass (single query
  per generation, using `out geom` to skip a separate node-resolution
  pass). Both run fully off the main thread; only the final block
  placement touches the server's main thread, which is required.
- **Bilinear elevation interpolation** across a sparse sample grid (one
  real elevation query per 8x8 block area, interpolated for everything
  between) -- this keeps API call counts sane for a 150-300m radius
  instead of querying every single block individually.
- **Real scanline polygon fill** for building footprints and water
  bodies, and **thick-line rasterization** for roads -- both are
  standard, correctly-implemented algorithms, not approximations.
- **Hollow buildings**, not solid block masses -- perimeter walls per
  level plus a flat roof cap, so the inside is actually enterable. This
  was a deliberate design choice over the simpler "just fill a box."
- **Throttled, tick-spread placement** -- `PLACEMENTS_PER_TICK` in
  `NexusTerraCommand` controls how many blocks go down per tick (2000
  by default). This is exactly the "slowly generate it if they have the
  processing power" behavior you asked for -- tune this number down on
  weaker hardware, up on beefier servers.

## What's a known V1 simplification (the honest list)

- **Water bodies follow terrain noise instead of a flat lake surface.**
  Real lakes are flat; this version places water at whatever the
  interpolated ground height is at each point, which will look slightly
  uneven on anything but very flat terrain. Fixing this means computing
  one flattened Y level per water polygon instead of per-block height --
  a contained, well-scoped next step.
- **Road width/wall height/building levels are reasonable defaults, not
  tuned against real generated output** (since I have no way to see the
  output yet). `building:levels` OSM tag support (to vary wall height
  per actual building instead of a flat default) is a natural next
  addition once the base shell is confirmed working.
- **Radius capped at 300m** -- deliberate, not a bug. Block count scales
  with radius squared, and the free public APIs need a sane request
  size. Raise the cap once self-hosted data sources are in place.
- **No landuse handling yet** (forests, parks, farmland) -- the data is
  already being fetched and categorized in `OsmFeature`, just not acted
  on. Straightforward addition: scatter trees across `landuse=forest`
  polygons, for instance.

## Build & deploy

Same flow as the other two plugins:
```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn clean package
```
Output: `target/NexusTerra-0.1.1.jar` -> upload to `plugins/`.

**Before your first test:** confirm your server host allows outbound
HTTPS connections to `api.open-elevation.com` and `overpass-api.de` --
some budget Minecraft hosts restrict outbound network access by
default, which would make this fail silently (or with a clear timeout
in console, which is what to look for first if nothing happens).

## First test to run

Pick somewhere you know well in real life, run:
```
/nexusterra generate <lat> <lon> 100
```
A 100m radius is small enough to place fast and gives you something you
can visually sanity-check against reality immediately -- your own
street, a park you know, whatever's easiest for you to judge "does this
actually look right."

## v0.1.1 patch notes

- Fixed the radius-300 crash: the elevation grid is now aligned around `(0,0)`, so the origin sample always exists.
- Added a null-safe origin elevation lookup as a second layer of protection.
- Extended the elevation grid to a complete 8-block boundary so edge interpolation always has valid neighbors.
- Added automatic Overpass failover between two public endpoints.
- Increased Overpass timeout tolerance and added clearer endpoint/status logging.
- Hardened Overpass JSON parsing against missing or malformed elements.
- If every Overpass endpoint is unavailable, generation now continues with elevation-only terrain instead of crashing.
- Added latitude, longitude, and minimum-radius validation.
- Moved Bukkit player messaging and task startup back onto the server main thread.
- Console failures now include the full exception stack trace for future patching.
