# NexusTerra v0.1.6 patch notes

## Compile-verified

Unlike every previous patch in this series, **this one was actually
compiled before shipping.** I built a set of minimal API stubs for the
Bukkit, Adventure and Gson surfaces the plugin touches and ran `javac`
against the real sources. It compiles clean.

That doesn't guarantee correct runtime behaviour -- the stubs only
prove the code type-checks, not that Paper does what the stubs claim
-- but it does rule out the class of failure that stopped v0.1.5 from
loading at all.

## The big miss: landuse was being thrown away

`OverpassClient` has parsed `LANDUSE` features since v0.1.1, and
`TerrainGenerator` never once looked at them. Every park, plaza,
parking lot, sports pitch and cemetery in the source data was fetched
over the network, parsed into objects, and then silently dropped on
the floor. That is why generated downtowns had large blank patches of
default grass where the real place has something.

Now rendered, with a material per type -- grass for parks, podzol for
woodland, concrete for parking and industrial, moss for sports
pitches, sand for playgrounds, polished andesite for plazas. Parking
lots additionally get painted bay markings so they don't read as one
grey rectangle. The Overpass query was also widened to request
`leisure` and `amenity=parking`, which weren't being asked for at all
before.

`landuse=residential` is deliberately *not* paved -- flattening whole
neighbourhoods to a single surface looks far worse than leaving
terrain alone.

## Pitched roofs

Small buildings now get proper gabled roofs instead of flat caps:
houses, terraces, cabins, barns, sheds, garages, and churches. The
ridge runs down the footprint's long axis and the roof slopes off both
sides, with gable ends filled in so there are no triangular holes.

OSM's own `roof:shape` tag overrides the guess wherever it's present.
Anything over 20 blocks tall stays flat regardless -- a gable on a
tower looks ridiculous.

## Rooftop plant

Flat roofs are no longer bare tabletops. Buildings with a large enough
footprint get two HVAC units, a stairwell penthouse, and -- on
anything 35+ blocks tall -- an antenna mast. Positions are derived
from the footprint's own bounding box, so they're stable across
regenerations rather than jittering every run. This matters more than
it sounds: you spend a lot of time looking *down* at a generated city.

## Ground-floor shopfronts

Commercial, retail, office, hotel and restaurant buildings now get a
glazed ground storey with structural piers every sixth column, instead
of the same solid wall treatment as the upper floors. Solid stone at
street level is a lot of what made earlier renders feel like a
warehouse district rather than a high street.

## Street furniture

- Lamp posts along pavements, spaced on a grid and explicitly excluded
  from the carriageway (the sidewalk and road cell sets are tracked
  separately so a lamp can never end up in the middle of a road).
- Trees scattered across parks, woodland, cemeteries and orchards,
  at a spacing that varies by land type -- dense in forest, sparse in
  a cemetery. Placement is hashed from world coordinates, so it's
  deterministic.

## Still not done

Adjacent buildings whose real footprints touch still merge into one
visual mass, since each is rasterized independently -- this is the
most obvious remaining artefact in dense blocks. There's also no
bridge handling at all, which will look wrong anywhere a road crosses
water; roads currently just follow terrain height straight into the
river. And building interiors above the ground floor are still
entirely hollow.
