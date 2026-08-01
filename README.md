# NexusTerra v0.1.26

## Usage
1. `mvn package`
2. Copy `target/NexusTerra-0.1.26.jar` into your server's `plugins/` folder (delete the old version first).
3. Restart the server.
4. Run `/nexusterra generate <lat> <lon> [radius]` as usual.

## Changed in v0.1.24 — crosswalks off by default, sills/lintels actually protrude now
Top priority this round, by explicit request: get the white concrete under control, full stop.

- **Crosswalks are now OFF by default.** Found the actual mechanism this time: at a roundabout,
  the overlap zone between the ring road and each connecting street can itself span several
  adjacent cells, not just one point -- with the 8-block clustering used before, that one real
  junction was being detected as many separate "intersections" a few blocks apart, each painting
  its own patch. Widened the clustering to 24 blocks so that collapses back into far fewer
  placements. But three rounds of fixes on this feature is enough signal that the underlying
  "detect an intersection by counting where roads overlap" approach is inherently fragile for
  anything busier than a simple 4-way crossing -- rather than keep asking you to test-and-report
  on it, it's opt-in now (`roads.crosswalks: false`). Turn it on if you want to see it on a simple
  grid of streets; if you see white patches anywhere that isn't an obvious intersection, that's
  this feature, and the fix is turning it back off, not a new bug to report.
  
  **If you already have a config.yml from a previous version**, this default won't reach you
  automatically -- config merging only adds keys that are missing, it never changes a value you
  (or an earlier version) already wrote. Set `roads.crosswalks: false` by hand in your existing
  config.yml to actually turn it off.
- **Window sills and lintels actually protrude now.** Last version's redesign fixed them eating
  the glass, but they were still flush with the wall -- a different-coloured block in the same
  plane, not a real ledge. They now sit one cell outside the wall (the same real per-cell outward
  calculation built for pipes last version), so they read as an actual protruding sill/lintel
  instead of a texture change built into the wall.

## Changed in v0.1.23 — three real bugs, plus real slab-based wall detailing

## Usage
1. `mvn package`
2. Copy `target/NexusTerra-0.1.23.jar` into your server's `plugins/` folder (delete the old version first).
3. Restart the server.
4. Run `/nexusterra generate <lat> <lon> [radius]` as usual.

## Changed in v0.1.23 — three real bugs, plus real slab-based wall detailing
- **The white/yellow line "glitching" at busy junctions and roundabouts, actually fixed this
  time.** Last version's fix deduplicated intersection detection per road, which was necessary but
  not sufficient -- every qualifying road's centre line was still being painted straight through
  the intersection itself, so several roads converging on the same roundabout all painted their
  lines through the same crowded area, overlapping into a mess. Real centre lines stop before a
  junction and pick back up after it; this now does the same, skipping centre-line painting
  entirely on any cell that's a genuine multi-road intersection.
- **Pipes/copper rods now actually mount on the exterior, not embedded in the wall.** They were
  replacing the wall material at its own position, which is flush with the wall, not sticking out
  from it the way a real drainpipe does. Fixed properly: added a real per-cell outward-wall-normal
  calculation (from each point's own local wall tangent, not one single guess for the whole
  building -- pipes run around every side of a building, and a single guess would only be correct
  for one side of it) and used it to offset pipes one block outside the actual wall face. The
  building name signs and foundation landscaping from recent versions were using a cruder
  whole-building direction estimate for the same kind of problem -- both switched over to the same
  real per-cell calculation now that it exists.
- **Window sills stopped eating the glass.** The old sill logic replaced an entire window row with
  a slab -- for the most common storey height, that meant literally half of every window showed
  slab instead of glass, which is what was reading as "the windows are slabs." Redesigned: the sill
  is now a ledge in the spandrel band *below* the window (using space that used to be flat trim),
  and a matching lintel accent sits in the band *above* it -- real slab-based definition around
  each opening, with every window row still showing full glass. This is also a direct answer to
  wanting more slab/block detailing on flat walls generally -- sills and lintels now appear
  automatically on every building with windows, not as a separate opt-in.

If the round glass tower from the screenshots still looks glitched after this, that's likely a
separate issue from these three and I'll need another look at it specifically once you've had a
chance to check the fixes above.

## Changed in v0.1.22 — glass panes gone, and a real bug fix for scattered white concrete

