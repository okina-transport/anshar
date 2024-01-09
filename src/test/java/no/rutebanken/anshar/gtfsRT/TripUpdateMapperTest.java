package no.rutebanken.anshar.gtfsRT;


import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.gtfsrt.mappers.TripUpdateMapper;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.MonitoredStopVisitCancellation;

import java.util.List;

import static junit.framework.Assert.assertTrue;


public class TripUpdateMapperTest extends SpringBootBaseTest {

    private static String GTFS_RT_URL = "https://www.data.gouv.fr/fr/datasets/r/3bd20bc6-bfae-48d8-8785-6a34cf272df2";
    private static final int DEFAULT_HEARTBEAT_SECONDS = 300;

    @Autowired
    private TripUpdateMapper tripUpdateMapper;


    @Test
    public void testGTFSRTTripUpdateMapperTest() {


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

        EstimatedVehicleJourney vehicleJourney = TripUpdateMapper.mapVehicleJourneyFromTripUpdate(tripBuilder.build());
        assertTrue(vehicleJourney != null);

    }

    @Test
    public void testGTFSRTTripUpdateCancellationMapperTest() {


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

        List<MonitoredStopVisitCancellation> monitoredStopVisitCancellations = tripUpdateMapper.mapStopCancellationFromTripUpdate(tripBuilder.build(), "test");
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

        List<MonitoredStopVisitCancellation> monitoredStopVisitCancellations2 = tripUpdateMapper.mapStopCancellationFromTripUpdate(tripBuilder2.build(), "test2");
        Assertions.assertTrue(monitoredStopVisitCancellations2.isEmpty());
    }
}
