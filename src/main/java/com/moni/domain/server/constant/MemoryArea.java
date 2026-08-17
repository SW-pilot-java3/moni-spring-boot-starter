package com.moni.domain.server.constant;

public enum MemoryArea {
    HEAP("heap");

    private final String value;

    MemoryArea(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
