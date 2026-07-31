# NexusTerra v0.1.12

## Usage
1. `mvn package`
2. Copy `target/NexusTerra-0.1.12.jar` into your server's `plugins/` folder (delete the old version first).
3. Restart the server.
4. Run `/nexusterra generate <lat> <lon> [radius]` as usual.

## Changed in v0.1.12
- **Fixed small gaps in building roofs/floors.** The polygon scanline fill (used for building
  interiors/floors, flat roofs, and lake polygons) could leave a one-cell gap on certain rows when a
  building's real-world footprint had a vertex sitting close to a scanline. Two edges meeting at that
  vertex would both register a crossing at nearly the same X, leaving an odd crossing count for that
  row — the fill loop pairs crossings two at a time, so the odd one out got silently dropped instead
  of filled. This is what was showing up as small dark holes in a few rooftops in your Hawthorne
  screenshot. Near-duplicate crossings are now merged before pairing.

## Related, but not something I "fixed" (setting expectations honestly)
- **Mobs spawning and burning inside buildings.** Buildings are hollow shells inside (a floor, walls,
  a roof cap — no dividers between storeys), so they're dark enough for hostile mobs to spawn in, same
  as any large enclosed dark room in vanilla Minecraft. The roof-hole bug above was letting daylight
  leak into some of those interiors and cook whatever had spawned there, which is what you saw burning
  in the screenshot. Fixing the hole stops that specific daylight leak, but mobs can still spawn inside
  dark interiors generally — that's standard Minecraft behavior for any unlit enclosed space, not a
  generation bug. If it's a problem for you, the practical fixes are server-side (mob-spawning
  gamerules, or lighting placed by hand/plugin after generation) rather than something to patch in the
  terrain generator itself. Happy to talk through options if you want to go there.
