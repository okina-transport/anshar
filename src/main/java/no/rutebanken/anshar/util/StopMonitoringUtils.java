package no.rutebanken.anshar.util;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import uk.org.siri.siri21.MonitoredStopVisit;
import uk.org.siri.siri21.MonitoredStopVisitCancellation;
import uk.org.siri.siri21.MonitoredVehicleJourneyStructure;
import uk.org.siri.siri21.NaturalLanguageStringStructure;

import java.time.ZonedDateTime;
import java.util.Optional;

@Slf4j
public class StopMonitoringUtils {


    public static Optional<String> getLineRef(MonitoredStopVisit stopVisit) {
        if (stopVisit.getMonitoredVehicleJourney() == null || stopVisit.getMonitoredVehicleJourney().getLineRef() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisit.getMonitoredVehicleJourney().getLineRef().getValue());
    }

    public static Optional<String> getLineName(MonitoredStopVisit stopVisit) {
        if (stopVisit.getMonitoredVehicleJourney() == null) {
            return Optional.empty();
        }
        return stopVisit.getMonitoredVehicleJourney().getPublishedLineNames().stream()
                .map(NaturalLanguageStringStructure::getValue)
                .filter(StringUtils::isNotBlank)
                .findFirst();
    }

    public static Optional<String> getLineRef(MonitoredStopVisitCancellation stopVisitCancellation) {
        if (stopVisitCancellation.getLineRef() == null || stopVisitCancellation.getLineRef().getValue() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisitCancellation.getLineRef().getValue());
    }

    public static Optional<String> getVehicleJourneyRef(MonitoredStopVisit stopVisit) {
        if (stopVisit.getMonitoredVehicleJourney() == null || stopVisit.getMonitoredVehicleJourney().getFramedVehicleJourneyRef() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisit.getMonitoredVehicleJourney().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
    }

    public static Optional<String> getVehicleJourneyName(MonitoredStopVisit stopVisit) {
        if (stopVisit.getMonitoredVehicleJourney() == null) {
            return Optional.empty();
        }
        return stopVisit.getMonitoredVehicleJourney().getVehicleJourneyNames().stream()
                .map(NaturalLanguageStringStructure::getValue)
                .filter(StringUtils::isNotBlank)
                .findFirst();
    }

    public static Optional<String> getVehicleJourneyRef(MonitoredStopVisitCancellation stopVisitCancellation) {
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

    public static Optional<String> getDestinationRef(MonitoredStopVisit stopVisit) {
        if (stopVisit.getMonitoredVehicleJourney() == null || stopVisit.getMonitoredVehicleJourney().getDestinationRef() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(stopVisit.getMonitoredVehicleJourney().getDestinationRef().getValue());
    }

    public static Optional<String> getDestinationName(MonitoredStopVisit stopVisit) {
        if (stopVisit.getMonitoredVehicleJourney() == null) {
            return Optional.empty();
        }
        return stopVisit.getMonitoredVehicleJourney().getDestinationNames().stream()
                .map(NaturalLanguageStringStructure::getValue)
                .filter(StringUtils::isNotBlank)
                .findFirst();
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
