/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  java.lang.Integer
 *  java.lang.NumberFormatException
 *  java.lang.Object
 *  java.lang.String
 *  java.util.List
 *  java.util.Map
 */
package com.nexusuniverse.terra.geo;

import com.nexusuniverse.terra.geo.GeoPoint;
import java.lang.Integer;
import java.lang.NumberFormatException;
import java.lang.Object;
import java.lang.String;
import java.util.List;
import java.util.Map;

public record OsmFeature(Category category, String subtype, List<List<GeoPoint>> rings, Map<String, String> tags) {
    public List<GeoPoint> vertices() {
        return this.rings.isEmpty() ? List.of() : (List)this.rings.get(0);
    }

    public String tag(String key, String fallback) {
        String value = (String)this.tags.get((Object)key);
        return value != null ? value : fallback;
    }

    public boolean hasTag(String key) {
        return this.tags.containsKey((Object)key);
    }

    public boolean isBridge() {
        String bridge = (String)this.tags.get((Object)"bridge");
        return bridge != null && !bridge.equals((Object)"no");
    }

    public boolean isTunnel() {
        String tunnel = (String)this.tags.get((Object)"tunnel");
        return tunnel != null && !tunnel.equals((Object)"no");
    }

    public int layer() {
        String raw = (String)this.tags.get((Object)"layer");
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt((String)raw.trim());
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    public static enum Category {
        BUILDING,
        ROAD,
        WATER,
        LANDUSE,
        RAILWAY,
        BARRIER;

    }
}
