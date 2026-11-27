package no.rutebanken.anshar.util;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import uk.org.siri.siri21.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class StopMonitoringUtils {

    public static final String IMMINENT_DEPARTURE_TEXT = "Départ proche";
    public static final Duration IMMINENT_DEPARTURE_THRESHOLD = Duration.ofSeconds(60);

    private StopMonitoringUtils() {
    }

    /**
     * Sets ArrivalProximityText to "Départ proche" on each visit whose expected time at stop
     * (ExpectedArrivalTime, or ExpectedDepartureTime when ExpectedArrivalTime is null)
     * is strictly in the future and less than {@link #IMMINENT_DEPARTURE_THRESHOLD} after {@code now}.
     */
    public static void setImminentDepartureProximityText(List<MonitoredStopVisit> stopVisits, ZonedDateTime now) {
        if (CollectionUtils.isEmpty(stopVisits)) {
            return;
        }
        ZonedDateTime limit = now.plus(IMMINENT_DEPARTURE_THRESHOLD);
        for (MonitoredStopVisit stopVisit : stopVisits) {
            if (stopVisit == null || stopVisit.getMonitoredVehicleJourney() == null) {
                continue;
            }
            MonitoredCallStructure call = stopVisit.getMonitoredVehicleJourney().getMonitoredCall();
            if (call == null) {
                continue;
            }
            ZonedDateTime expectedTime = call.getExpectedArrivalTime() != null ? call.getExpectedArrivalTime() : call.getExpectedDepartureTime();
            if (expectedTime != null && expectedTime.isAfter(now) && expectedTime.isBefore(limit)) {
                NaturalLanguageStringStructure text = new NaturalLanguageStringStructure();
                text.setValue(IMMINENT_DEPARTURE_TEXT);
                call.setArrivalProximityText(text);
            }
        }
    }

    public static boolean isMonitored(MonitoredStopVisit stopVisit) {
        if (stopVisit.getMonitoredVehicleJourney() == null || stopVisit.getMonitoredVehicleJourney().isMonitored() == null) {
            return true;
        }
        return Boolean.TRUE.equals(stopVisit.getMonitoredVehicleJourney().isMonitored());
    }

    public static Optional<String> getLineRef(MonitoredStopVisit stopVisit) {
        if (stopVisit.getMonitoredVehicleJourney() == null || stopVisit.getMonitoredVehicleJourney().getLineRef() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisit.getMonitoredVehicleJourney().getLineRef().getValue());
    }

    public static Optional<String> getVias(MonitoredStopVisit stopVisit) {
        if (stopVisit.getMonitoredVehicleJourney() == null || stopVisit.getMonitoredVehicleJourney().getVias() == null) {
            return Optional.empty();
        }


        return Optional.of(stopVisit.getMonitoredVehicleJourney().getVias().stream()
                .map(StopMonitoringUtils::extractPlaceNameOrRef)
                .collect(Collectors.joining("-")));
    }

    public static String extractPlaceNameOrRef(ViaNameStructure viaNameStruct) {
        if (viaNameStruct.getPlaceRef() != null) {
            return viaNameStruct.getPlaceRef().getValue();
        } else if (CollectionUtils.isNotEmpty(viaNameStruct.getPlaceNames())) {
            return viaNameStruct.getPlaceNames().stream()
                    .sorted(Comparator.comparing(NaturalLanguagePlaceNameStructure::getLang, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(NaturalLanguagePlaceNameStructure::getValue, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(NaturalLanguagePlaceNameStructure::getValue)
                    .collect(Collectors.joining("-"));
        } else {
            return null;
        }
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

    public static Optional<String> getOriginRef(MonitoredStopVisit stopVisit) {
        if (stopVisit.getMonitoredVehicleJourney() == null || stopVisit.getMonitoredVehicleJourney().getOriginRef() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(stopVisit.getMonitoredVehicleJourney().getOriginRef().getValue());
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

}
