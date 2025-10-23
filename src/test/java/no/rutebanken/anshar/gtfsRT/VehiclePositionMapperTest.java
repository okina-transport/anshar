package no.rutebanken.anshar.gtfsRT;


import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.gtfsrt.mappers.VehiclePositionMapper;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.StopTimesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri20.VehicleActivityStructure;

import java.time.Instant;
import java.util.Collections;
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
        Instant trueInstant = Instant.parse("2025-10-21T10:36:40Z");
        Instant falseInstant = Instant.parse("1970-01-21T09:10:43Z");
        long expectedTimestampMillis = 1761043000;

        GtfsRealtime.TripDescriptor.Builder tripDescBuild = GtfsRealtime.TripDescriptor.newBuilder();
        tripDescBuild.setTripId("tripId");

        GtfsRealtime.Position.Builder pos = GtfsRealtime.Position.newBuilder();
        pos.setLatitude(5.5f);
        pos.setLongitude(0.8f);

        GtfsRealtime.VehiclePosition.Builder vehiclePosition = GtfsRealtime.VehiclePosition.newBuilder();

        vehiclePosition.setTimestamp(expectedTimestampMillis);
        vehiclePosition.setTrip(tripDescBuild);
        vehiclePosition.setPosition(pos);

        List<String> routeIdList = Collections.emptyList();

        VehicleActivityStructure vehicleJourney = vehiclePositionMapper.mapVehicleActivityFromVehiclePosition(
                vehiclePosition.build(),
                DATASET_ID,
                routeIdList
        );

        assertThat(vehicleJourney).isNotNull();

        assertThat(vehicleJourney.getRecordedAtTime().toInstant().getEpochSecond())
                .isEqualTo(expectedTimestampMillis);

        assertThat(vehicleJourney.getRecordedAtTime().toInstant().getEpochSecond()).isEqualTo(trueInstant.getEpochSecond());
        assertThat(vehicleJourney.getRecordedAtTime().toInstant().getEpochSecond()).isNotEqualTo(falseInstant.getEpochSecond());

        assertThat(vehicleJourney.getMonitoredVehicleJourney().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef())
                .isEqualTo("tripId");

    }


}
