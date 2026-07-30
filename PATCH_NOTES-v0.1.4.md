# NexusTerra v0.1.4 patch notes

## What the screenshot showed

Pathways and water were generating correctly, but the buildings were
one indistinguishable grey mass. Looking at the generation code, that
tracked exactly: every single building, regardless of what it actually
was in the real world, got the same fixed 6-block wall height and the
same STONE_BRICKS material with no windows, doors, or roof variation.
A whole city block of different buildings all rendering identically is
exactly what produces "one big grey slab" instead of a recognizable
skyline.

## What changed

- **Real building heights.** `BuildingStyle` now reads a building's
  `height` or `building:levels` OSM tag (most buildings in reasonably
  mapped areas have at least one of these) and uses that instead of a
  flat default -- so buildings actually vary in height like a real
  skyline, clamped to a sane 3-45 block range.
- **Material varies by building type.** Houses, apartments, offices,
  industrial buildings, garages, and religious/civic buildings each
  get a different wall and roof material (mud bricks, brick, quartz,
  cobbled deepslate, stripped wood, calcite/sandstone, etc.), plus an
  explicit `building:material` or `roof:colour` tag overrides the
  type-based guess when present.
- **Windows and a door.** A simple periodic pattern cuts glass
  openings into upper-level wall columns (never the ground floor, so
  it still reads as a wall at eye level) and a door-sized gap at each
  building's entrance point. Not real per-building window layouts, but
  enough that walls stop reading as flat colored slabs.
- **Roads vary by type** instead of uniform gravel: blackstone for
  motorways/primary roads, gray concrete for residential streets,
  coarse dirt for service tracks, smooth stone slabs for footpaths.
- **Water is flattened properly.** Previously followed per-column
  terrain noise, producing a stair-stepped mess. Now each water
  feature gets one flat surface height (the lowest terrain point under
  its footprint, like a real lake sitting in a basin), with a sand
  bed underneath.

## Still worth knowing

This is still block-grid rasterization, not architectural generation
-- roofs are flat caps (no pitched/gabled roofs yet), window placement
is a fixed pattern rather than reading actual window positions from
OSM (most buildings don't have that level of detail mapped anyway),
and adjacent touching buildings will still visually merge at their
shared wall since each is drawn independently. Worth tackling next if
the height/material/window pass isn't enough on its own once you see
it rendered: pitched roofs for residential buildings, and a minimum
gap enforced between adjacent building footprints so rows of buildings
read as separate structures even when their real-world footprints
technically touch.
