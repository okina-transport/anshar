package no.rutebanken.anshar.gtfsRT;


import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.gtfsrt.mappers.VehiclePositionMapper;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.StopTimesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri20.VehicleActivityStructure;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VehiclePositionMapperTest extends SpringBootBaseTest {

    private static final String DATASET_ID = "BERTHELET";

    private VehiclePositionMapper vehiclePositionMapper;

    @BeforeEach
    public void setup() {
        vehiclePositionMapper = new VehiclePositionMapper(new StopTimesService());
    }

    @Test
    void testGTFSRTVehiclePositionMapperTest() {
        GtfsRealtime.TripUpdate.Builder tripBuilder = GtfsRealtime.TripUpdate.newBuilder();
        GtfsRealtime.TripDescriptor.Builder tripDescBuild = GtfsRealtime.TripDescriptor.newBuilder();
        tripDescBuild.setTripId("tripId");
        tripBuilder.setTrip(tripDescBuild);
        GtfsRealtime.VehiclePosition.Builder vehiclePosition = GtfsRealtime.VehiclePosition.newBuilder();
        GtfsRealtime.Position.Builder pos = GtfsRealtime.Position.newBuilder();
        pos.setLatitude(5.5f);
        pos.setLongitude(0.8f);
        vehiclePosition.setPosition(pos);

        List<String> routeIdList = Arrays.asList("".split(","));

        VehicleActivityStructure vehicleJourney = vehiclePositionMapper.mapVehicleActivityFromVehiclePosition(vehiclePosition.build(), DATASET_ID, routeIdList);
        assertThat(vehicleJourney).isNotNull();

    }


}
