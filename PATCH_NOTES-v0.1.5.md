# NexusTerra v0.1.5 patch notes

## What the Columbus Circle test render exposed

The v0.1.4 test at `40.7677 -73.9807 250` produced buildings covered in
vertical glass striping, all at what looked like the same height, with
pre-existing trees punching through the roads. Three real bugs, not
just tuning problems:

### 1. `thickLine` was inflating every wall to 3 blocks thick

`int half = Math.max(1, width / 2);` -- for `width = 1` (which is what
building walls requested) that evaluates to `max(1, 0) = 1`, so the
method stamped a **3x3 square** at every step along the line. Walls
came out three blocks thick, and every odd width was off by one.

### 2. The window pattern was indexing into a list full of duplicates

Worse, because `thickLine` marked a 3x3 square per step, it returned
the *same block coordinate roughly nine times*. The v0.1.4 window
check was `perimeterIndex % 4 == 0`, using the raw list index -- so
instead of walking along the wall, the pattern cycled through those
duplicates. That is precisely the vertical glass striping in the
screenshot; it was never placing windows at all.

### 3. Every Manhattan building hit the height ceiling

`MAX_WALL_HEIGHT` was 45. Real buildings around Columbus Circle are
100-180m, so essentially all of them clamped to exactly 45 -- meaning
the "real heights from OSM tags" feature added in v0.1.4 still
produced a completely uniform skyline.

### Also found while in there

Base terrain columns placed stone at `height - 4` and dirt at
`height - 1`, with **nothing at `height - 3` or `height - 2`** -- two
air gaps inside every single ground column in the entire generated
area.

## What changed

**Geometry correctness**
- `thickLine` now produces exactly the requested width (1 means 1) and
  deduplicates its output.
- New `PolygonRasterizer.outline()` returns an ordered single-block
  perimeter walk where each block carries a true along-path index --
  this is what makes evenly spaced wall features actually possible.
- All placements are now deduplicated by coordinate with last-write-wins,
  which both fixes layering between overlapping features and cuts the
  total block count substantially.
- Terrain columns are filled solid.

**Building detail**
- Height ceiling raised to 140 and now additionally clamped against the
  world's real build limit at generation time.
- Per-storey trim bands: every floor line gets a contrasting material,
  so a tall wall reads as having storeys instead of being one flat
  expanse.
- Real windows: a 2-wide opening every 5 blocks along the wall,
  occupying the middle rows of each storey -- not the ground floor, not
  the roofline.
- Window material varies by building type (light blue for
  office/commercial, purple for religious, plain panes for houses).
- Storey height varies by type: commercial floors are taller than
  residential ones, church/temple taller still.
- Interior ground floor is laid so buildings aren't open to bare grass.
- Doorway moved off the corner (index 0 always landed on one) and
  widened to 2x2.
- Flat roofs get a parapet lip around the edge.

**Roads**
- Sidewalks (smooth stone) laid alongside roads 4+ blocks wide, in a
  separate pass so adjacent roads can't pave over each other.
- Dashed white centre markings on roads 7+ blocks wide.

**Site clearing (new)**
- `BlockPlacementTask` now runs a clearing phase before placing
  anything, sweeping away pre-existing trees and terrain up to 24
  blocks above the new ground level. This is why trees were growing
  through the roads. Clearing is driven by a compact height map rather
  than millions of AIR placements, so it costs about a megabyte of
  memory instead of hundreds.

## Still not done

Roofs are still flat -- no pitched or gabled roofs for residential
buildings yet, which is the most obvious remaining gap for anywhere
that isn't a downtown core. Adjacent buildings whose real footprints
touch will still visually merge into one mass, since each is
rasterized independently. And there's no street furniture at all yet
(lamps, benches, signage), which is a big part of what makes a
generated street feel inhabited rather than empty.
