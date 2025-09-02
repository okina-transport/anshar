package no.rutebanken.anshar.gbfs;

import com.okina.gbfs.to.siri.StationStatusToSiriFmMapper;
import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.data.FacilityMonitoring;
import no.rutebanken.anshar.routes.health.HealthManager;
import org.apache.commons.collections4.CollectionUtils;
import org.mobilitydata.gbfs.v3_0.station_status.GBFSStationStatus;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.FacilityConditionStructure;

import java.util.Collection;
import java.util.List;

@Service
@Slf4j
public class GbfsIngester {

    private final FacilityMonitoring facilityMonitoring;
    private final HealthManager healthManager;
    private final StationStatusToSiriFmMapper mapper;

    public GbfsIngester(FacilityMonitoring facilityMonitoring, HealthManager healthManager, StationStatusToSiriFmMapper mapper) {
        this.facilityMonitoring = facilityMonitoring;
        this.healthManager = healthManager;
        this.mapper = mapper;
    }

    public void ingest(GBFSStationStatus stationStatus, String datasetId) {
        List<FacilityConditionStructure> fcss = mapper.map(stationStatus);
        if (CollectionUtils.isEmpty(fcss)) {
            log.warn("No facility condition mapped from GBFS station station {} on dataset {}, abort ingesting SIRI FM from GBFS", stationStatus, datasetId);
            return;
        }

        healthManager.dataReceived();

        int nbStations = stationStatus == null || stationStatus.getData() == null ? 0 : CollectionUtils.size(stationStatus.getData().getStations());
        log.info("Mapped {} facility condition(s) from {} GBFS station status(es)", CollectionUtils.size(fcss), nbStations);
        Collection<FacilityConditionStructure> addedFcss = facilityMonitoring.addAll(datasetId, fcss);
        log.info("GBFS - Ingested facility conditions {} on {} (dataset: {})", CollectionUtils.size(addedFcss), CollectionUtils.size(fcss), datasetId);
    }
}
