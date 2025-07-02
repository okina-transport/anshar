package no.rutebanken.anshar.routes.health;

import lombok.Data;

@Data
public class IncomingFlowDailyStatus {
    private String id;
    private DailyStatus dailyStatus;
    private String dataset;
    private String url;
    private IncomingFlowType type;
}
