package no.rutebanken.anshar.subscription;

import com.hazelcast.replicatedmap.ReplicatedMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class DatasetService {

    private final SubscriptionConfig subscriptionConfig;
    private final ReplicatedMap<String, SubscriptionSetup> subscriptions;

    public DatasetService(SubscriptionConfig subscriptionConfig, @Qualifier("getSubscriptionsMap") ReplicatedMap<String, SubscriptionSetup> subscriptions) {
        this.subscriptionConfig = subscriptionConfig;
        this.subscriptions = subscriptions;
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
        for (var siriApi : subscriptionConfig.getSiriApis()) {
            datasetIds.add(siriApi.getDatasetId());
        }

        for (var subscription : subscriptions.values()) {
            if (StringUtils.isNotEmpty(subscription.getDatasetId())) {
                datasetIds.add(subscription.getDatasetId());
            }
        }
        return datasetIds;
    }

    public boolean exists(String datasetId) {
        return getAllDatasetIds().contains(datasetId);
    }

}
