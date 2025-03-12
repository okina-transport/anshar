package no.rutebanken.anshar.routes.health;

import java.util.ArrayList;
import java.util.List;

public class IncomingDataHealth {

    List<IncomingFlowStatus> gtfsStatuses = new ArrayList<>();
    List<IncomingFlowStatus> siriStatuses = new ArrayList<>();


    public List<IncomingFlowStatus> getGtfsStatuses() {
        return gtfsStatuses;
    }

    public void setGtfsStatuses(List<IncomingFlowStatus> gtfsStatuses) {
        this.gtfsStatuses = gtfsStatuses;
    }

    public List<IncomingFlowStatus> getSiriStatuses() {
        return siriStatuses;
    }

    public void setSiriStatuses(List<IncomingFlowStatus> siriStatuses) {
        this.siriStatuses = siriStatuses;
    }
}
