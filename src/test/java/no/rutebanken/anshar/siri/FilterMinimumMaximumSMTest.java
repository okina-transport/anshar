package no.rutebanken.anshar.siri;

import no.rutebanken.anshar.idTests.TestUtils;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.util.SiriUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.JourneyPlaceRefStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.MonitoredCallStructure;
import uk.org.siri.siri21.MonitoredStopVisit;
import uk.org.siri.siri21.MonitoredVehicleJourneyStructure;
import uk.org.siri.siri21.MonitoringRefStructure;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopMonitoringDeliveryStructure;
import uk.org.siri.siri21.ViaNameStructure;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

public class FilterMinimumMaximumSMTest {

    private static final String[] LINE_REFS = {"L1", "L1", "L2", "L2"};
    private static final String[] VIAS = {"V1", "V2", "V3", "V4"};
    private static final int[] MINUTES_PAST_THE_HOUR = {0, 10, 20, 30};

    // Times are anchored on tomorrow's date so expected departures are always in the future,
    // regardless of when the test is actually run.
    private final ZonedDateTime baseDate = ZonedDateTime.now().plusDays(1).truncatedTo(ChronoUnit.DAYS);


    /**
     * Filtering with maximum = 4.
     * So, only first 4 visits must be returned
     */
    @Test
    public void testFilterWithOnlyMaximum() {

        Siri siri = createSiriWithMultipleStopVisits();
        OutboundSubscriptionSetup outboundSubscriptionSetup = TestUtils.createSmOutboundSubscription(true);
        outboundSubscriptionSetup.setMaximumStopVisits(BigInteger.valueOf(4));

        Siri filteredSiri = SiriUtils.filterStopMonitoringOnNbOfStopVisits(siri, outboundSubscriptionSetup);

        List<MonitoredStopVisit> filteredVisits = filteredSiri.getServiceDelivery()
                .getStopMonitoringDeliveries().get(0).getMonitoredStopVisits();

        Assertions.assertEquals(4, filteredVisits.size());

        List<ZonedDateTime> expectedDepartures = filteredVisits.stream()
                .map(visit -> visit.getMonitoredVehicleJourney().getMonitoredCall().getExpectedDepartureTime())
                .toList();

        // maximum only -> the 4 soonest departures across all lines/vias are kept, in order: 8h, 8h10, 8h20, 8h30
        Assertions.assertEquals(List.of(
                expectedDeparture(8, 0),
                expectedDeparture(8, 10),
                expectedDeparture(8, 20),
                expectedDeparture(8, 30)
        ), expectedDepartures);
    }

    /**
     * Filtering with maximum = 4 and minPerLine = 2
     * So, only first 4 visits must be returned
     */
    @Test
    public void testFilterWithMinPerLine() {

        Siri siri = createSiriWithMultipleStopVisits();
        OutboundSubscriptionSetup outboundSubscriptionSetup = TestUtils.createSmOutboundSubscription(true);
        outboundSubscriptionSetup.setMaximumStopVisits(BigInteger.valueOf(4));
        outboundSubscriptionSetup.setMinimumStopVisitsPerLine(BigInteger.valueOf(2));

        Siri filteredSiri = SiriUtils.filterStopMonitoringOnNbOfStopVisits(siri, outboundSubscriptionSetup);
        List<MonitoredStopVisit> filteredVisits = filteredSiri.getServiceDelivery()
                .getStopMonitoringDeliveries().get(0).getMonitoredStopVisits();
        Assertions.assertEquals(4, filteredVisits.size());

        List<ZonedDateTime> expectedDepartures = filteredVisits.stream()
                .map(visit -> visit.getMonitoredVehicleJourney().getMonitoredCall().getExpectedDepartureTime())
                .toList();

        // maximum only -> the 4 soonest departures across all lines/vias are kept, in order: 8h, 8h10, 8h20, 8h30
        Assertions.assertEquals(List.of(
                expectedDeparture(8, 0),
                expectedDeparture(8, 10),
                expectedDeparture(8, 20),
                expectedDeparture(8, 30)
        ), expectedDepartures);

    }


