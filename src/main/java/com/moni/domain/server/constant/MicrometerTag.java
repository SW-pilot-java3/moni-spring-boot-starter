package com.moni.domain.server.constant;

public enum MicrometerTag {
    POOL("pool"),
    NAME("name"),
    AREA("area"),
    ID("id"),
    STATE("state"),
    URI("uri"),
    METHOD("method"),
    STATUS("status");

    private final String key;

    MicrometerTag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
