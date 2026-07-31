# NexusTerra v0.1.20

## Usage
1. `mvn package`
2. Copy `target/NexusTerra-0.1.20.jar` into your server's `plugins/` folder (delete the old version first).
3. Restart the server.
4. Run `/nexusterra generate <lat> <lon> [radius]` as usual.

## Changed in v0.1.20 — patterns pulled from more hand-built reference shots
More screenshots of hand-built work: a brick appliance store with real signage and chain-link
roofline fencing, an ornate brick building with a castle-like roofline, a modern glass storefront
with a striped awning by a canal, foundation landscaping around building bases, double-yellow
center lines on wider roads.

- **Building name signs.** Any building with a `name` tag in OSM now gets a real wall-mounted sign
  with its actual name on the facade, using the same sign-with-text mechanism already proven for
  street name signs. Placed one cell outside the wall face (not replacing the wall itself), backed
  by the real wall block behind it, with the outward direction estimated from the building's own
  centroid -- there's no true wall-normal geometry computed in this pipeline, so this is a
  reasonable approximation for a normal (mostly convex) building shape, not a guarantee for every
  possible footprint.
- **Roofline cresting**, replacing a flat trim band that read as unfinished: industrial/warehouse/
  commercial/retail buildings get a chain-link fence course around the roof edge; civic/religious
  buildings (church, cathedral, school, government, etc.) get a crenellated, castle-like up-down
  parapet instead.
- **Foundation landscaping** -- a handful of flower/bush clusters right outside a building's own
  walls, not just scattered through parks. Never placed on a road, bridge, or sidewalk cell.
- **Double yellow centre lines** on motorway/trunk/primary/secondary roads (the same tier that
  already gets the darker blacktop material), instead of the dashed white marking used for lesser
  roads -- a solid line, not literally two parallel lines, since a true double line would need a
  real perpendicular road-width offset that isn't available this cheaply; still a real, correct,
  and much more recognizable distinction from the dashed residential-street marking.

Same honesty as last time about what's not attempted: the awning-and-canal patio scene and the
specific arched/pointed window shapes from the gothic building aren't generalizable the way the
above are -- true arched windows would need per-window-boundary geometry this pipeline doesn't
track (it classifies window cells one at a time, not window openings as a whole), and getting that
wrong would look worse than the current plain rectangular cutouts.

## Changed in v0.1.19 — closing the gap toward hand-built detail

## Usage
1. `mvn package`
2. Copy `target/NexusTerra-0.1.19.jar` into your server's `plugins/` folder (delete the old version first).
3. Restart the server.
4. Run `/nexusterra generate <lat> <lon> [radius]` as usual.

## Changed in v0.1.19 — closing the gap toward hand-built detail
Prompted by screenshots of hand-built interiors and exteriors (a hotel lobby, a kitchen, a rooftop
with detailed AC units, a street with proper street lights and crosswalks, a checkered plaza).
Full parity with bespoke hand-building isn't something procedural generation can chase -- a
specific reception desk layout, a modeled delivery truck, an exact loading dock aren't
generalizable -- but a lot of what actually makes those builds read as "detailed" is a pattern,
not a one-off, and those patterns are now in the generator:

- **Checkered floors and rugs, everywhere an interior has one.** This alone is most of what
  separates "someone furnished this" from "a flat colored rectangle." Real two-tone tile/rug
  pairings per building category (dark/light wood for houses, warm-tone carpet pairs for
  residential, cool-tone pairs for office/civic, red/purple for religious buildings) instead of
  one flat material.
- **A real 3-piece couch** (two facing stairs flanking a slab seat, plus a low coffee table) in
  the living-room rotation, replacing what used to be a single stray stair block.
- **Hanging pendant lights** -- a chain dropping from the ceiling to a lit block (shroomlight or
  glowstone), on every furnished floor. Position is checked against the actual interior footprint
  first and falls back safely on small/irregular buildings instead of risking a light embedded in
  a wall.
- **Potted plants on a proper pedestal** (a slab base under a small leaf column) instead of a
  single flowerpot block, which reads as too small next to full-height furniture -- used as a
  bedroom accent for now.
