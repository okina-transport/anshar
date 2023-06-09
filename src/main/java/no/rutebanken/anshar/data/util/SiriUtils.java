package no.rutebanken.anshar.data.util;

import uk.org.siri.siri20.MonitoredCallStructure;
import uk.org.siri.siri20.MonitoredStopVisit;
import uk.org.siri.siri20.MonitoredVehicleJourneyStructure;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public class SiriUtils {

    public static long getExpiration(MonitoredStopVisit monitoredStopVisit, long gracePeriod) {
        MonitoredVehicleJourneyStructure monitoredVehicleJourney = monitoredStopVisit.getMonitoredVehicleJourney();

        ZonedDateTime expiryTimestamp = null;
        if (monitoredVehicleJourney.getMonitoredCall() != null) {
            MonitoredCallStructure estimatedCalls = monitoredVehicleJourney.getMonitoredCall();

            if (estimatedCalls.getAimedArrivalTime() != null) {
                expiryTimestamp = estimatedCalls.getAimedArrivalTime();
            }
            if (estimatedCalls.getAimedDepartureTime() != null) {
                expiryTimestamp = estimatedCalls.getAimedDepartureTime();
            }
            if (estimatedCalls.getExpectedArrivalTime() != null) {
                expiryTimestamp = estimatedCalls.getExpectedArrivalTime();
            }
            if (estimatedCalls.getExpectedDepartureTime() != null) {
                expiryTimestamp = estimatedCalls.getExpectedDepartureTime();
            }
        }

        if (expiryTimestamp != null) {
            return ZonedDateTime.now().until(expiryTimestamp.plus(gracePeriod, ChronoUnit.MINUTES), ChronoUnit.MILLIS);
        }

        return -1;
    }
}
