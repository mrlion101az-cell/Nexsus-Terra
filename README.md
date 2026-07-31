# NexusTerra v0.1.9

## Usage
1. `mvn package`
2. Copy `target/NexusTerra-0.1.9.jar` into your server's `plugins/` folder (delete the old version first).
3. Restart the server.
4. Run `/nexusterra generate <lat> <lon> [radius]` as usual.

## Changes in v0.1.9
- **Street signs**: named roads (OSM `name` tag) now get a sign post beside the sidewalk showing the street name (truncated to 15 characters, the vanilla sign-line limit). Toggle with `roads.street-signs` in config.yml.
- **Vegetation increase**: tree spacing in parks/gardens/forests/orchards/cemeteries roughly halved for denser coverage, and the random skip chance dropped from 15% to 8%. New ground-cover pass scatters short grass, flowers (dandelion/poppy/blue orchid/oxeye daisy/azure bluet), and ferns across grass and wooded landuse cells that don't get a tree — grass areas were previously bare except for trees.
- **Water features fixed**: this was a real bug, not just missing coverage. Rivers and streams (OSM `waterway` ways) were being run through the same closed-polygon fill used for lakes, which only works for closed shapes — a river is an open line, so the old code produced broken/patchy results or nothing at all. Rivers, streams, canals, and drains/ditches now render as their own line-based water channels with width by type (river widest, drain/ditch narrowest), each with a sand bed. Closed lake/pond polygons (`natural=water`) still use the polygon fill as before, since that part was working correctly.
- **Bridge/ramp slopes**: unchanged from v0.1.8 (already fixed) — bridge decks follow a smoothed height profile instead of a single flat plane.

## Known limitation carried into this version
- General terrain "steppiness" on sloped ground (not bridges) is inherent to a 1-block voxel heightmap — Minecraft has no sub-block terrain, so any block-based generator will show 1-block-high steps where the real-world grade rises. This is different from the bridge issue fixed in v0.1.8, which was a genuine flat-plane bug rather than a grid limitation. If this is still bothering you after testing, let's talk about it specifically — there may be room to smooth road transitions with stairs/slabs even if raw terrain itself can't be sub-divided.
- Building floor-level exterior banding (trim per storey) already existed as of v0.1.5/v0.1.6 — if it's still hard to read which floor is which in your screenshots, flag it with a close-up screenshot and I'll take a look at increasing contrast between storeys rather than assuming it needs to be built from scratch.