- **Crosswalks** at genuine multi-way intersections (reusing the same detection already built for
  traffic lights). Being upfront about a real limitation: the actual direction of travel isn't
  known at that point in the code, so stripe orientation is a best guess, not computed from real
  road geometry -- but painting is strictly confined to cells already confirmed to be road, so a
  wrong guess can never put a stripe somewhere that isn't a road.
- **Checkered plaza paving** -- 2x2 brick tiles alternating with the base surface on
  park/plaza/square landuse areas, instead of one flat material.
- **A real street light silhouette** -- pole, horizontal cross-arm, light hanging at the end of
  the arm -- instead of a light sitting directly on top of a pole like a torch.
- **Sidewalk planters** -- small wall-rimmed flower boxes, spaced independently of both benches
  and lamp posts so the three don't all land on the same cells.
- **Quartz-capped AC units** and **palm trees** (a distinct thin-trunk, flat-frond silhouette, not
  just a recolored round tree -- rolled in as stylistic variety since this plugin has no climate
  data to place them geographically correctly, which is worth knowing).

Deliberately left out, and why: a reception desk, specific kitchen-appliance replicas, a modeled
loading dock or vehicle, and real per-road-direction crosswalk orientation. Each of those needs
either real per-scene composition judgment or geometry this plugin doesn't currently compute --
folding in a half-working version of any of them would look worse than not attempting it, the same
principle behind not attempting full interior room subdivision in v0.1.16.

## Changed in v0.1.18

## Usage
1. `mvn package`
2. Copy `target/NexusTerra-0.1.18.jar` into your server's `plugins/` folder (delete the old version first).
3. Restart the server.
4. Run `/nexusterra generate <lat> <lon> [radius]` as usual.

## Changed in v0.1.18 — natural slopes instead of jagged terracing
A screenshot showed grass terrain stepping in small, sharp cliffs instead of a smooth hillside.
Root cause: elevation data is smoothly interpolated between real sample points, but each block
column rounds that to its own integer height completely independently -- there was nothing
connecting a column to its neighbours, so any local rounding noise showed up as a visible step.
This likely got more noticeable after the v0.1.17 radius work, since a coarser elevation-sample-
step at larger radii means more distance between real sample points for that per-column rounding
noise to accumulate over.

Two changes, aimed at both the symptom and a likely contributor:
- **A real smoothing pass, not a bigger radius change.** The raw elevation grid now gets a mild
  blur (two passes of a 3x3 weighted average) before it's rounded into block heights. Strong
  enough to remove single-block stair-stepping between neighbouring columns; gentle enough that an
  actual hill spanning dozens of blocks is essentially unaffected -- this smooths out rounding
  noise, not real terrain. Every other feature that reads ground height (roads, sidewalks,
  buildings, bridges, trees, everything) now reads the same smoothed values instead of the raw
  ones, so nothing ends up floating or sunken relative to the terrain around it.
- **Softer elevation-step scaling.** Last version's radius-based coarsening jumped between fixed
  tiers (8 → 12 → 16 → 24 → 32 at hard 300/600/1000/1500m breakpoints) -- a hard jump right at a
  specific radius would itself have been a visible discontinuity in terrain quality between two
  generations a few metres apart. It now scales continuously (roughly radius/60, capped at 32),
  which also means less aggressive coarsening in the 300-600m range specifically, where it was
  probably contributing to what showed up in the screenshot.

Small bonus while in this code: exposed rock faces (anywhere terrain height changes sharply enough
to show the sub-surface stone) now mix in a little andesite/diorite/cobblestone instead of being
uniform stone -- subtle, but a perfectly uniform cliff face reads as artificial in a way real
exposed rock doesn't.

