package com.nexusuniverse.terra.geo;

import java.util.List;
import java.util.Map;

/**
 * One parsed OpenStreetMap feature -- a building footprint, road
 * centreline, water body outline, railway, barrier, or landuse area.
 *
 * v0.1.7 changed `vertices` to `rings`. OSM represents many large or
 * complex areas (city parks with lakes in them, rivers, building
 * complexes with courtyards) as *multipolygon relations* rather than
 * simple closed ways, and the generator was only ever asking Overpass
 * for ways -- so those features were invisible to it entirely. A
 * relation arrives as several rings: `outer` boundaries and `inner`
 * holes. Holding a list of rings lets the rasterizer apply the
 * even-odd rule across all of them at once, which produces the holes
 * for free.
 *
 * Line features (roads, railways, fences) simply carry a single ring
 * that isn't closed; `vertices()` returns it for convenience.
 */
public record OsmFeature(Category category, String subtype, List<List<GeoPoint>> rings, Map<String, String> tags) {

    public enum Category { BUILDING, ROAD, WATER, LANDUSE, RAILWAY, BARRIER }

    /** The first (or only) ring. Line features have exactly one. */
    public List<GeoPoint> vertices() {
        return rings.isEmpty() ? List.of() : rings.get(0);
    }

    public String tag(String key, String fallback) {
        String value = tags.get(key);
        return value != null ? value : fallback;
    }

    public boolean hasTag(String key) {
        return tags.containsKey(key);
    }

    /** True when OSM marks this way as carried on a bridge. */
    public boolean isBridge() {
        String bridge = tags.get("bridge");
        return bridge != null && !bridge.equals("no");
    }

    /** True when OSM marks this way as running through a tunnel. */
    public boolean isTunnel() {
        String tunnel = tags.get("tunnel");
        return tunnel != null && !tunnel.equals("no");
    }

    /**
     * OSM's `layer` tag: negative below ground, positive above. Used to
     * stack overpasses correctly rather than letting them fight for the
     * same block.
     */
    public int layer() {
        String raw = tags.get("layer");
        if (raw == null) return 0;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
