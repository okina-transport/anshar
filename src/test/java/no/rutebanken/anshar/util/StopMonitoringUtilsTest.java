package no.rutebanken.anshar.util;

import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.MonitoredCallStructure;
import uk.org.siri.siri21.MonitoredStopVisit;
import uk.org.siri.siri21.MonitoredVehicleJourneyStructure;
import uk.org.siri.siri21.NaturalLanguageStringStructure;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-11-27T10:00:00+01:00");

    @Test
    void imminentArrivalSetsProximityText() {
        MonitoredStopVisit visit = buildStopVisitWithTimes(NOW.plusSeconds(30), NOW.plusSeconds(600));
        StopMonitoringUtils.setImminentDepartureProximityText(List.of(visit), NOW);
        assertEquals("Départ proche", proximityText(visit));
    }

    @Test
    void distantArrivalDoesNotSetProximityTextEvenIfDepartureImminent() {
        MonitoredStopVisit visit = buildStopVisitWithTimes(NOW.plusSeconds(60), NOW.plusSeconds(10));
        StopMonitoringUtils.setImminentDepartureProximityText(List.of(visit), NOW);
        assertNull(proximityText(visit));
    }

    @Test
    void imminentDepartureUsedWhenArrivalAbsent() {
        MonitoredStopVisit visit = buildStopVisitWithTimes(null, NOW.plusSeconds(59));
        StopMonitoringUtils.setImminentDepartureProximityText(List.of(visit), NOW);
        assertEquals("Départ proche", proximityText(visit));
    }

    @Test
    void distantDepartureWhenArrivalAbsentDoesNotSetProximityText() {
        MonitoredStopVisit visit = buildStopVisitWithTimes(null, NOW.plusSeconds(120));
        StopMonitoringUtils.setImminentDepartureProximityText(List.of(visit), NOW);
        assertNull(proximityText(visit));
    }

    @Test
    void existingProximityTextKeptWhenNotImminent() {
        MonitoredStopVisit visit = buildStopVisitWithTimes(NOW.plusSeconds(300), null);
        NaturalLanguageStringStructure text = new NaturalLanguageStringStructure();
        text.setValue("A l'approche");
        visit.getMonitoredVehicleJourney().getMonitoredCall().setArrivalProximityText(text);
        StopMonitoringUtils.setImminentDepartureProximityText(List.of(visit), NOW);
        assertEquals("A l'approche", proximityText(visit));
    }

    @Test
    void pastArrivalDoesNotSetProximityText() {
        MonitoredStopVisit visit = buildStopVisitWithTimes(NOW.minusSeconds(10), NOW.plusSeconds(30));
        StopMonitoringUtils.setImminentDepartureProximityText(List.of(visit), NOW);
        assertNull(proximityText(visit));
    }

    @Test
    void arrivalExactlyNowDoesNotSetProximityText() {
        MonitoredStopVisit visit = buildStopVisitWithTimes(NOW, null);
        StopMonitoringUtils.setImminentDepartureProximityText(List.of(visit), NOW);
        assertNull(proximityText(visit));
    }

    @Test
    void pastDepartureWhenArrivalAbsentDoesNotSetProximityText() {
        MonitoredStopVisit visit = buildStopVisitWithTimes(null, NOW.minusSeconds(5));
        StopMonitoringUtils.setImminentDepartureProximityText(List.of(visit), NOW);
        assertNull(proximityText(visit));
    }

    @Test
    void noExpectedTimesDoesNotSetProximityText() {
        MonitoredStopVisit visit = buildStopVisitWithTimes(null, null);
        StopMonitoringUtils.setImminentDepartureProximityText(List.of(visit), NOW);
        assertNull(proximityText(visit));
    }

    private static String proximityText(MonitoredStopVisit visit) {
        NaturalLanguageStringStructure text = visit.getMonitoredVehicleJourney().getMonitoredCall().getArrivalProximityText();
        return text == null ? null : text.getValue();
    }

    private static MonitoredStopVisit buildStopVisitWithTimes(ZonedDateTime expectedArrival, ZonedDateTime expectedDeparture) {
        MonitoredCallStructure call = new MonitoredCallStructure();
        call.setExpectedArrivalTime(expectedArrival);
        call.setExpectedDepartureTime(expectedDeparture);
        MonitoredStopVisit visit = buildStopVisit(true);
        visit.getMonitoredVehicleJourney().setMonitoredCall(call);
        return visit;
    }

    private static MonitoredStopVisit buildStopVisit(Boolean monitored) {
        MonitoredVehicleJourneyStructure vehicleJourney = new MonitoredVehicleJourneyStructure();
        vehicleJourney.setMonitored(monitored);
        MonitoredStopVisit stopVisit = new MonitoredStopVisit();
        stopVisit.setMonitoredVehicleJourney(vehicleJourney);
        return stopVisit;
    }
}
