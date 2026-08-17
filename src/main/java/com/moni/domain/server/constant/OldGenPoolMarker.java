package com.moni.domain.server.constant;

import java.util.Arrays;
public enum OldGenPoolMarker {
    OLD("Old"),
    TENURED("Tenured");

    private final String marker;

    OldGenPoolMarker(String marker) {
        this.marker = marker;
    }

    public static boolean matchesPoolId(String poolId) {
        return poolId != null && Arrays.stream(values()).anyMatch(marker -> poolId.contains(marker.marker));
    }
}
