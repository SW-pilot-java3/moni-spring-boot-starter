package com.moni.domain.instance.constant;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum PseudoFilesystemType {
    TMPFS("tmpfs"),
    DEVTMPFS("devtmpfs"),
    PROC("proc"),
    SYSFS("sysfs"),
    OVERLAY("overlay"),
    SQUASHFS("squashfs"),
    CGROUP("cgroup"),
    CGROUP2("cgroup2"),
    DEVPTS("devpts"),
    DEBUGFS("debugfs"),
    TRACEFS("tracefs"),
    MQUEUE("mqueue"),
    SECURITYFS("securityfs"),
    PSTORE("pstore"),
    BPF("bpf"),
    AUTOFS("autofs"),
    RPC_PIPEFS("rpc_pipefs"),
    CONFIGFS("configfs"),
    FUSECTL("fusectl"),
    BINFMT_MISC("binfmt_misc");

    private static final Set<String> FSTYPES = Arrays.stream(values())
            .map(type -> type.fstype)
            .collect(Collectors.toSet());

    private final String fstype;

    PseudoFilesystemType(String fstype) {
        this.fstype = fstype;
    }

    public static boolean isPseudo(String fstype) {
        return FSTYPES.contains(fstype);
    }
}
