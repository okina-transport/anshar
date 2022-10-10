package no.rutebanken.anshar.util;

import uk.org.siri.siri20.MonitoredStopVisit;

import java.util.Optional;

public class StopMonitoringUtils {

    public static Optional<String> getLineName(MonitoredStopVisit stopVisit){
        if (stopVisit.getMonitoredVehicleJourney() == null || stopVisit.getMonitoredVehicleJourney().getLineRef() == null){
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisit.getMonitoredVehicleJourney().getLineRef().getValue());
    }

    public static Optional<String> getVehicleJourneyName(MonitoredStopVisit stopVisit){
        if (stopVisit.getMonitoredVehicleJourney() == null || stopVisit.getMonitoredVehicleJourney().getFramedVehicleJourneyRef() == null ){
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisit.getMonitoredVehicleJourney().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
    }

    public static Optional<String> getMonitoringRef(MonitoredStopVisit stopVisit){
        if (stopVisit.getMonitoringRef() == null ){
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisit.getMonitoringRef().getValue());
    }
}
