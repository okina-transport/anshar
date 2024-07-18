package no.rutebanken.anshar.util;

import uk.org.siri.siri21.MonitoredStopVisit;
import uk.org.siri.siri21.MonitoredStopVisitCancellation;

import java.util.Optional;

public class StopMonitoringUtils {

    public static Optional<String> getLineName(MonitoredStopVisit stopVisit) {
        if (stopVisit.getMonitoredVehicleJourney() == null || stopVisit.getMonitoredVehicleJourney().getLineRef() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisit.getMonitoredVehicleJourney().getLineRef().getValue());
    }

    public static Optional<String> getLineName(MonitoredStopVisitCancellation stopVisitCancellation) {
        if (stopVisitCancellation.getLineRef() == null || stopVisitCancellation.getLineRef().getValue() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisitCancellation.getLineRef().getValue());
    }

    public static Optional<String> getVehicleJourneyName(MonitoredStopVisit stopVisit) {
        if (stopVisit.getMonitoredVehicleJourney() == null || stopVisit.getMonitoredVehicleJourney().getFramedVehicleJourneyRef() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisit.getMonitoredVehicleJourney().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
    }

    public static Optional<String> getVehicleJourneyName(MonitoredStopVisitCancellation stopVisitCancellation) {
        if (stopVisitCancellation.getVehicleJourneyRef() == null || stopVisitCancellation.getVehicleJourneyRef().getDatedVehicleJourneyRef() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisitCancellation.getVehicleJourneyRef().getDatedVehicleJourneyRef());
    }

    public static Optional<String> getMonitoringRef(MonitoredStopVisit stopVisit) {
        if (stopVisit.getMonitoringRef() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisit.getMonitoringRef().getValue());
    }
}