## Usage
1. `mvn package`
2. Copy `target/NexusTerra-0.1.22.jar` into your server's `plugins/` folder (delete the old version first).
3. Restart the server.
4. Run `/nexusterra generate <lat> <lon> [radius]` as usual.

## Changed in v0.1.22 — glass panes gone, and a real bug fix for scattered white concrete
- **Glass panes replaced with real glass blocks.** The only two places glass panes were still used
  (the generic/fallback window material) now use full `GLASS` blocks like every other window
  material in the palette already did.
- **Found and fixed the actual cause of the random white concrete patches.** Traffic lights and
  crosswalks both detect intersections by counting, per grid cell, how many times any road touched
  it -- 2+ was treated as "two different roads cross here." The bug: that count was never
  deduplicated per road. A single curved or multi-segment road's own rasterisation can revisit the
  same cell more than once at a segment joint, and that alone was enough to trip the same "2+
  hits" threshold -- meaning ordinary bends in a single road, not real intersections, could get
  treated as one. Crosswalks paint a real area of blocks, so this is what was showing up as
  patches of white concrete scattered in places that clearly weren't intersections. Now
  deduplicated per road before counting, so only genuinely different roads overlapping a cell can
  ever count toward that threshold. Also shrank the crosswalk footprint a bit further as a second
  layer of defense, independent of the root-cause fix.

## Changed in v0.1.21 — gas stations, storage tanks, aged roads, landscaped parking

## Usage
1. `mvn package`
2. Copy `target/NexusTerra-0.1.21.jar` into your server's `plugins/` folder (delete the old version first).
3. Restart the server.
4. Run `/nexusterra generate <lat> <lon> [radius]` as usual.

## Changed in v0.1.21 — gas stations, storage tanks, aged roads, landscaped parking
Three more screenshots: a landscaped gas station parking lot, an industrial/refinery scene with
storage tanks and rail sidings, a shopping strip with a canopy walkway. "Look at how my roads are
aged. Look at how I made this gas station. Look at my tanks. Look at my parking lot."

- **Gas-station-style canopies and industrial storage tanks are real structures now**, not just
  absent from the output -- OSM tags them as `man_made=canopy` and `man_made=storage_tank`
  respectively, neither of which this plugin queried for or rendered before. Canopies build as
  support pillars around the footprint holding up a flat roof, open on all sides, no walls -- the
  actual gas-station building itself (if separately mapped with `building=yes`) still renders
  through the normal building path. Storage tanks build as a real cylinder using the footprint's
  own shape (real OSM tank footprints already rasterise close enough to circular), banded near the
  base/middle/top, flat white or light-grey top.
- **Aged roads.** A road surface that's perfectly uniform reads as freshly poured. About 8% of any
  given road's cells now get a worn/patched material instead of the base one (gravel patches on
  blacktop, cobblestone patches on residential concrete, and so on) -- scattered by a per-cell hash
  rather than any regular spacing, so it reads as wear, not a repeating pattern.
- **Landscaped parking islands.** Parking lots get periodic grass strips instead of being paved
  edge to edge, and now roll actual trees (sparse, tied to a new parking-specific tree spacing) --
  but only on the island strips themselves, gated so a tree can never appear to grow straight out
  of bare pavement.

## Changed in v0.1.20 — patterns pulled from more hand-built reference shots

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

## v0.1.25 — continuous expansion

New commands:

    /nexusterra expand <lat> <lon> [tileRadiusMeters]
    /nexusterra stop

`expand` starts a spiral of tiles outward from the given coordinate instead of one bounded
single-shot generation. Each tile goes through the exact same generation pipeline and per-tile
radius limit as `/nexusterra generate` (`generation.max-radius-metres`) — this isn't a rewrite of
core generation, just the same request repeated on a spiral of re-centred coordinates, offset to
the correct world-block position each time. One tile is fully cleared and placed before the next
tile's OSM/elevation fetch even starts.

`stop` cancels the run — either between tiles (during the pacing delay) or mid-tile (the in-flight
placement task is cancelled, leaving that tile partially built rather than trying to undo it).

New `config.yml` section:

    expansion:
      max-radius-metres: 10000   # dead-man switch — real distance, not tile count
      max-tiles: 5000            # backstop, independent of the distance cap
      pacing-seconds: 5          # delay between tiles, keeps the free APIs from being hammered
      default-tile-radius-metres: 150

