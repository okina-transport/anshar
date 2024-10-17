package no.rutebanken.anshar.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;

import java.io.Serializable;
import java.util.Map;


/**
 * Parameters related to subscriptions that must be created dynamically by using provider's stop discovery service
 */
@Data
@EqualsAndHashCode(of = {"datasetId", "url", "discoveryType", "subscriptionMode", "requestorRef"})
public class DiscoverySubscription implements Serializable {

    private String datasetId;
    private String url;
    private SiriDataType discoveryType;
    private String requestorRef;
    private int heartbeatIntervalSeconds;
    private int changeBeforeUpdatesSeconds;
    private int updateIntervalSeconds;
    private int previewIntervalSeconds;
    private Map<String, Object> customHeaders;
    private SubscriptionSetup.SubscriptionMode subscriptionMode;
    private int durationOfSubscriptionHours;
    private String vendorBaseName;
    private String subscriptionIdBase;

}
