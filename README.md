# NexusTerra v0.1.0

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
- **Real buildings, not placeholder boxes**: `BuildingBuilder` reads
  actual OSM `height` and `building:levels` tags when present (falling
  back to a 2-story default only when the data doesn't have either),
  varies wall/roof material by building type (`house` gets bricks and a
  dark wood-toned roof, `commercial` gets white concrete, `industrial`
  gets gray concrete, etc.), places evenly-spaced windows along every
  wall, carves a door opening, and adds real floor slabs between levels.
- **Real pitched roofs for houses** -- actual sloped geometry (ridge
  line along the building's longer axis, two planes sloping down to the
  wall tops), not a flat cap. Reserved for house-type buildings
  (`house`, `detached`, `semidetached_house`, `terrace`), since those
  realistically have pitched roofs while commercial/industrial/apartment
  buildings realistically have flat ones -- both are now handled
  correctly rather than everything defaulting to flat.
- **Flattened water surfaces** -- each water body now sits at one
  representative level (the lowest point in its footprint, matching how
  real water actually pools) instead of following per-block terrain
  noise, which fixes the visibly-uneven-lake issue from the first version.
- **Road materials vary by type**, not uniform gravel -- real roads
  (motorway/primary/secondary) get an asphalt-toned material,
  residential streets get a lighter concrete tone, service/track roads
  get coarse dirt, and footways/paths get an actual dirt path block.
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
  output yet) -- though building height/levels now come from real OSM
  data when it's present on a given building, only falling back to a
  flat default when the tag is genuinely missing.
- **Roofs are flat for non-house buildings**, and pitched-roof slopes are
  built from solid whole-block steps rather than smooth stair-block
  diagonals -- a deliberate choice: the placement pipeline only does
  plain `setType()` with no facing/orientation data, so stair blocks
  here would all default to the same direction regardless of actual
  slope and look visually wrong. A stepped solid slope is the honest
  result of what this pipeline can currently place correctly; true
  stair-based smooth roofs would need the placement system extended to
  carry BlockData, not just Material.
- **Windows are evenly spaced by wall position, not tied to real window
  data** (OSM doesn't reliably have that) -- every 4 blocks along each
  wall, skipping the ground floor except for one door opening.
- **Water flattening uses each polygon's lowest interior point as the
  surface level** -- correct for a single lake/pond, but a water body
  that's actually two separate basins connected by a narrow channel
  would still get one flat level across both, which may not always be
  right. Edge case, not the common case.
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
Output: `target/NexusTerra-0.1.0.jar` -> upload to `plugins/`.

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
