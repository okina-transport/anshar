package no.rutebanken.anshar.gbfs;

import com.okina.gbfs.to.siri.StationStatusToSiriFmMapper;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.siri.handlers.inbound.FacilityMonitoringInbound;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mobilitydata.gbfs.v3_0.station_status.GBFSStationStatus;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.siri.siri21.FacilityConditionStructure;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GbfsIngesterTest {

    @Mock
    FacilityMonitoringInbound facilityMonitoringInbound;

    @Mock
    HealthManager healthManager;

    @Mock
    StationStatusToSiriFmMapper mapper;

    @InjectMocks
    GbfsIngester tested;

    private static final String DATASET_ID = "test";

    @Test
    void test_ingest_whenNoFacilityConditionIsMapped_thenDoNothing() {
        GBFSStationStatus  stationStatus = new GBFSStationStatus();
        List<FacilityConditionStructure> fcss = List.of();
        when(mapper.map(stationStatus)).thenReturn(fcss);

        tested.ingest(stationStatus, DATASET_ID);

        verify(mapper).map(stationStatus);
        verify(healthManager, never()).dataReceived();
        verify(facilityMonitoringInbound, never()).ingestFacilities(any(), any());
    }

    @Test
    void test_ingest_whenFacilityConditionsAreMapped_thenAddThemInCache() {
        GBFSStationStatus  stationStatus = new GBFSStationStatus();
        FacilityConditionStructure fcs = new FacilityConditionStructure();
        List<FacilityConditionStructure> fcss = List.of(fcs);
        when(mapper.map(stationStatus)).thenReturn(fcss);

        tested.ingest(stationStatus, DATASET_ID);

        verify(mapper).map(stationStatus);
        verify(healthManager).dataReceived();
        verify(facilityMonitoringInbound).ingestFacilities(eq(DATASET_ID), eq(fcss), anyLong());
    }


}
