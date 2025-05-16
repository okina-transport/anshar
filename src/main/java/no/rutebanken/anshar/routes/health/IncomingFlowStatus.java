package no.rutebanken.anshar.routes.health;

import lombok.Data;

@Data
public class IncomingFlowStatus {

    private String id;
    private String status;
    private long lastUpdate;
    private String dataset;
    private String url;
    private IncomingFlowType type;

    public enum IncomingFlowType {
        GTFS,
        SIRI,
    }
    
}
