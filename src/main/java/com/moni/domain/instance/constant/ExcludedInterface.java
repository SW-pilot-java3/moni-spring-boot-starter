package com.moni.domain.instance.constant;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public enum ExcludedInterface {
    LOOPBACK("lo"),
    DOCKER_BRIDGE("docker\\d*"),
    VETH("veth.*"),
    CUSTOM_BRIDGE("br-.*"),
    LIBVIRT_BRIDGE("virbr\\d*");

    private static final Pattern EXCLUDED_PATTERN = Pattern.compile(
            Arrays.stream(values())
                    .map(excluded -> excluded.pattern)
                    .collect(Collectors.joining("|")));

    private final String pattern;

    ExcludedInterface(String pattern) {
        this.pattern = pattern;
    }

    public static boolean isExcluded(String interfaceName) {
        return EXCLUDED_PATTERN.matcher(interfaceName).matches();
    }
}
