package no.rutebanken.anshar.util;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import uk.org.siri.siri21.MonitoredStopVisit;
import uk.org.siri.siri21.MonitoredStopVisitCancellation;
import uk.org.siri.siri21.MonitoredVehicleJourneyStructure;

import java.time.ZonedDateTime;
import java.util.Optional;

@Slf4j
public class StopMonitoringUtils {


    private static final String ENTRY_TIME_TAG_NAME = "EntryTime";


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


    public static Optional<String> getAimedTimeAtStop(MonitoredStopVisit stopVisit) {
        Optional<String> result = Optional.empty();
        if (stopVisit.getMonitoredVehicleJourney() == null || stopVisit.getMonitoredVehicleJourney().getMonitoredCall() == null) {
            return Optional.empty();
        }

        ZonedDateTime aimedDepartureTime = stopVisit.getMonitoredVehicleJourney().getMonitoredCall().getAimedDepartureTime();
        ZonedDateTime aimedArrivalTime = stopVisit.getMonitoredVehicleJourney().getMonitoredCall().getAimedArrivalTime();
        if (aimedDepartureTime != null) {
            result = Optional.of(String.valueOf(aimedDepartureTime.toInstant()));
        } else if (aimedArrivalTime != null) {
            result = Optional.of(String.valueOf(aimedArrivalTime.toInstant()));
        }
        return result;
    }

    public static void feedDestinationDisplay(MonitoredStopVisit stopVisit) {
        MonitoredVehicleJourneyStructure monitoredVehicleJourney = stopVisit.getMonitoredVehicleJourney();
        if (monitoredVehicleJourney != null
                && monitoredVehicleJourney.getMonitoredCall() != null
                && CollectionUtils.isEmpty(monitoredVehicleJourney.getMonitoredCall().getDestinationDisplaies())
                && CollectionUtils.isNotEmpty(monitoredVehicleJourney.getDestinationNames())) {
                monitoredVehicleJourney.getMonitoredCall().getDestinationDisplaies().addAll(monitoredVehicleJourney.getDestinationNames());
        } else if (stopVisit.getMonitoringRef() != null) {
            log.debug("No destination display and no destination name found for {}", stopVisit.getMonitoringRef().getValue());
        }
    }
}
