package no.rutebanken.anshar.routes.siri.handlers.outbound;

import io.micrometer.core.instrument.util.StringUtils;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.routes.mapping.ExternalIdsService;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import no.rutebanken.anshar.util.IDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.MonitoredStopVisit;
import uk.org.siri.siri20.MonitoringRefStructure;
import uk.org.siri.siri20.ServiceRequest;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.StopMonitoringRequestStructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
        for(String originalMonitoringRef : originalMonitoringRefs){
            if(StringUtils.isNotEmpty(datasetId) && StringUtils.isNotEmpty(originalMonitoringRef) && stopPlaceUpdaterService.canBeReverted(originalMonitoringRef, datasetId)){
                importedIds.addAll(stopPlaceUpdaterService.getReverse(originalMonitoringRef, datasetId));
            }
            else if (StringUtils.isEmpty(datasetId) && stopPlaceUpdaterService.canBeRevertedWithoutDatasetId(originalMonitoringRef)){
                importedIds.addAll(stopPlaceUpdaterService.getReverseWithoutDatasetId(originalMonitoringRef));
            }
            else {
                return new HashSet<>();
            }
        }

        return importedIds;
    }

    public Set<String> convertFromAltIdsToImportedIdsStop(Set<String> originalMonitoringRefs, String datasetId) {
        Set<String> importedIds = new HashSet<>();
        for(String originalMonitoringRef : originalMonitoringRefs) {
            if (StringUtils.isNotEmpty(datasetId) && StringUtils.isNotEmpty(originalMonitoringRef) && !externalIdsService.getReverseAltIdStop(datasetId, originalMonitoringRef).isEmpty()) {
                importedIds.addAll(externalIdsService.getReverseAltIdStop(datasetId, originalMonitoringRef));
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
        } else if (OutboundIdMappingPolicy.ORIGINAL_ID.equals(outboundIdMappingPolicy) && StringUtils.isEmpty(datasetId)){
            importedIds = new HashSet<>();
        }
        else{
            importedIds = originalMonitoringRefs;
        }

        return importedIds;
    }

    public Siri getStopMonitoringServiceDelivery(ServiceRequest serviceRequest, OutboundIdMappingPolicy outboundIdMappingPolicy, String datasetId, List<ValueAdapter> valueAdapters, Siri serviceResponse, String requestorRef, String clientTrackingName, int maxSize){
        Set<String> originalMonitoringRefs = getMonitoringRefs(serviceRequest);
        Set<String> importedIds = getImportedIds(outboundIdMappingPolicy, originalMonitoringRefs, datasetId);

        List<Siri> siriList = new ArrayList<>();
        if(StringUtils.isEmpty(datasetId) && outboundIdMappingPolicy.equals(OutboundIdMappingPolicy.DEFAULT) && !importedIds.isEmpty()){
            Set<String> datasetIds = monitoredStopVisits.getAllDatasetIds();
            for(String datasetIdFromList : datasetIds){
                serviceResponse = getServiceResponseStopVisits(outboundIdMappingPolicy, requestorRef, clientTrackingName, maxSize, datasetIdFromList, importedIds);
                if(!serviceResponse.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().isEmpty()){
                    siriList.add(serviceResponse);
                }
            }
            if(!siriList.isEmpty()){
                List<MonitoredStopVisit> stopVisits = new ArrayList<>();
                for(Siri siri : siriList){
                    stopVisits.addAll(siri.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits());
                }
                serviceResponse = siriObjectFactory.createSMServiceDelivery(stopVisits);
            }
            else {
                serviceResponse = siriObjectFactory.createSMServiceDelivery(new ArrayList<>());
            }

        }
        else if(StringUtils.isNotEmpty(datasetId) && outboundIdMappingPolicy.equals(OutboundIdMappingPolicy.DEFAULT) && importedIds.isEmpty() && !originalMonitoringRefs.isEmpty()){
            serviceResponse = siriObjectFactory.createSMServiceDelivery(new ArrayList<>());
        }
        else {
            serviceResponse = getServiceResponseStopVisits(outboundIdMappingPolicy, requestorRef, clientTrackingName, maxSize, datasetId, importedIds);
        }

        logger.debug("Asking for service delivery for requestorId={}, monitoringRef={}, clientTrackingName={}, datasetId={}", requestorRef, String.join("|", originalMonitoringRefs), clientTrackingName, datasetId);

        return serviceResponse;
    }

    private Siri getServiceResponseStopVisits(OutboundIdMappingPolicy outboundIdMappingPolicy, String requestorRef, String clientTrackingName, int maxSize, String datasetId, Set<String> importedIds) {
        Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParams(datasetId, importedIds, ObjectType.STOP);
        Set<String> revertedMonitoringRefs = IDUtils.revertMonitoringRefs(importedIds, idMap.get(ObjectType.STOP));
        List<ValueAdapter> valueAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.STOP_MONITORING, outboundIdMappingPolicy, idMap);
        Siri serviceResponse = monitoredStopVisits.createServiceDelivery(requestorRef, datasetId, clientTrackingName, maxSize, revertedMonitoringRefs);
        return SiriValueTransformer.transform(serviceResponse, valueAdapters, false, false);
    }
}
