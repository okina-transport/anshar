package no.rutebanken.anshar.gtfsRT;


import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.gtfsrt.mappers.TripUpdateMapper;
import no.rutebanken.anshar.gtfsrt.mappers.VehiclePositionMapper;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.VehicleActivityStructure;

import static junit.framework.Assert.assertTrue;


public class VehiclePositionMapperTest extends SpringBootBaseTest {

    private static String GTFS_RT_URL = "https://www.data.gouv.fr/fr/datasets/r/3bd20bc6-bfae-48d8-8785-6a34cf272df2";
    private static final int DEFAULT_HEARTBEAT_SECONDS = 300;



    @Test
    public void testGTFSRTVehiclePositionMapperTest() {
        GtfsRealtime.TripUpdate.Builder tripBuilder = GtfsRealtime.TripUpdate.newBuilder();
        GtfsRealtime.TripDescriptor.Builder tripDescBuild = GtfsRealtime.TripDescriptor.newBuilder();
        tripDescBuild.setTripId("tripId");
        tripBuilder.setTrip(tripDescBuild);
        GtfsRealtime.VehiclePosition.Builder vehiclePosition = GtfsRealtime.VehiclePosition.newBuilder();
        GtfsRealtime.Position.Builder pos = GtfsRealtime.Position.newBuilder();
        pos.setLatitude(5.5f);
        pos.setLongitude(0.8f);
        vehiclePosition.setPosition(pos);
        VehicleActivityStructure vehicleJourney = VehiclePositionMapper.mapVehicleActivityFromVehiclePosition(vehiclePosition.build());
        assertTrue(vehicleJourney != null);

    }


}
