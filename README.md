# NexusTerra v0.1.10

## Usage
1. `mvn package`
2. Copy `target/NexusTerra-0.1.10.jar` into your server's `plugins/` folder (delete the old version first).
3. Restart the server.
4. Run `/nexusterra generate <lat> <lon> [radius]` as usual.

## Changed in v0.1.10
- **Fixed diagonal window/pier banding on buildings.** The window rhythm on walls was driven by a
  counter that incremented once per unique voxel column visited while tracing a building's outline.
  On perfectly N/S/E/W walls that lines up with real distance, but on angled or diagonal wall
  segments the voxel staircase visits fewer unique columns per unit of real length than a straight
  wall does, so the counter drifted out of phase with the wall's actual position. That's what showed
  up as the diagonal zigzag striping on the glass building from your Apache Junction screenshot.
  The outline tracer now tracks true accumulated distance along the perimeter instead of a raw visit
  count, so the window/pier pattern stays consistent regardless of wall angle.

## Still open / not addressed this round
- **The large bare dirt field with faint dashed white lines running through it** (also from the
  Apache Junction screenshot) — I have a plausible read (a paved road with dashed centre-line
  markings crossing a farmland/vacant-land landuse polygon, where the road surface itself is just
  low-contrast against the dirt at that zoom level) but I'm not confident enough to "fix" it without
  seeing it up close, since I could easily patch the wrong thing. Send a closer screenshot of that
  specific spot and I'll dig in properly.
- **Overall structure count vs. the real area** — still likely an OpenStreetMap data-coverage gap
  rather than a plugin bug (see previous discussion). Worth confirming on openstreetmap.org for that
  bounding box if you haven't already.
