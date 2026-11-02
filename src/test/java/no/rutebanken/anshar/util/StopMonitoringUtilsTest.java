package no.rutebanken.anshar.util;

import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.MonitoredStopVisit;
import uk.org.siri.siri21.MonitoredVehicleJourneyStructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StopMonitoringUtilsTest {

    @Test
    void absentMonitoredIsConsideredRealtime() {
        assertTrue(StopMonitoringUtils.isMonitored(buildStopVisit(null)));
    }

    @Test
    void explicitMonitoredValueIsRespected() {
        assertTrue(StopMonitoringUtils.isMonitored(buildStopVisit(true)));
        assertFalse(StopMonitoringUtils.isMonitored(buildStopVisit(false)));
    }

    private static MonitoredStopVisit buildStopVisit(Boolean monitored) {
        MonitoredVehicleJourneyStructure vehicleJourney = new MonitoredVehicleJourneyStructure();
        vehicleJourney.setMonitored(monitored);
        MonitoredStopVisit stopVisit = new MonitoredStopVisit();
        stopVisit.setMonitoredVehicleJourney(vehicleJourney);
        return stopVisit;
    }
}
