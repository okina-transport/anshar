package no.rutebanken.anshar.config;

import lombok.Getter;
import lombok.Setter;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;

import java.io.InputStream;
import java.util.List;

/**
 * POJO Class to store incoming siri + its parameters
 * Incoming siri can be :
 * - inbound data (from GTFS-RT/SIRI subscriptions)
 * - outbound data (simple request/subscription/request, etc)
 */
@Setter
@Getter
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

}
