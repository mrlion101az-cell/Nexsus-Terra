# NexusTerra v0.1.11

## Usage
1. `mvn package`
2. Copy `target/NexusTerra-0.1.11.jar` into your server's `plugins/` folder (delete the old version first).
3. Restart the server.
4. Run `/nexusterra generate <lat> <lon> [radius]` as usual.

## Changed in v0.1.11
- **Utility/telephone poles**: new. Wooden poles with a crossbar along sidewalks, spaced further
  apart than lamp posts (`roads.telephone-poles`, `roads.telephone-pole-spacing` in config.yml,
  default every 20 blocks).
- **Traffic lights**: new. Placed at genuine multi-way intersections — detected by tracking how many
  *different* road features actually cover the same cell (2+ means two separate roads cross there,
  not just a single road's own width). Lights are anchored to a nearby sidewalk cell so they sit off
  the driving surface, not in the middle of the road. This is a heuristic like the street-sign
  placement, not a full traffic-engineering model — expect one signal cluster per real intersection,
  not per lane approach. Toggle with `roads.traffic-lights`.
- **House colour variety**: individual houses (house/detached/semidetached/bungalow/cabin) now pick
  from a small palette of wall materials (mud bricks, brick, sandstone, terracotta) and roof colours
  (red/brown/orange terracotta, light grey concrete) based on each building's own footprint, instead
  of every house in a neighbourhood being an identical colour. Row-house/apartment buildings got a
  smaller wall-material variety pass too.
- **Foundation course**: every building now gets a one-block plinth at the base of its walls in the
  trim material, instead of the wall pattern starting flush at ground level. Small detail, but it's
  what was making buildings read as flat boxes up close.
- **Pitched roof eaves**: pitched roofs now extend one block past the wall face on all sides (a real
  eave overhang) instead of stopping flush with the walls, which is what made roofs look pasted onto
  the building rather than sitting on top of it.

## Explicitly deferred this round (not done, and why)
- **Yard fences, driveways, and front walkways** — I looked at adding these but backed off. Doing it
  well means checking a proposed fence/path against every road, sidewalk, and neighbouring building
  footprint so it doesn't cut through something else, and I don't have a way to verify that visually
  before handing it to you. I'd rather say no for now than ship something that clutters lots with
  broken-looking fences. Worth revisiting once we can iterate with real screenshots of a residential
  block specifically.
- **Curbs distinguishing the road edge from the sidewalk** — same reasoning: doable, but needs a
  verification loop I don't have yet, and I didn't want to add more unverified geometry changes in
  the same batch as the intersection/traffic-light logic above.
