# NexusTerra v0.1.13

## Usage
1. `mvn package`
2. Copy `target/NexusTerra-0.1.13.jar` into your server's `plugins/` folder (delete the old version first).
3. Restart the server.
4. Run `/nexusterra generate <lat> <lon> [radius]` as usual.

## Changed in v0.1.13
- **Material variety expanded to every building type, not just houses.** Commercial/retail/supermarket,
  office, industrial/warehouse, and school/university/hospital/civic/government buildings each now pick
  from 2-3 wall material variants per building (quartz/sandstone/diorite for commercial, concrete/glass/
  stone for office, deepslate/iron/concrete for industrial, sandstone/calcite variants for civic). Only
  hotel and the church/temple family stayed single-material, since those tend to be one-off landmark
  buildings rather than repeated blocks where uniformity reads as a bug.
- **Window sills.** The bottom row of each window opening (where there's more than one row of glass
  available) is now a matching slab of the building's trim material instead of another pane of glass —
  a real sill ledge instead of glass running straight to a hard edge.

## Explicitly NOT done this round, and why
You asked to go all-in with materials and detail, and the honest next step for the biggest visual jump
is switching pitched roofs from flat blocks to actual angled stair blocks, and adding real door blocks
at entrances instead of empty gaps. I did not do that in this batch, on purpose:

Both stairs and doors need correct block *orientation* (which way they face), not just a material pick.
I have no way to render or preview the result before you do — if I get the facing convention backward,
every roof or door on the generated map would look wrong in the same way, all at once, and you'd only
find out after generating a whole city. I'd rather tell you that plainly than ship it and hope.

If you want me to build it anyway, say so and I will — I'll implement it as carefully as I can from the
documented Minecraft block-state conventions, but plan on one round of "here's a screenshot, the facing
is backward" and a one-line fix in response, rather than expecting it perfect on the first try. That's
a real trade-off, not a brush-off, and I'd rather set that expectation now than after you've generated
half a city with inverted roofs.
