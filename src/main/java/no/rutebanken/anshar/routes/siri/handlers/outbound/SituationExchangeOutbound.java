package no.rutebanken.anshar.routes.siri.handlers.outbound;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.Siri;

import java.util.*;

@Service
public class SituationExchangeOutbound {

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private Situations situations;

    @Autowired
    SiriObjectFactory siriObjectFactory;

    public List<ValueAdapter> getValueAdapters(String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy) {
        List<ValueAdapter> valueAdapters;
        Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);
        valueAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.SITUATION_EXCHANGE, outboundIdMappingPolicy, idMap);
        return valueAdapters;
    }

    public Siri createServiceDelivery(String requestorRef, String datasetId, String clientTrackingName, OutboundIdMappingPolicy outboundIdMappingPolicy, int maxSize) {

        Set<String> datasetToRequest = StringUtils.isEmpty(datasetId) ? subscriptionConfig.getSXDatasetIds() : new HashSet<>(Arrays.asList(datasetId));
        List<Siri> results = new ArrayList<>();
        Siri serviceResponse;

        for (String datasetIdToRequest : datasetToRequest) {
            Siri datasetResults = getTransformedSiriForDataset(datasetIdToRequest, outboundIdMappingPolicy, requestorRef, clientTrackingName, maxSize);
            results.add(datasetResults);
        }

        if (!results.isEmpty()) {
            List<PtSituationElement> situations = new ArrayList<>();
            for (Siri siri : results) {
                situations.addAll(siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements());
            }
            serviceResponse = siriObjectFactory.createSXServiceDelivery(situations);
        } else {
            serviceResponse = siriObjectFactory.createSXServiceDelivery(new ArrayList<>());
        }


        return serviceResponse;
    }

    /**
     * Request SX cache for specific dataset and transforms ids depending on the dataset
     *
     * @param datasetIdToRequest      the dataset for which data is requested
     * @param outboundIdMappingPolicy the outbound id preference
     * @param requestorRef            requestor
     * @param clientTrackingName      the client name
     * @param maxSize                 max size of the delivery
     * @return a siri with transformed ids
     */
    private Siri getTransformedSiriForDataset(String datasetIdToRequest, OutboundIdMappingPolicy outboundIdMappingPolicy, String requestorRef, String clientTrackingName, int maxSize) {
        List<ValueAdapter> valueAdapters = getValueAdapters(datasetIdToRequest, outboundIdMappingPolicy);
        Siri serviceResponse = situations.createServiceDelivery(requestorRef, datasetIdToRequest, clientTrackingName, maxSize);
        return SiriValueTransformer.transform(serviceResponse, valueAdapters, false, false);
    }


}