    /**
     * Filtering with maximum = 4 and minPerLine = 3
     *
     */
    @Test
    public void testFilterWithMinPerLine2() {

        Siri siri = createSiriWithMultipleStopVisits();
        OutboundSubscriptionSetup outboundSubscriptionSetup = TestUtils.createSmOutboundSubscription(true);
        outboundSubscriptionSetup.setMaximumStopVisits(BigInteger.valueOf(4));
        outboundSubscriptionSetup.setMinimumStopVisitsPerLine(BigInteger.valueOf(3));

        Siri filteredSiri = SiriUtils.filterStopMonitoringOnNbOfStopVisits(siri, outboundSubscriptionSetup);
        List<MonitoredStopVisit> filteredVisits = filteredSiri.getServiceDelivery()
                .getStopMonitoringDeliveries().get(0).getMonitoredStopVisits();

        Assertions.assertEquals(6, filteredVisits.size());

        List<ZonedDateTime> expectedDepartures = filteredVisits.stream()
                .map(visit -> visit.getMonitoredVehicleJourney().getMonitoredCall().getExpectedDepartureTime())
                .toList();

        // minPerLine=3 -> the 3 soonest visits per line (L1: 8h,8h10,9h ; L2: 8h20,8h30,9h20) are kept
        Assertions.assertEquals(List.of(
                expectedDeparture(8, 0),
                expectedDeparture(8, 10),
                expectedDeparture(8, 20),
                expectedDeparture(8, 30),
                expectedDeparture(9, 0),
                expectedDeparture(9, 20)
        ), expectedDepartures);
    }

    /**
     * Filtering with maximum = 6 and minPerLine = 2
     *
     */
    @Test
    public void testFilterWithMinPerLine3() {

        Siri siri = createSiriWithMultipleStopVisits();
        OutboundSubscriptionSetup outboundSubscriptionSetup = TestUtils.createSmOutboundSubscription(true);
        outboundSubscriptionSetup.setMaximumStopVisits(BigInteger.valueOf(4));
        outboundSubscriptionSetup.setMinimumStopVisitsPerLine(BigInteger.valueOf(3));

        Siri filteredSiri = SiriUtils.filterStopMonitoringOnNbOfStopVisits(siri, outboundSubscriptionSetup);
        List<MonitoredStopVisit> filteredVisits = filteredSiri.getServiceDelivery()
                .getStopMonitoringDeliveries().get(0).getMonitoredStopVisits();

        Assertions.assertEquals(6, filteredVisits.size());

        List<ZonedDateTime> expectedDepartures = filteredVisits.stream()
                .map(visit -> visit.getMonitoredVehicleJourney().getMonitoredCall().getExpectedDepartureTime())
                .toList();


        Assertions.assertEquals(List.of(
                expectedDeparture(8, 0),
                expectedDeparture(8, 10),
                expectedDeparture(8, 20),
                expectedDeparture(8, 30),
                expectedDeparture(9, 0),
                expectedDeparture(9, 20)
        ), expectedDepartures);
    }

    /**
     * Filtering with maximum = 4 and minPerLineVia = 2
     *
     */
    @Test
    public void testFilterWithMinPerLineVia() {

        Siri siri = createSiriWithMultipleStopVisits();
        OutboundSubscriptionSetup outboundSubscriptionSetup = TestUtils.createSmOutboundSubscription(true);
        outboundSubscriptionSetup.setMaximumStopVisits(BigInteger.valueOf(4));
        outboundSubscriptionSetup.setMinimumStopVisitsPerLineVia(BigInteger.valueOf(2));

        Siri filteredSiri = SiriUtils.filterStopMonitoringOnNbOfStopVisits(siri, outboundSubscriptionSetup);
        List<MonitoredStopVisit> filteredVisits = filteredSiri.getServiceDelivery()
                .getStopMonitoringDeliveries().get(0).getMonitoredStopVisits();

        Assertions.assertEquals(8, filteredVisits.size());

        List<ZonedDateTime> expectedDepartures = filteredVisits.stream()
                .map(visit -> visit.getMonitoredVehicleJourney().getMonitoredCall().getExpectedDepartureTime())
                .toList();

        // minPerLineVia=2 -> the 2 soonest visits per line/via (V1, V2, V3, V4) are kept
        Assertions.assertEquals(List.of(
                expectedDeparture(8, 0),
                expectedDeparture(8, 10),
                expectedDeparture(8, 20),
                expectedDeparture(8, 30),
                expectedDeparture(9, 0),
                expectedDeparture(9, 10),
                expectedDeparture(9, 20),
                expectedDeparture(9, 30)
        ), expectedDepartures);
    }

