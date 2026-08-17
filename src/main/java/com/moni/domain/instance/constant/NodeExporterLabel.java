package com.moni.domain.instance.constant;

public enum NodeExporterLabel {
    DEVICE("device"),
    MOUNTPOINT("mountpoint"),
    FSTYPE("fstype"),
    MODE("mode"),
    CPU("cpu");

    private final String key;

    NodeExporterLabel(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