## Changed in v0.1.17 — a real 1000m radius, service lines, rooftop details, more vegetation
- **Max radius raised to 1000m** (`generation.max-radius-metres`), from 300m. This wasn't just a
  number change: at a fixed elevation-sample-step, holding the same fine sampling resolution at
  1000m would have meant roughly 11x as many Open-Elevation API points as 300m needed, which with
  this plugin's existing rate-limit pacing would have made a 1000m generation take a genuinely long
  time. The elevation step now automatically coarsens as the requested radius grows (12/16/24/32
  past 300/600/1000/1500m) so a 1000m generation's elevation fetch stays in roughly the same time
  ballpark 300m used to be. The actual block-placement phase (terrain + roads + buildings) still
  scales with area and will take longer -- that part is genuinely more work, not a bug -- raise
  `placements-per-tick`/`clear-checks-per-tick` if you want it to finish faster at the cost of more
  per-tick load. Also raised the Overpass query's own timeout budget and the HTTP client timeout to
  give a 2km+ bounding box more headroom; very dense city cores may still push against what the free
  public Overpass endpoints can handle at 1000m -- if a huge generation in a very dense downtown
  comes back empty, try a smaller radius for that specific spot.
- **Drainpipes/service lines.** A vertical seam down the wall face on most buildings -- iron bars on
  some, a thin copper rod line on others (`LIGHTNING_ROD` stacked vertically reads as a continuous
  thin rod in copper's colour; there's no literal "copper bar" block in vanilla). Picked per building
  from a hash, so it's consistent for that building but varies street to street, and about a quarter
  of buildings get none at all. Never overrides a window, trim band, storefront, or the door gap.
- **Skylights.** Small glass patches let into flat roofs themselves (not raised like the HVAC units),
  gated so not every roof gets one.
- **AC units got real material variety** -- iron, light gray concrete, or gray concrete instead of
  always iron block.
- **Copper lightning rods on roofs**, not just a metaphor: tall buildings' existing antenna mast now
  ends in an actual `LIGHTNING_ROD` cap, and about a third of shorter buildings (which don't get the
  tall mast) get a small standalone one instead, so copper rod details show up across a range of
  building heights, not just towers.
- **Vegetation.** Flower palette roughly doubled (added cornflower, allium, lily of the valley, and
  four tulip colours to the existing five). More importantly: a real bush/shrub is now a genuine
  possible outcome of undergrowth generation, not just flat ground cover -- small irregular leaf
  clumps (oak or azalea) or, about a quarter of the time, an actual sweet berry bush block. This is
  a small, contained slice of "vegetation overhaul," not a full rework of every plant system; ground
  cover, tree placement/spacing, and landuse-driven surfaces are unchanged from before.

## Changed in v0.1.16 — interiors: real floors and basic furnishing
- **Structural floors at every storey.** Buildings were hollow shafts before this -- exterior walls
  and a roof, nothing dividing the levels inside. Every storey above the ground floor now gets an
  actual floor slab across the building's footprint (wood for houses/apartments, concrete for
  offices/civic, stone for churches), capped at 60 floors so an unusually tall or tightly-storeyed
  tower can't runaway-generate hundreds of them.
- **Carpet rugs.** A patch of colored carpet near the centre of each floor -- warm tones (red/orange/
  brown/green) for residential, cool tones (light gray/cyan/blue/gray) for office/civic, red for
  religious buildings. This is genuinely new use of the carpet block family, not a recolor of
  something that already existed.
- **Basic per-floor furniture, catered to building category.** One small, safe furniture cluster per
  floor -- not full rooms with interior walls (see the note below on why), but real, recognizable
  pieces: houses and apartments cycle kitchen (smoker/crafting table/barrel) → bedroom (a correctly
  oriented two-block bed, plus a chest) → living room (bookshelf + a stair "seat") as you go up
  floors; offices/schools/hospitals/civic buildings get a lectern and bookshelves; commercial
  buildings get a stockroom (barrels + a chest); industrial gets a workshop (smithing table +
  furnace); churches/cathedrals/mosques/temples get a small lectern-and-candle altar.
- **Beds actually work correctly now.** Needed a bit more plumbing: a bed is two blocks that share a
  facing direction but need their head/foot explicitly set, which `BlockPlacement` had no way to
  express before (it could set a material, and as of the last update, a facing direction). It can now
  carry that too, so beds place as one real, correctly-oriented object instead of two disconnected
  colored blocks.
- **More copper.** Residential/apartment roofs can now roll weathered copper, commercial/office roofs
  can roll exposed copper, and hotel roofs got an extra cut-copper option -- copper was previously
  only in the church/industrial/hotel palettes.

