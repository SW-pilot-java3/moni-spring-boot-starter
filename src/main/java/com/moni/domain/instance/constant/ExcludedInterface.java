package com.moni.domain.instance.constant;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum ExcludedInterface {
    LOOPBACK("lo");

    private static final Set<String> NAMES = Arrays.stream(values())
            .map(excluded -> excluded.interfaceName)
            .collect(Collectors.toSet());

    private final String interfaceName;

    ExcludedInterface(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public static boolean isExcluded(String interfaceName) {
        return NAMES.contains(interfaceName);
    }
}
