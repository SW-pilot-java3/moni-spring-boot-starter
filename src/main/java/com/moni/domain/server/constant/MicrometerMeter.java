package com.moni.domain.server.constant;

public enum MicrometerMeter {
    JVM_MEMORY_USED("jvm.memory.used"),
    JVM_MEMORY_MAX("jvm.memory.max"),
    JVM_GC_PAUSE("jvm.gc.pause"),
    PROCESS_UPTIME("process.uptime"),
    JVM_THREADS_LIVE("jvm.threads.live"),
    JVM_THREADS_STATES("jvm.threads.states"),

    HTTP_SERVER_REQUESTS("http.server.requests"),

    HIKARICP_CONNECTIONS_ACTIVE("hikaricp.connections.active"),
    HIKARICP_CONNECTIONS_IDLE("hikaricp.connections.idle"),
    HIKARICP_CONNECTIONS_PENDING("hikaricp.connections.pending"),
    HIKARICP_CONNECTIONS_MAX("hikaricp.connections.max"),
    HIKARICP_CONNECTIONS_TIMEOUT("hikaricp.connections.timeout"),

    EXECUTOR_ACTIVE("executor.active"),
    EXECUTOR_POOL_MAX("executor.pool.max"),
    EXECUTOR_QUEUED("executor.queued"),
    EXECUTOR_QUEUE_REMAINING("executor.queue.remaining");

    private final String meterName;

    MicrometerMeter(String meterName) {
        this.meterName = meterName;
    }

    public String meterName() {
        return meterName;
    }
}
