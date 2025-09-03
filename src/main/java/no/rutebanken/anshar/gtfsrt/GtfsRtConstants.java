package no.rutebanken.anshar.gtfsrt;

public class GtfsRtConstants {

    private GtfsRtConstants() {
        throw new IllegalStateException();
    }

    public static final String LOCK_MAP = "ansharRouteLOCK_MAP";
    public static final String GTFS_RT_LOCK = "isGtfsRtRunning";
    public static final String GTFS_RT_LAST_EXECUTION_TIME = "gtfsRTLastExecutionTime";
    public static final String GTFS_RT_ET_PROXY_QUEUE = "activemq:queue:gtfsrt.queue.proxy.et.input";
    public static final String GTFS_RT_SM_PROXY_QUEUE = "activemq:queue:gtfsrt.queue.proxy.sm.input";
    public static final String GTFS_RT_VM_PROXY_QUEUE = "activemq:queue:gtfsrt.queue.proxy.vm.input";
    public static final String GTFS_RT_SX_PROXY_QUEUE = "activemq:queue:gtfsrt.queue.proxy.sx.input";
}
