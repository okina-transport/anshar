package no.rutebanken.anshar.gtfsRT;


import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.api.PublishedLineNameMapping;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.gtfsrt.mappers.TripUpdateMapper;
import no.rutebanken.anshar.routes.mapping.LineUpdaterService;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.mapping.StopTimesService;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.siri.siri21.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripUpdateMapperTest {

    @InjectMocks
    private TripUpdateMapper tripUpdateMapper;

    @Mock
    private StopTimesService stopTimesService;

    @Mock
    private StopPlaceUpdaterService stopPlaceService;

    @Mock
    private LineUpdaterService lineUpdaterService;

    @Mock
    private SubscriptionConfig subscriptionConfig;

    @Test
    void testGTFSRTTripUpdateMapperTest_with_occupancy() {
        GtfsRealtime.TripUpdate.Builder tripBuilder = GtfsRealtime.TripUpdate.newBuilder();
        GtfsRealtime.TripDescriptor.Builder tripDescBuild = GtfsRealtime.TripDescriptor.newBuilder();
        tripDescBuild.setTripId("tripId");
        tripBuilder.setTrip(tripDescBuild);
        GtfsRealtime.TripUpdate.StopTimeUpdate.Builder stopTimeUpd = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder();
        stopTimeUpd.setStopId("stopId");
        stopTimeUpd.setStopSequence(0);
        GtfsRealtime.TripUpdate.StopTimeEvent.Builder ste = GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder();
        ste.setDelay(50);
        stopTimeUpd.setDeparture(ste.build());
        tripBuilder.addStopTimeUpdate(stopTimeUpd);

        List<String> routeIdList = Collections.emptyList();
        GtfsRealtime.VehiclePosition.OccupancyStatus status = GtfsRealtime.VehiclePosition.OccupancyStatus.FEW_SEATS_AVAILABLE;

        EstimatedVehicleJourney vehicleJourney = tripUpdateMapper.mapVehicleJourneyFromTripUpdate(
                tripBuilder.build(), "", routeIdList, PublishedLineNameMapping.LINE_NAME, status);

        assertThat(vehicleJourney).isNotNull();
        assertThat(vehicleJourney.getEstimatedCalls().getEstimatedCalls()).isNotEmpty();
        EstimatedCall firstCall = vehicleJourney.getEstimatedCalls().getEstimatedCalls().get(0);
        assertEquals(OccupancyEnumeration.FEW_SEATS_AVAILABLE, firstCall.getExpectedDepartureOccupancies().get(0).getOccupancyLevel());
    }

    @Test
    void testGTFSRTTripUpdateToStopMonitoringMapperTest_with_occupancy() {
        String dataset = "DAT1";
        String tripId = "tripId";
        String stopId = "stopId";

        when(stopTimesService.getDirectionId(dataset, tripId)).thenReturn(Optional.of("A"));

        GtfsRealtime.TripUpdate.Builder tripBuilder = GtfsRealtime.TripUpdate.newBuilder();
        GtfsRealtime.TripDescriptor.Builder tripDescBuild = GtfsRealtime.TripDescriptor.newBuilder();
        tripDescBuild.setTripId(tripId);
        tripBuilder.setTrip(tripDescBuild);
        GtfsRealtime.TripUpdate.StopTimeUpdate.Builder stopTimeUpd = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder();
        stopTimeUpd.setStopId(stopId);
        stopTimeUpd.setStopSequence(0);
        GtfsRealtime.TripUpdate.StopTimeEvent.Builder ste = GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder();
        ste.setDelay(50);
        stopTimeUpd.setDeparture(ste.build());
        tripBuilder.addStopTimeUpdate(stopTimeUpd);

        GtfsRealtime.VehiclePosition.OccupancyStatus expectedStatus = GtfsRealtime.VehiclePosition.OccupancyStatus.MANY_SEATS_AVAILABLE;
        List<String> routeIdList = Collections.emptyList();
        Map<String, GtfsRealtime.VehiclePosition> vehiclePositionsByTripId = new HashMap<>();
        List<MonitoredStopVisit> stopMonitorings = tripUpdateMapper.mapStopVisitFromTripUpdate(
                tripBuilder.build(),
                dataset,
                routeIdList,
                PublishedLineNameMapping.LINE_NAME,
                vehiclePositionsByTripId,
                expectedStatus
        );

        Assertions.assertEquals(1, stopMonitorings.size());
        MonitoredStopVisit sm = stopMonitorings.getFirst();

        Assertions.assertEquals(1, sm.getMonitoredVehicleJourney().getDirectionNames().size());
        Assertions.assertEquals("A", sm.getMonitoredVehicleJourney().getDirectionNames().getFirst().getValue());

        MonitoredCallStructure monitoredCall = sm.getMonitoredVehicleJourney().getMonitoredCall();
        Assertions.assertNotNull(monitoredCall);
        Assertions.assertFalse(monitoredCall.getExpectedDepartureOccupancies().isEmpty(), "La liste d'occupation ne devrait pas être vide");
        OccupancyEnumeration siriOccupancy = monitoredCall.getExpectedDepartureOccupancies().getFirst().getOccupancyLevel();
        Assertions.assertEquals(OccupancyEnumeration.MANY_SEATS_AVAILABLE, siriOccupancy);
    }

    @Test
    void testGTFSRTTripUpdateToStopMonitoringMapperTest_with_direction_0() {
        GtfsRealtime.TripUpdate.Builder tripBuilder = GtfsRealtime.TripUpdate.newBuilder();
        GtfsRealtime.TripDescriptor.Builder tripDescBuild = GtfsRealtime.TripDescriptor.newBuilder();
        tripDescBuild.setTripId("tripId");
        tripDescBuild.setDirectionId(0);
        tripBuilder.setTrip(tripDescBuild);
        GtfsRealtime.TripUpdate.StopTimeUpdate.Builder stopTimeUpd = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder();
        stopTimeUpd.setStopId("stopId");
        stopTimeUpd.setStopSequence(0);
        GtfsRealtime.TripUpdate.StopTimeEvent.Builder ste = GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder();
        ste.setDelay(50);
        stopTimeUpd.setDeparture(ste.build());
        tripBuilder.addStopTimeUpdate(stopTimeUpd);

        List<String> routeIdList = Arrays.asList("".split(","));
        List<MonitoredStopVisit> stopMonitorings = tripUpdateMapper.mapStopVisitFromTripUpdate(tripBuilder.build(), "", routeIdList, PublishedLineNameMapping.LINE_NAME, new HashMap<>(), null);
        Assertions.assertEquals(1, stopMonitorings.size());
        MonitoredStopVisit sm = stopMonitorings.getFirst();
        Assertions.assertEquals(1, sm.getMonitoredVehicleJourney().getDirectionNames().size());
        Assertions.assertEquals("A", sm.getMonitoredVehicleJourney().getDirectionNames().getFirst().getValue());
    }

    @Test
    void testGTFSRTTripUpdateToStopMonitoringMapperTest_with_direction_1() {
        GtfsRealtime.TripUpdate.Builder tripBuilder = GtfsRealtime.TripUpdate.newBuilder();
        GtfsRealtime.TripDescriptor.Builder tripDescBuild = GtfsRealtime.TripDescriptor.newBuilder();
        tripDescBuild.setTripId("tripId");
        tripDescBuild.setDirectionId(1);
        tripBuilder.setTrip(tripDescBuild);
        GtfsRealtime.TripUpdate.StopTimeUpdate.Builder stopTimeUpd = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder();
        stopTimeUpd.setStopId("stopId");
        stopTimeUpd.setStopSequence(0);
        GtfsRealtime.TripUpdate.StopTimeEvent.Builder ste = GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder();
        ste.setDelay(50);
        stopTimeUpd.setDeparture(ste.build());
        tripBuilder.addStopTimeUpdate(stopTimeUpd);

        List<String> routeIdList = Arrays.asList("".split(","));
        List<MonitoredStopVisit> stopMonitorings = tripUpdateMapper.mapStopVisitFromTripUpdate(tripBuilder.build(), "", routeIdList, PublishedLineNameMapping.LINE_NAME, new HashMap<>(), null);
        Assertions.assertEquals(1, stopMonitorings.size());
        MonitoredStopVisit sm = stopMonitorings.getFirst();
        Assertions.assertEquals(1, sm.getMonitoredVehicleJourney().getDirectionNames().size());
        Assertions.assertEquals("R", sm.getMonitoredVehicleJourney().getDirectionNames().getFirst().getValue());
    }

    @Test
    void testGTFSRTTripUpdateCancellationMapperTest() {
        GtfsRealtime.TripUpdate.Builder tripBuilder = GtfsRealtime.TripUpdate.newBuilder();
        GtfsRealtime.TripDescriptor.Builder tripDescBuild = GtfsRealtime.TripDescriptor.newBuilder();
        tripDescBuild.setTripId("tripId");
        tripDescBuild.setRouteId("routeId");
        tripDescBuild.setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED);
        tripBuilder.setTrip(tripDescBuild);
        GtfsRealtime.TripUpdate.StopTimeUpdate.Builder stopTimeUpd = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder();
        stopTimeUpd.setStopId("stopId");
        stopTimeUpd.setStopSequence(0);
        GtfsRealtime.TripUpdate.StopTimeEvent.Builder ste = GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder();
        ste.setDelay(50);
        stopTimeUpd.setDeparture(ste.build());
        tripBuilder.addStopTimeUpdate(stopTimeUpd);

        List<String> routeIdList = Arrays.asList("stopId".split(","));

        List<MonitoredStopVisitCancellation> monitoredStopVisitCancellations = tripUpdateMapper.mapStopCancellationFromTripUpdate(tripBuilder.build(), "test", routeIdList);
        Assertions.assertFalse(monitoredStopVisitCancellations.isEmpty());
        Assertions.assertEquals("test-tripId-stopId-routeId", monitoredStopVisitCancellations.getFirst().getItemRef().getValue());
        Assertions.assertEquals("routeId", monitoredStopVisitCancellations.getFirst().getLineRef().getValue());
        Assertions.assertEquals("tripId", monitoredStopVisitCancellations.getFirst().getVehicleJourneyRef().getDatedVehicleJourneyRef());
        Assertions.assertEquals("stopId", monitoredStopVisitCancellations.getFirst().getMonitoringRef().getValue());

        GtfsRealtime.TripUpdate.Builder tripBuilder2 = GtfsRealtime.TripUpdate.newBuilder();
        GtfsRealtime.TripDescriptor.Builder tripDescBuild2 = GtfsRealtime.TripDescriptor.newBuilder();
        tripDescBuild2.setTripId("tripId");
        tripDescBuild2.setRouteId("routeId");
        tripDescBuild2.setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED);
        tripBuilder2.setTrip(tripDescBuild2);
        GtfsRealtime.TripUpdate.StopTimeUpdate.Builder stopTimeUpd2 = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder();
        stopTimeUpd2.setStopId("stopId");
        stopTimeUpd2.setStopSequence(0);
        GtfsRealtime.TripUpdate.StopTimeEvent.Builder ste2 = GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder();
        ste2.setDelay(50);
        stopTimeUpd2.setDeparture(ste2.build());
        tripBuilder2.addStopTimeUpdate(stopTimeUpd2);

        List<MonitoredStopVisitCancellation> monitoredStopVisitCancellations2 = tripUpdateMapper.mapStopCancellationFromTripUpdate(tripBuilder2.build(), "test2", routeIdList);
        assertThat(monitoredStopVisitCancellations2).isEmpty();
    }

    @Test
    void test_mapStopVisitFromTripUpdate_whenShouldMapPublishedLineNameByLineNumberAndLineNumberIsInCache_thenAddLineNumberInPublishedLineNames() {
        // Arrange
        GtfsRealtime.TripUpdate.Builder tripBuilder = GtfsRealtime.TripUpdate.newBuilder();
        GtfsRealtime.TripDescriptor.Builder tripDescBuild = GtfsRealtime.TripDescriptor.newBuilder();
        tripDescBuild.setTripId("tripId");
        tripDescBuild.setRouteId("routeId");
        tripBuilder.setTrip(tripDescBuild);
        GtfsRealtime.TripUpdate.StopTimeUpdate.Builder stopTimeUpd = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder();
        stopTimeUpd.setStopId("stopId");
        stopTimeUpd.setStopSequence(0);
        GtfsRealtime.TripUpdate.StopTimeEvent.Builder ste = GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder();
        ste.setDelay(50);
        stopTimeUpd.setDeparture(ste.build());
        tripBuilder.addStopTimeUpdate(stopTimeUpd);

        List<String> routeIdList = Arrays.asList("stopId".split(","));

        IdProcessingParameters ipp = new IdProcessingParameters();
        ipp.setDatasetId("test");
        ipp.setOutputPrefixToAdd("TEST:Line:");
        ipp.setOutputSuffixToAdd(":LOC");


        when(lineUpdaterService.getLineNumber("TEST:Line:routeId:LOC")).thenReturn(Optional.of("1"));
        when(subscriptionConfig.getIdParametersForDataset("test", ObjectType.LINE)).thenReturn(Optional.of(ipp));

        // Act
        List<MonitoredStopVisit> output = tripUpdateMapper.mapStopVisitFromTripUpdate(tripBuilder.build(), "test", routeIdList, PublishedLineNameMapping.LINE_NUMBER, new HashMap<>(), null);

        // Assert
        Assertions.assertEquals("1", output.getFirst().getMonitoredVehicleJourney().getPublishedLineNames().getFirst().getValue());
    }

    @Test
    void test_mapStopVisitFromTripUpdate_whenShouldMapPublishedLineNameByLineNumberAndLineNumberIsNotInCache_thenDoesNotAddLineNumberInPublishedLineNames() {
        // Arrange
        GtfsRealtime.TripUpdate.Builder tripBuilder = GtfsRealtime.TripUpdate.newBuilder();
        GtfsRealtime.TripDescriptor.Builder tripDescBuild = GtfsRealtime.TripDescriptor.newBuilder();
        tripDescBuild.setTripId("tripId");
        tripDescBuild.setRouteId("routeId");
        tripBuilder.setTrip(tripDescBuild);
        GtfsRealtime.TripUpdate.StopTimeUpdate.Builder stopTimeUpd = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder();
        stopTimeUpd.setStopId("stopId");
        stopTimeUpd.setStopSequence(0);
        GtfsRealtime.TripUpdate.StopTimeEvent.Builder ste = GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder();
        ste.setDelay(50);
        stopTimeUpd.setDeparture(ste.build());
        tripBuilder.addStopTimeUpdate(stopTimeUpd);

        List<String> routeIdList = Arrays.asList("stopId".split(","));

        when(lineUpdaterService.getLineNumber("routeId")).thenReturn(Optional.empty());
        when(subscriptionConfig.getIdParametersForDataset("test", ObjectType.LINE)).thenReturn(Optional.empty());

        // Act
        List<MonitoredStopVisit> output = tripUpdateMapper.mapStopVisitFromTripUpdate(tripBuilder.build(), "test", routeIdList, PublishedLineNameMapping.LINE_NUMBER, new HashMap<>(), null);

        // Assert
        Assertions.assertTrue(CollectionUtils.isEmpty(output.getFirst().getMonitoredVehicleJourney().getPublishedLineNames()));
    }

    @ParameterizedTest
    @EnumSource(value = GtfsRealtime.VehiclePosition.OccupancyStatus.class, names = {
            "EMPTY",
            "MANY_SEATS_AVAILABLE",
            "FEW_SEATS_AVAILABLE",
            "STANDING_ROOM_ONLY",
            "CRUSHED_STANDING_ROOM_ONLY",
            "FULL",
            "NOT_ACCEPTING_PASSENGERS",
            "NO_DATA_AVAILABLE",
            "NOT_BOARDABLE"
    })
    void testOccupancyMappingAllCases(GtfsRealtime.VehiclePosition.OccupancyStatus gtfsStatus) {
        String dataset = "DAT1";
        GtfsRealtime.TripUpdate tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("test-trip").build())
                .addStopTimeUpdate(GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
                        .setStopId("stop-1")
                        .setStopSequence(1)
                        .build())
                .build();

        List<MonitoredStopVisit> results = tripUpdateMapper.mapStopVisitFromTripUpdate(
                tripUpdate,
                dataset,
                Collections.emptyList(),
                PublishedLineNameMapping.LINE_NAME,
                new HashMap<>(),
                gtfsStatus
        );

        MonitoredCallStructure monitoredCall = results.getFirst().getMonitoredVehicleJourney().getMonitoredCall();
        var occupancies = monitoredCall.getExpectedDepartureOccupancies();

        switch (gtfsStatus) {
            case EMPTY ->
                    Assertions.assertEquals(OccupancyEnumeration.EMPTY, occupancies.getFirst().getOccupancyLevel());
            case MANY_SEATS_AVAILABLE ->
                    Assertions.assertEquals(OccupancyEnumeration.MANY_SEATS_AVAILABLE, occupancies.getFirst().getOccupancyLevel());
            case FEW_SEATS_AVAILABLE ->
                    Assertions.assertEquals(OccupancyEnumeration.FEW_SEATS_AVAILABLE, occupancies.getFirst().getOccupancyLevel());
            case STANDING_ROOM_ONLY ->
                    Assertions.assertEquals(OccupancyEnumeration.STANDING_ROOM_ONLY, occupancies.getFirst().getOccupancyLevel());
            case CRUSHED_STANDING_ROOM_ONLY ->
                    Assertions.assertEquals(OccupancyEnumeration.CRUSHED_STANDING_ROOM_ONLY, occupancies.getFirst().getOccupancyLevel());
            case FULL ->
                    Assertions.assertEquals(OccupancyEnumeration.FULL, occupancies.getFirst().getOccupancyLevel());
            case NOT_ACCEPTING_PASSENGERS ->
                    Assertions.assertEquals(OccupancyEnumeration.NOT_ACCEPTING_PASSENGERS, occupancies.getFirst().getOccupancyLevel());
            case NO_DATA_AVAILABLE ->
                    Assertions.assertTrue(occupancies.isEmpty(), "La liste devrait être vide pour NO_DATA_AVAILABLE");
            default -> // Cas UNKNOWN (ex: NOT_BOARDABLE)
                    Assertions.assertEquals(OccupancyEnumeration.UNKNOWN, occupancies.getFirst().getOccupancyLevel());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "EMPTY, empty",
            "MANY_SEATS_AVAILABLE, manySeatsAvailable",
            "FEW_SEATS_AVAILABLE, fewSeatsAvailable",
            "STANDING_ROOM_ONLY, standingRoomOnly",
            "CRUSHED_STANDING_ROOM_ONLY, crushedStandingRoomOnly",
            "FULL, full",
            "NOT_ACCEPTING_PASSENGERS, notAcceptingPassengers"
    })
    void testETOccupancyMappingAllCases(String gtfsInput, String siriExpected) {
        GtfsRealtime.VehiclePosition.OccupancyStatus gtfsStatus = GtfsRealtime.VehiclePosition.OccupancyStatus.valueOf(gtfsInput);
        OccupancyEnumeration expectedSiri = OccupancyEnumeration.fromValue(siriExpected);

        GtfsRealtime.TripUpdate tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("trip-1").build())
                .addStopTimeUpdate(GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
                        .setStopId("stop-1")
                        .setStopSequence(1)
                        .build())
                .build();

        EstimatedVehicleJourney result = tripUpdateMapper.mapVehicleJourneyFromTripUpdate(
                tripUpdate, "dataset", Collections.emptyList(), PublishedLineNameMapping.LINE_NAME, gtfsStatus);

        EstimatedCall call = result.getEstimatedCalls().getEstimatedCalls().get(0);

        Assertions.assertFalse(call.getExpectedDepartureOccupancies().isEmpty(), "L'occupancy ne devrait pas être vide pour " + gtfsInput);
        OccupancyEnumeration actualSiri = call.getExpectedDepartureOccupancies().get(0).getOccupancyLevel();
        Assertions.assertEquals(expectedSiri, actualSiri, "Le mapping pour " + gtfsInput + " a échoué");
    }

    @Test
    void testETOccupancyMappingNoDataCase() {
        GtfsRealtime.TripUpdate tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("trip-1").build())
                .addStopTimeUpdate(GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder().setStopId("stop-1").build())
                .build();

        EstimatedVehicleJourney result = tripUpdateMapper.mapVehicleJourneyFromTripUpdate(
                tripUpdate, "dataset", Collections.emptyList(), PublishedLineNameMapping.LINE_NAME,
                GtfsRealtime.VehiclePosition.OccupancyStatus.NO_DATA_AVAILABLE);

        EstimatedCall call = result.getEstimatedCalls().getEstimatedCalls().get(0);
        assertThat(call.getExpectedDepartureOccupancies()).isEmpty();
    }

}
