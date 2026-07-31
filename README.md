# NexusTerra v0.1.15

## Usage
1. `mvn package`
2. Copy `target/NexusTerra-0.1.15.jar` into your server's `plugins/` folder (delete the old version first).
3. Restart the server.
4. Run `/nexusterra generate <lat> <lon> [radius]` as usual.

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

