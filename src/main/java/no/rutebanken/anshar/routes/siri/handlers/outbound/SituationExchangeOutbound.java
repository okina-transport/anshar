package no.rutebanken.anshar.routes.siri.handlers.outbound;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.processor.SxPublishingActionFilterPostProcessor;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.Siri;

import java.util.*;

@Service
public class SituationExchangeOutbound {

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private Situations situations;

    @Autowired
    SiriObjectFactory siriObjectFactory;

    public List<ValueAdapter> getValueAdapters(String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy, String sxPublishingName) {
        List<ValueAdapter> valueAdapters;
        Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);
        // creating a new list to avoid additional adapters to be cached
        valueAdapters = new ArrayList<>(MappingAdapterPresets.getOutboundAdapters(SiriDataType.SITUATION_EXCHANGE, outboundIdMappingPolicy, idMap));
        if (StringUtils.isNotBlank(sxPublishingName)) {
            valueAdapters.add(new SxPublishingActionFilterPostProcessor(sxPublishingName));
        }
        return valueAdapters;
    }

    private void removeSxPublishingActionFilterProcessor(List<ValueAdapter> valueAdapters) {
        if (CollectionUtils.isEmpty(valueAdapters)) {
            return;
        }
        valueAdapters.removeIf(valueAdapter -> valueAdapter instanceof SxPublishingActionFilterPostProcessor);
    }

    public Siri createServiceDelivery(String requestorRef, String datasetId, String clientTrackingName, OutboundIdMappingPolicy outboundIdMappingPolicy, int maxSize, String messageId, String sxPublishingActionName) {
        Set<String> datasetToRequest = StringUtils.isEmpty(datasetId) ? situations.getAllDatasetIds() : new HashSet<>(Arrays.asList(datasetId));
        return createServiceDelivery(requestorRef, datasetToRequest.stream().toList(), clientTrackingName, outboundIdMappingPolicy, maxSize, messageId, sxPublishingActionName);
    }

    public Siri createServiceDelivery(String requestorRef, List<String> datasetToRequest, String clientTrackingName, OutboundIdMappingPolicy outboundIdMappingPolicy, int maxSize,
                                      String messageId, String sxPublishingActionName) {
        List<Siri> results = new ArrayList<>();
        Siri serviceResponse;

        if (datasetToRequest.isEmpty()) {
            datasetToRequest = situations.getAllDatasetIds().stream().toList();
        }

        for (String datasetIdToRequest : datasetToRequest) {
            Siri datasetResults = getTransformedSiriForDataset(datasetIdToRequest, outboundIdMappingPolicy, requestorRef, clientTrackingName, maxSize, messageId, sxPublishingActionName);
            results.add(datasetResults);
        }

        if (!results.isEmpty()) {
            List<PtSituationElement> situations = new ArrayList<>();
            for (Siri siri : results) {
                situations.addAll(siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements());
            }
            serviceResponse = siriObjectFactory.createSXServiceDelivery(situations, requestorRef, null);
        } else {
            serviceResponse = siriObjectFactory.createSXServiceDelivery(new ArrayList<>(), requestorRef, null);
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
    private Siri getTransformedSiriForDataset(String datasetIdToRequest, OutboundIdMappingPolicy outboundIdMappingPolicy, String requestorRef, String clientTrackingName, int maxSize,
                                              String messageId, String sxPublishingActionName) {
        List<ValueAdapter> valueAdapters = getValueAdapters(datasetIdToRequest, outboundIdMappingPolicy, sxPublishingActionName);
        Siri serviceResponse = situations.createServiceDelivery(requestorRef, datasetIdToRequest, clientTrackingName, maxSize, messageId);
        return SiriValueTransformer.transform(serviceResponse, valueAdapters, false, false);
    }


}
