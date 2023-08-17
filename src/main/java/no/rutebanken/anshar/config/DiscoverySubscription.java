package no.rutebanken.anshar.config;

import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;


/**
 * Parameters related to subscriptions that must be created dynamically by using provider's stop discovery service
 */
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

    public String getSubscriptionIdBase() {
        return subscriptionIdBase;
    }

    public void setSubscriptionIdBase(String subscriptionIdBase) {
        this.subscriptionIdBase = subscriptionIdBase;
    }

    public String getVendorBaseName() {
        return vendorBaseName;
    }

    public void setVendorBaseName(String vendorBaseName) {
        this.vendorBaseName = vendorBaseName;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public String getUrl() {
        return url;
    }

    public SiriDataType getDiscoveryType() {
        return discoveryType;
    }

    public void setDiscoveryType(SiriDataType discoveryType) {
        this.discoveryType = discoveryType;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getRequestorRef() {
        return requestorRef;
    }

    public void setRequestorRef(String requestorRef) {
        this.requestorRef = requestorRef;
    }

    public SubscriptionSetup.SubscriptionMode getSubscriptionMode() {
        return subscriptionMode;
    }

    public void setSubscriptionMode(SubscriptionSetup.SubscriptionMode subscriptionMode) {
        this.subscriptionMode = subscriptionMode;
    }

    public int getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    public void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }

    public int getChangeBeforeUpdatesSeconds() {
        return changeBeforeUpdatesSeconds;
    }

    public void setChangeBeforeUpdatesSeconds(int changeBeforeUpdatesSeconds) {
        this.changeBeforeUpdatesSeconds = changeBeforeUpdatesSeconds;
    }

    public int getUpdateIntervalSeconds() {
        return updateIntervalSeconds;
    }

    public void setUpdateIntervalSeconds(int updateIntervalSeconds) {
        this.updateIntervalSeconds = updateIntervalSeconds;
    }

    public int getPreviewIntervalSeconds() {
        return previewIntervalSeconds;
    }

    public void setPreviewIntervalSeconds(int previewIntervalSeconds) {
        this.previewIntervalSeconds = previewIntervalSeconds;
    }

    public Map<String, Object> getCustomHeaders() {
        return customHeaders;
    }

    public void setCustomHeaders(Map<String, Object> customHeaders) {
        this.customHeaders = customHeaders;
    }

    public int getDurationOfSubscriptionHours() {
        return durationOfSubscriptionHours;
    }

    public void setDurationOfSubscriptionHours(int durationOfSubscriptionHours) {
        this.durationOfSubscriptionHours = durationOfSubscriptionHours;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscoverySubscription that = (DiscoverySubscription) o;
        return Objects.equals(datasetId, that.datasetId) && Objects.equals(url, that.url)
                && Objects.equals(discoveryType, that.discoveryType)
                && Objects.equals(subscriptionMode, that.subscriptionMode)
                && Objects.equals(requestorRef, that.requestorRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datasetId, url, discoveryType, requestorRef, subscriptionMode);
    }
}
