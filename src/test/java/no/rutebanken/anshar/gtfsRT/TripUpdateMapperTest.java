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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.MonitoredStopVisit;
import uk.org.siri.siri21.MonitoredStopVisitCancellation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    void testGTFSRTTripUpdateMapperTest() {
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

        List<String> routeIdList = Arrays.asList("".split(","));

        EstimatedVehicleJourney vehicleJourney = tripUpdateMapper.mapVehicleJourneyFromTripUpdate(tripBuilder.build(), "", routeIdList);
        assertThat(vehicleJourney).isNotNull();

    }

    @Test
    void testGTFSRTTripUpdateToStopMonitoringMapperTest_without_direction() {
        String dataset = "DAT1";
        String tripId = "tripId";
        when(stopTimesService.getDirectionId(dataset, tripId)).thenReturn(Optional.of("A"));


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

        List<String> routeIdList = Arrays.asList("".split(","));
        List<MonitoredStopVisit> stopMonitorings = tripUpdateMapper.mapStopVisitFromTripUpdate(tripBuilder.build(), dataset, routeIdList, PublishedLineNameMapping.LINE_NAME, new HashMap<>());
        Assertions.assertEquals(1, stopMonitorings.size());
        MonitoredStopVisit sm = stopMonitorings.getFirst();
        Assertions.assertEquals(1, sm.getMonitoredVehicleJourney().getDirectionNames().size());
        Assertions.assertEquals("A", sm.getMonitoredVehicleJourney().getDirectionNames().getFirst().getValue());
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
        List<MonitoredStopVisit> stopMonitorings = tripUpdateMapper.mapStopVisitFromTripUpdate(tripBuilder.build(), "", routeIdList, PublishedLineNameMapping.LINE_NAME, new HashMap<>());
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
        List<MonitoredStopVisit> stopMonitorings = tripUpdateMapper.mapStopVisitFromTripUpdate(tripBuilder.build(), "", routeIdList, PublishedLineNameMapping.LINE_NAME, new HashMap<>());
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
        List<MonitoredStopVisit> output = tripUpdateMapper.mapStopVisitFromTripUpdate(tripBuilder.build(), "test", routeIdList, PublishedLineNameMapping.LINE_NUMBER, new HashMap<>());

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
        List<MonitoredStopVisit> output = tripUpdateMapper.mapStopVisitFromTripUpdate(tripBuilder.build(), "test", routeIdList, PublishedLineNameMapping.LINE_NUMBER, new HashMap<>());

        // Assert
        Assertions.assertTrue(CollectionUtils.isEmpty(output.getFirst().getMonitoredVehicleJourney().getPublishedLineNames()));
    }

}
