package com.moni.domain.instance.constant;

public enum CpuMode {
    IDLE("idle"),
    IOWAIT("iowait");

    private final String value;

    CpuMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
