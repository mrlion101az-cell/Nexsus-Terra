package com.nexusuniverse.terra.geo;

import java.util.List;
import java.util.Map;

/**
 * One parsed OpenStreetMap "way" -- a building footprint, road
 * centerline, water body outline, or landuse area. The vertex list is
 * the way's real-world geometry, in order, as returned directly by
 * Overpass's "out geom" mode (no separate node-lookup pass needed).
 *
 * tags carries every OSM tag on the way, not just the one used to pick
 * `category`/`subtype` - v0.1.4 needs building:levels/height and
 * building material tags to make generated structures look less like
 * a single undifferentiated grey block.
 */
public record OsmFeature(Category category, String subtype, List<GeoPoint> vertices, Map<String, String> tags) {

    public enum Category { BUILDING, ROAD, WATER, LANDUSE }

    public String tag(String key, String fallback) {
        String value = tags.get(key);
        return value != null ? value : fallback;
    }
}
