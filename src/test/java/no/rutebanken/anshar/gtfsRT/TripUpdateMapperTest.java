package no.rutebanken.anshar.gtfsRT;


import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.gtfsrt.mappers.AlertMapper;
import no.rutebanken.anshar.gtfsrt.mappers.TripUpdateMapper;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.PtSituationElement;

import static junit.framework.Assert.assertTrue;


public class TripUpdateMapperTest extends SpringBootBaseTest {

    private static String GTFS_RT_URL = "https://www.data.gouv.fr/fr/datasets/r/3bd20bc6-bfae-48d8-8785-6a34cf272df2";
    private static final int DEFAULT_HEARTBEAT_SECONDS = 300;


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

}