**Why no interior walls or real room layout:** subdividing an arbitrary building footprint into
rooms, placing walls and doors between them, and furnishing each room individually is a much bigger
problem than what's above, and getting it wrong (a wall through a doorway, furniture wedged against
a window) would look worse than a simpler, safer approach. Furniture is placed once near the centre
of each floor's open footprint instead, which stays clear of exterior walls for any reasonably-shaped
building without needing to know where interior walls would even go. This isn't a claim that real
buildings only have one piece of furniture per floor -- it's a deliberately bounded first pass. If
it looks good in practice, true room subdivision (walls + doors + multiple furnished rooms per
floor) is a reasonable next step, but it's a meaningfully bigger and riskier undertaking than
everything else in this update, so I didn't fold it in blind.

## Changed in v0.1.15 — street furniture, real trees, arches, and buildings that sit properly on hills
- **Benches — new.** Placed along sidewalks the same way lamp posts are, on their own spacing so
  they don't always line up with a lamp. Built from real stair blocks with correct facing (a small
  plumbing addition: `BlockPlacement` can now carry orientation, not just a material, so stairs
  actually face the right way instead of Bukkit's default). Rolls between oak/spruce/dark oak/stone.
- **Fountains — new.** A small circular basin with a raised rim and a light at the centre, placed
  once at the centroid of any sufficiently large park/plaza/square, not scattered everywhere.
- **Bigger, more varied trees.** Added cherry and jungle to the species roster (5 → 7). About 1 in 25
  trees now rolls as a landmark-scale giant instead — a thick 2x2 trunk and a wide, domed, multi-layer
  canopy that towers over the street trees around it, the way a real old neighbourhood has the
  occasional huge tree that's clearly much older than everything nearby.
- **Real arches under bridges.** Previously, tall bridges were held up by bare single-block pillars
  with open air between them. Wherever two adjacent pillars have enough clearance underneath, the
  span between them is now filled with an actual stone arch — a true semicircle, springing just
  above the ground and closing just under the deck — instead of open space. Short spans or spans
  without much headroom are left as plain pillars; there's no attempt to force an arch shape in
  where it wouldn't read as one.
- **Buildings on sloped lots no longer float or bury themselves.** Previously a building's floor
  height came from a single arbitrary corner of its footprint, so on any real slope part of the
  building would clip into the hillside and part would hang in open air. The floor now sits at the
  lowest point across the whole footprint, and anywhere the real terrain doesn't match that level
  gets filled in with the building's own trim material — a retaining wall on the uphill side, a
  filled footing on the downhill side — the way a real building on a slope actually sits on a
  levelled pad, not on raw unmodified terrain.
- **Plazas and squares tagged the way OSM actually tags them.** The real-world tagging for a paved
  plaza/square (`highway=pedestrian` + `area=yes` — the tag used for places like Trafalgar Square)
  was being treated as a linear road, not a walkable area, so it never got the plaza surface
  treatment and never would have triggered the new fountain. It's now routed into the same pipeline
  as `landuse=pedestrian`.

This is a direct answer to "benches, street lights, trees, custom structures, nice slopes, nice
arches" — street lights, telephone poles, and traffic lights already existed before this pass;
everything above is what was actually missing.

## A concrete example to test against
Trafalgar Square, London is a good one-shot test for most of this update in a single 300m-radius
generation: it's tagged `highway=pedestrian`/`area=yes` in OSM (verified this is the standard
real-world tagging, and fixed the classifier to actually route it to the plaza pipeline rather than
assume it already worked) — so it should now get the plaza surface and trigger the new fountain,
dense building fronts with real `building:levels`/`roof:shape` tags around the square, and named
sidewalks (Charing Cross Road, The Strand) for benches/lamps/signs. Run:

    /nexusterra generate 51.5080 -0.1281 250

For the *slope* fix and arch bridges specifically, San Francisco's California Street through Nob
Hill is a real hill with real building lots on it, and the cable car line running up it is tagged
as a railway in OSM:

    /nexusterra generate 37.7913 -122.4110 200

I can't run either of these myself — this all requires a live Paper server and world, which I don't
have access to. Both are picked because they're OSM-data-rich (real tags, not sparse rural data) and
each stresses different parts of this update; run whichever's more useful and let me know what comes
out wrong, since I can't verify the visual result without you looking at it in-game.