    /**
     * Filtering with maximum = 10 and minPerLineVia = 2
     *
     */
    @Test
    public void testFilterWithMinPerLineVia2() {

        Siri siri = createSiriWithMultipleStopVisits();
        OutboundSubscriptionSetup outboundSubscriptionSetup = TestUtils.createSmOutboundSubscription(true);
        outboundSubscriptionSetup.setMaximumStopVisits(BigInteger.valueOf(10));
        outboundSubscriptionSetup.setMinimumStopVisitsPerLineVia(BigInteger.valueOf(2));

        Siri filteredSiri = SiriUtils.filterStopMonitoringOnNbOfStopVisits(siri, outboundSubscriptionSetup);
        List<MonitoredStopVisit> filteredVisits = filteredSiri.getServiceDelivery()
                .getStopMonitoringDeliveries().get(0).getMonitoredStopVisits();

        Assertions.assertEquals(10, filteredVisits.size());

        List<ZonedDateTime> expectedDepartures = filteredVisits.stream()
                .map(visit -> visit.getMonitoredVehicleJourney().getMonitoredCall().getExpectedDepartureTime())
                .toList();

        // minPerLineVia=2 fills 8 visits (2 per line/via), then maximum=10 pulls in the 2 next soonest overall
        Assertions.assertEquals(List.of(
                expectedDeparture(8, 0),
                expectedDeparture(8, 10),
                expectedDeparture(8, 20),
                expectedDeparture(8, 30),
                expectedDeparture(9, 0),
                expectedDeparture(9, 10),
                expectedDeparture(9, 20),
                expectedDeparture(9, 30),
                expectedDeparture(10, 0),
                expectedDeparture(10, 10)
        ), expectedDepartures);
    }


    private ZonedDateTime expectedDeparture(int hour, int minute) {
        return baseDate.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
    }

    /**
     * -  lineRef L1, avec Via V1 :  expectedDeparture à 8h, 9h, 10h, 11h
     * -  lineRef L1, avec Via V2 :  expectedDeparture à 8h10, 9h10, 10h10, 11h10
     * -  lineRef L2, avec Via V3 :  expectedDeparture à 8h20, 9h20, 10h20, 11h20
     * -  lineRef L2, avec Via V4 :  expectedDeparture à 8h30, 9h30, 10h30, 11h30
     *
     * @return
     */
    private Siri createSiriWithMultipleStopVisits() {
        Siri siri = new Siri();
        ServiceDelivery serviceDelivery = new ServiceDelivery();
        StopMonitoringDeliveryStructure stopMonitoringDelivery = new StopMonitoringDeliveryStructure();

        for (int group = 0; group < LINE_REFS.length; group++) {
            for (int hour = 8; hour <= 11; hour++) {
                stopMonitoringDelivery.getMonitoredStopVisits().add(
                        createMonitoredStopVisit(LINE_REFS[group], VIAS[group], expectedDeparture(hour, MINUTES_PAST_THE_HOUR[group]))
                );
            }
        }

        serviceDelivery.getStopMonitoringDeliveries().add(stopMonitoringDelivery);
        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }

    private MonitoredStopVisit createMonitoredStopVisit(String lineRefValue, String viaValue, ZonedDateTime expectedDeparture) {
        MonitoredStopVisit stopVisit = new MonitoredStopVisit();
        stopVisit.setRecordedAtTime(ZonedDateTime.now());
        stopVisit.setItemIdentifier(UUID.randomUUID().toString());

        MonitoringRefStructure monitoringRef = new MonitoringRefStructure();
        monitoringRef.setValue(UUID.randomUUID().toString());
        stopVisit.setMonitoringRef(monitoringRef);

        MonitoredVehicleJourneyStructure vehicleJourney = new MonitoredVehicleJourneyStructure();

        LineRef lineRef = new LineRef();
        lineRef.setValue(lineRefValue);
        vehicleJourney.setLineRef(lineRef);

        ViaNameStructure via = new ViaNameStructure();
        JourneyPlaceRefStructure placeRef = new JourneyPlaceRefStructure();
        placeRef.setValue(viaValue);
        via.setPlaceRef(placeRef);
        vehicleJourney.getVias().add(via);

        MonitoredCallStructure monitoredCall = new MonitoredCallStructure();
        monitoredCall.setExpectedDepartureTime(expectedDeparture);
        vehicleJourney.setMonitoredCall(monitoredCall);

        stopVisit.setMonitoredVehicleJourney(vehicleJourney);
        return stopVisit;
    }
}
