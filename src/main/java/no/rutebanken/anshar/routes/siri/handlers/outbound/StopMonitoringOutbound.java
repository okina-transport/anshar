package no.rutebanken.anshar.routes.siri.handlers.outbound;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.data.util.CustomStringUtils;
import no.rutebanken.anshar.routes.mapping.ExternalIdsService;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.helpers.StopMonitoringServiceDeliveryParameter;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import no.rutebanken.anshar.util.IDUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StopMonitoringOutbound {

    private static final Logger logger = LoggerFactory.getLogger(StopMonitoringOutbound.class);

    @Autowired
    StopPlaceUpdaterService stopPlaceUpdaterService;

    @Autowired
    ExternalIdsService externalIdsService;

    @Autowired
    MonitoredStopVisits monitoredStopVisits;

    @Autowired
    SiriObjectFactory siriObjectFactory;

    @Autowired
    SubscriptionConfig subscriptionConfig;

    /**
     * Converts netex Ids (MOBIITI:Quay:xxx) to imported Ids prefixed by producer (PROD123:Quay:xxx)
     *
     * @param originalMonitoringRefs
     * @return the converted ids
     */
    public Set<String> convertToImportedIds(Set<String> originalMonitoringRefs, String datasetId) {
        Set<String> importedIds = new HashSet<>();
        for (String originalMonitoringRef : originalMonitoringRefs) {
            if (StringUtils.isNotEmpty(datasetId) && StringUtils.isNotEmpty(originalMonitoringRef) && stopPlaceUpdaterService.canBeReverted(originalMonitoringRef, datasetId)) {
                importedIds.addAll(stopPlaceUpdaterService.getReverse(originalMonitoringRef, datasetId));
            } else if (StringUtils.isEmpty(datasetId) && stopPlaceUpdaterService.canBeRevertedWithoutDatasetId(originalMonitoringRef)) {
                importedIds.addAll(stopPlaceUpdaterService.getReverseWithoutDatasetId(originalMonitoringRef));
            } else {
                return new HashSet<>();
            }
        }

        return importedIds;
    }

    public Set<String> convertFromAltIdsToImportedIdsStop(Set<String> originalMonitoringRefs, String datasetId) {
        Set<String> importedIds = new HashSet<>();
        for (String originalMonitoringRef : originalMonitoringRefs) {
            if (StringUtils.isNotEmpty(datasetId) && StringUtils.isNotEmpty(originalMonitoringRef) && !externalIdsService.getReverseAltIdStop(datasetId, originalMonitoringRef).isEmpty()) {
                importedIds.addAll(externalIdsService.getReverseAltIdStop(datasetId, originalMonitoringRef));
            } else {
                // we need to keep original search to avoid search with empty stops and recover all stops from cache
                importedIds.add(originalMonitoringRef);
            }
        }
        return importedIds;
    }

    public Set<String> getMonitoringRefs(ServiceRequest serviceRequest) {
        Map<Class, Set<String>> filterMap = new HashMap<>();
        for (StopMonitoringRequestStructure req : serviceRequest.getStopMonitoringRequests()) {
            MonitoringRefStructure monitoringRef = req.getMonitoringRef();
            if (monitoringRef != null) {
                Set<String> monitoringRefs = filterMap.get(MonitoringRefStructure.class) != null ? filterMap.get(MonitoringRefStructure.class) : new HashSet<>();
                monitoringRefs.add(monitoringRef.getValue());
                filterMap.put(MonitoringRefStructure.class, monitoringRefs);
            }
        }
        return filterMap.get(MonitoringRefStructure.class) != null ? filterMap.get(MonitoringRefStructure.class) : new HashSet<>();
    }

    public Set<String> getImportedIds(OutboundIdMappingPolicy outboundIdMappingPolicy, Set<String> originalMonitoringRefs, String datasetId) {
        Set<String> importedIds;
        if (OutboundIdMappingPolicy.DEFAULT.equals(outboundIdMappingPolicy)) {
            importedIds = convertToImportedIds(originalMonitoringRefs, datasetId);
        } else if (OutboundIdMappingPolicy.ALT_ID.equals(outboundIdMappingPolicy)) {
            importedIds = convertFromAltIdsToImportedIdsStop(originalMonitoringRefs, datasetId);
        } else if (OutboundIdMappingPolicy.ORIGINAL_ID.equals(outboundIdMappingPolicy) && StringUtils.isEmpty(datasetId)) {
            importedIds = new HashSet<>();
        } else {
            importedIds = originalMonitoringRefs;
        }

        return importedIds;
    }

    public Siri getStopMonitoringServiceDelivery(StopMonitoringServiceDeliveryParameter stopMonitoringServiceDeliveryParameter) {
        ServiceRequest serviceRequest = stopMonitoringServiceDeliveryParameter.serviceRequest();
        Set<String> originalMonitoringRefs = getMonitoringRefs(serviceRequest);
        OutboundIdMappingPolicy outboundIdMappingPolicy = stopMonitoringServiceDeliveryParameter.incomingSiriParameters().getOutboundIdMappingPolicy();
        String datasetId = stopMonitoringServiceDeliveryParameter.incomingSiriParameters().getDatasetId();
        String clientTrackingName = stopMonitoringServiceDeliveryParameter.incomingSiriParameters().getClientTrackingName();
        String messageId = serviceRequest.getMessageIdentifier() != null ? serviceRequest.getMessageIdentifier().getValue() : null;
        String requestorRef = null;
        if (serviceRequest.getRequestorRef() != null) {
            requestorRef = serviceRequest.getRequestorRef().getValue();
        }
        Set<String> importedIds = getImportedIds(outboundIdMappingPolicy, originalMonitoringRefs, datasetId);

        List<Siri> siriList = new ArrayList<>();
        Siri serviceResponse;
        if (StringUtils.isEmpty(datasetId) && outboundIdMappingPolicy.equals(OutboundIdMappingPolicy.DEFAULT) && !importedIds.isEmpty()) {
            Set<String> datasetIds = monitoredStopVisits.getAllDatasetIds();

            for (String datasetIdFromList : datasetIds) {
                serviceResponse = getServiceResponseStopVisits(datasetIdFromList, importedIds, stopMonitoringServiceDeliveryParameter);
                if (CollectionUtils.isNotEmpty(serviceResponse.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits())) {
                    siriList.add(serviceResponse);
                }
            }

            if (CollectionUtils.isNotEmpty(siriList)) {
                List<MonitoredStopVisit> stopVisits = new ArrayList<>();
                for (Siri siri : siriList) {
                    stopVisits.addAll(siri.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits());
                }
                serviceResponse = siriObjectFactory.createSMServiceDelivery(stopVisits, requestorRef, messageId);

            } else {
                serviceResponse = siriObjectFactory.createSMServiceDelivery(new ArrayList<>(), requestorRef, messageId);
            }

        } else if (StringUtils.isNotEmpty(datasetId) && outboundIdMappingPolicy.equals(OutboundIdMappingPolicy.DEFAULT)
                && CollectionUtils.isEmpty(importedIds) && CollectionUtils.isNotEmpty(originalMonitoringRefs)) {
            serviceResponse = siriObjectFactory.createSMServiceDelivery(new ArrayList<>(), requestorRef, messageId);
        } else {
            serviceResponse = getServiceResponseStopVisits(datasetId, importedIds, stopMonitoringServiceDeliveryParameter);
        }

        logger.debug("Asking for service delivery for requestorId={}, monitoringRef={}, clientTrackingName={}, datasetId={}", requestorRef, String.join("|", originalMonitoringRefs), clientTrackingName, datasetId);

        return serviceResponse;
    }

    public Siri getServiceResponseStopVisits(String datasetId, Set<String> importedIds, StopMonitoringServiceDeliveryParameter parameter) {
        Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParams(datasetId, importedIds, ObjectType.STOP);
        Set<String> revertedMonitoringRefs = IDUtils.revertMonitoringRefs(importedIds, idMap.get(ObjectType.STOP));
        revertedMonitoringRefs = revertedMonitoringRefs.stream()
                .map(CustomStringUtils::revertChouetteIdTransformation)
                .collect(Collectors.toSet());


        IncomingSiriParameters incomingSiriParameters = parameter.incomingSiriParameters();
        String messageId = parameter.serviceRequest().getMessageIdentifier() != null ? parameter.serviceRequest().getMessageIdentifier().getValue() : null;
        String requestorRef = null;
        if (parameter.serviceRequest().getRequestorRef() != null) {
            requestorRef = parameter.serviceRequest().getRequestorRef().getValue();
        }
        List<ValueAdapter> valueAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.STOP_MONITORING, incomingSiriParameters.getOutboundIdMappingPolicy(), idMap);
        Siri serviceResponse = monitoredStopVisits.createServiceDelivery(requestorRef, datasetId, incomingSiriParameters.getMaxSize(), revertedMonitoringRefs, messageId, incomingSiriParameters.isTheoreticalDataExcluded(), -1);
        return SiriValueTransformer.transform(serviceResponse, valueAdapters, false, false);
    }

    /**
     * Build a map with all data that must be send by scheduled Notification sender
     *
     * @return Map<String, Siri>
     * key : datasetId
     * value : Siri that contains all data linked to searchedStops
     */
    public Map<String, Siri> getScheduledDeliveryToSend(Set<String> stopsToSearch) {
        Map<String, Siri> results = new HashMap<>();
        Set<String> datasetIds = monitoredStopVisits.getAllDatasetIds();

        for (String datasetId : datasetIds) {
            Siri datasetResults = monitoredStopVisits.createServiceDelivery("SCHEDULED_DELIVERY", datasetId, Integer.MAX_VALUE, stopsToSearch, null, false, -1);
            if (datasetResults.getServiceDelivery() != null && datasetResults.getServiceDelivery().getStopMonitoringDeliveries() != null
                    && !datasetResults.getServiceDelivery().getStopMonitoringDeliveries().isEmpty()) {
                results.put(datasetId, datasetResults);
            }
        }
        return results;
    }
}
