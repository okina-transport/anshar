package no.rutebanken.anshar.subscription;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class DatasetService {

    private final SubscriptionConfig subscriptionConfig;

    public DatasetService(SubscriptionConfig subscriptionConfig) {
        this.subscriptionConfig = subscriptionConfig;
    }

    /**
     * @return all datasetIds handled by ANSHAR
     */
    public Set<String> getAllDatasetIds() {
        Set<String> datasetIds = new HashSet<>();
        for (var ipp : subscriptionConfig.getIdProcessingParameters()) {
            datasetIds.add(ipp.getDatasetId());
        }
        for (var ss : subscriptionConfig.getSubscriptions()) {
            datasetIds.add(ss.getDatasetId());
        }
        for (var ds : subscriptionConfig.getDiscoverySubscriptions()) {
            datasetIds.add(ds.getDatasetId());
        }
        for (var gtfsRtApi : subscriptionConfig.getGtfsRTApis()) {
            datasetIds.add(gtfsRtApi.getDatasetId());
        }
        for (var siriApi : subscriptionConfig.getSiriApis()) {
            datasetIds.add(siriApi.getDatasetId());
        }
        return datasetIds;
    }

    public boolean exists(String datasetId) {
        return getAllDatasetIds().contains(datasetId);
    }

}