`max-radius-metres` is the actual dead-man switch: it's checked as a straight-line distance in
metres from the origin coordinate to each candidate tile's centre, not a tile count, so it stays
correct regardless of what `tileRadiusMeters` a given run uses. `max-tiles` is a separate hard
backstop in case a very small tile radius would otherwise enumerate an unreasonable tile count
before the distance cap kicks in.

At the default 150m tile radius, a 10,000m run is on the order of 1,400+ tiles. With the default
5s pacing between tiles that's a genuinely long-running task (likely several hours end to end,
not counting per-tile fetch/placement time on top) and a correspondingly large amount of new
world-save data — both expected at this radius, not a sign anything's stuck. Nothing about the
per-tile pipeline changed, so quality and detail stay identical to a normal `generate` run; this
version is purely about sequencing many of them safely.

Only one expansion runs per player at a time — starting a second while one is active gets a
"use /nexusterra stop first" message rather than stacking sessions. If a single tile's OSM/elevation
fetch fails outright (as opposed to just returning sparse data), the whole run stops at that tile
rather than skipping ahead, since a real fetch failure partway through is exactly the kind of
thing worth surfacing rather than silently working around.

Not implemented as part of this pass: resuming an expansion across a server restart (a `/reload`
or crash mid-run currently just stops it — you'd re-run `/nexusterra expand` from the same origin
to continue, and it'll simply regenerate tiles already built), and multiple concurrent expansions
for the same player. Both are addressable later if they turn out to matter in practice.

## v0.1.26 — square tiles + shared elevation reference (expansion seam fixes)

Two real bugs in how `/nexusterra expand` tiles fit together, both fixed at the root rather than
patched over:

**1. Tiles were circular, not square, so adjacent tiles never actually touched.** Every tile —
including a normal single `/nexusterra generate` — has always built inside a *circle* inscribed in
its own square bounding box (`x² + z² <= radius²`), leaving the four corners of that box unbuilt.
`ExpansionManager` spaces tile centres exactly `2 × tileRadius` apart, i.e. bounding-box-adjacent —
so those unbuilt circular corners showed up as real gaps between tiles, not a rendering artifact.
Fixed at the single choke point every feature builder in `TerrainGenerator` already funnels through
(`outsideRadius`, plus two inline duplicate checks in the height/terrain grid setup): it's now a
square bound (`|x| <= radius && |z| <= radius`) instead of a circular one. This changes the
*shape* of every tile — square instead of round — for both `generate` and `expand`; it does not
touch feature logic itself. Adjacent expansion tiles now share a full edge with a 1-block overlap
column (an intentional side effect of the existing `2 × tileRadius` spacing against a now-square
footprint), so there's no seam gap to fall into.

**2. Each tile was zeroing its own height to its own centre's real elevation.** `TerrainGenerator`
has always measured every block's height relative to the real-world elevation sampled at that
generation's own centre point, then added the (fixed) world Y baseline on top. For one `generate`
call that's exactly right. But `expand` was calling `generate` fresh per tile, each with its *own*
centre — so two adjacent tiles whose real-world centres sit at even slightly different elevations
ended up with different local "zero points," and the terrain between them stepped up, down, or
gapped at the seam even though the real-world terrain there is continuous. Fixed with a new optional
`baseElevationOverride` parameter on `TerrainGenerator.generate` (existing single-shot `generate`
callers are unaffected — the override defaults to null, preserving the old per-tile-centre
behaviour): `ExpansionManager` now captures the real elevation actually used by the very first tile
(the expansion's own origin coordinate) and passes that same value into every later tile in the run,
so the whole expansion sits on one shared absolute elevation reference instead of each tile
re-zeroing itself. `GenerationResult` gained a `baseElevation()` field to carry that value back out.

One known residual imperfection, not fixed in this pass: the per-tile elevation smoothing pass
(the 3×3 blur that turns jagged block-by-block rounding into a natural-looking slope) still runs
independently per tile, using only that tile's own sample grid as blur input. Right at a shared
edge, a tile has no visibility into its neighbour's samples one step further out, so the blur can
land a block or so differently than its neighbour did on the same seam — a small local texture
difference, not the metres-scale gap/mismatch this version actually fixes. Flagging it rather than
guessing at a fix blind, since it'd need either overlapping sample fetches between tiles or a
second cross-tile smoothing pass, and it's a much smaller problem than the two above.

