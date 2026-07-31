# NexusTerra v0.1.8

## What's in this release
- **NexusTerra-0_1_8.jar** — the compiled plugin. Drop this into your server's `plugins/` folder (replacing the old NexusTerra jar) and restart.
- **NexusTerra-0.1.8-source.zip** — the full Java source tree, in case you want to keep a copy for future edits.

## Changes in v0.1.8
- **Bridges/ramps**: decks now follow a smoothed height profile along the road instead of sitting at one flat height for the whole way. Ramps taper down to street level instead of ending abruptly in mid-air.
- **Trees**: five species now spawn (oak, birch, spruce, dark oak, acacia) with three canopy shapes (round, conical, umbrella) and varied sizes. Placement is jittered and partially randomized instead of sitting on a rigid grid.

## Usage
1. Stop your server (or just replace the jar while it's off).
2. Copy `NexusTerra-0_1_8.jar` into `plugins/`.
3. Delete the old `NexusTerra-*.jar` if it's still there, so there's only one version loaded.
4. Start the server.
5. Run `/nexusterra generate <lat> <lon> [radius]` as before.
