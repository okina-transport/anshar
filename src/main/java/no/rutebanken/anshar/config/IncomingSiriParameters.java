package no.rutebanken.anshar.config;

import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;

import java.io.InputStream;
import java.util.List;

/**
 * POJO Class to store incoming siri + its parameters
 * Incoming siri can be :
 * - inbound data (from GTFS-RT/SIRI subscriptions)
 * - outbound data (simple request/subscription/request, etc)
 */
public class IncomingSiriParameters {

    // the incoming Siri
    private InputStream incomingSiriStream;

    //parameters
    private String subscriptionId;
    private String datasetId;
    private List<String> excludedDatasetIdList;
    private OutboundIdMappingPolicy outboundIdMappingPolicy;
    private int maxSize;
    private String clientTrackingName;
    private boolean soapTransformation;
    private boolean useOriginalId;
    private String version;


    public static IncomingSiriParameters buildFromSubscription(String subscriptionId, InputStream incomingSiriStream) {
        IncomingSiriParameters incomingSiriParameters = new IncomingSiriParameters();
        incomingSiriParameters.setSubscriptionId(subscriptionId);
        incomingSiriParameters.setIncomingSiriStream(incomingSiriStream);
        return incomingSiriParameters;
    }

    public InputStream getIncomingSiriStream() {
        return incomingSiriStream;
    }

    public void setIncomingSiriStream(InputStream incomingSiriStream) {
        this.incomingSiriStream = incomingSiriStream;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public List<String> getExcludedDatasetIdList() {
        return excludedDatasetIdList;
    }

    public void setExcludedDatasetIdList(List<String> excludedDatasetIdList) {
        this.excludedDatasetIdList = excludedDatasetIdList;
    }

    public OutboundIdMappingPolicy getOutboundIdMappingPolicy() {
        return outboundIdMappingPolicy;
    }

    public void setOutboundIdMappingPolicy(OutboundIdMappingPolicy outboundIdMappingPolicy) {
        this.outboundIdMappingPolicy = outboundIdMappingPolicy;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public String getClientTrackingName() {
        return clientTrackingName;
    }

    public void setClientTrackingName(String clientTrackingName) {
        this.clientTrackingName = clientTrackingName;
    }

    public boolean isSoapTransformation() {
        return soapTransformation;
    }

    public void setSoapTransformation(boolean soapTransformation) {
        this.soapTransformation = soapTransformation;
    }

    public boolean isUseOriginalId() {
        return useOriginalId;
    }

    public void setUseOriginalId(boolean useOriginalId) {
        this.useOriginalId = useOriginalId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
