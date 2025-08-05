package no.rutebanken.anshar.gtfsRT;


import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.gtfsrt.mappers.TripUpdateMapper;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.mapping.StopTimesService;
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
        List<MonitoredStopVisit> stopMonitorings = tripUpdateMapper.mapStopVisitFromTripUpdate(tripBuilder.build(), dataset, routeIdList);
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
        List<MonitoredStopVisit> stopMonitorings = tripUpdateMapper.mapStopVisitFromTripUpdate(tripBuilder.build(), "", routeIdList);
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
        List<MonitoredStopVisit> stopMonitorings = tripUpdateMapper.mapStopVisitFromTripUpdate(tripBuilder.build(), "", routeIdList);
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
        Assertions.assertEquals("test-tripId-stopId-routeId", monitoredStopVisitCancellations.get(0).getItemRef().getValue());
        Assertions.assertEquals("routeId", monitoredStopVisitCancellations.get(0).getLineRef().getValue());
        Assertions.assertEquals("tripId", monitoredStopVisitCancellations.get(0).getVehicleJourneyRef().getDatedVehicleJourneyRef());
        Assertions.assertEquals("stopId", monitoredStopVisitCancellations.get(0).getMonitoringRef().getValue());

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
}
