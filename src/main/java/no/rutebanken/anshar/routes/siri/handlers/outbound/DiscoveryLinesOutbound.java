package no.rutebanken.anshar.routes.siri.handlers.outbound;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.DiscoveryCache;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.routes.mapping.ExternalIdsService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.AnnotatedLineRef;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.Siri;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DiscoveryLinesOutbound {

    @Autowired
    private ExternalIdsService externalIdsService;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private Utils utils;

    @Autowired
    private DiscoveryCache discoveryCache;

    /**
     * Creates a siri response with all lines existing in the cache, for vehicle Monitoring
     *
     * @return the siri response with all points
     */
    public Siri getDiscoveryLines(String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy) {

        List<SiriDataType> siriDataTypes = new ArrayList<>();
        siriDataTypes.add(SiriDataType.VEHICLE_MONITORING);

        Map<String, Set<String>> linesByDataset;

        if (datasetId == null) {
            linesByDataset = discoveryCache.getDiscoveryLines();
        } else {
            linesByDataset = new HashMap<>();
            linesByDataset.put(datasetId, discoveryCache.getDiscoveryLinesForDataset(datasetId));
        }

        Set<String> datasetList = linesByDataset.keySet();


        Map<String, IdProcessingParameters> idProcessingMap = utils.buildIdProcessingMap(datasetList, ObjectType.LINE);

        Set<String> lineRefSetVM = new HashSet<>();


        for (Map.Entry<String, Set<String>> linesByDatasetEntry : linesByDataset.entrySet()) {
            //for each datasetId
            Set<String> lineList = linesByDatasetEntry.getValue();
            if (lineList == null || lineList.isEmpty()) {
                continue;
            }

            lineRefSetVM.addAll(extractAndTransformLineId(linesByDatasetEntry.getKey(), lineList, idProcessingMap));
        }


        if (OutboundIdMappingPolicy.ALT_ID.equals(outboundIdMappingPolicy) && datasetId != null) {
            lineRefSetVM = lineRefSetVM.stream()
                    .map(id -> externalIdsService.getAltId(datasetId, id, ObjectType.LINE).orElse(id))
                    .collect(Collectors.toSet());
        }

        Set<String> lineRefSetET = estimatedTimetables.getAll(datasetId)
                .stream()
                .filter(estimatedVehicleJourney -> estimatedVehicleJourney.getLineRef() != null)
                .map(estimatedVehicleJourney -> {
                            String lineRef = estimatedVehicleJourney.getLineRef().getValue();
                            return idProcessingMap.containsKey(datasetId) ? idProcessingMap.get(datasetId).applyTransformationToString(lineRef) : lineRef;
                        }
                )
                .collect(Collectors.toSet());

        if (OutboundIdMappingPolicy.ALT_ID.equals(outboundIdMappingPolicy) && datasetId != null) {
            lineRefSetET = lineRefSetET.stream()
                    .map(id -> externalIdsService.getAltId(datasetId, id, ObjectType.LINE).orElse(id))
                    .collect(Collectors.toSet());
        }

        Set<String> lineRefSet = new HashSet<>();
        lineRefSet.addAll(lineRefSetVM);
        lineRefSet.addAll(lineRefSetET);

        List<AnnotatedLineRef> resultList = lineRefSet.stream()
                .map(this::convertKeyToLineRef)
                .collect(Collectors.toList());

        return siriObjectFactory.createLinesDiscoveryDelivery(resultList);

    }

    /**
     * Extract a lineId from a subscriptionSetup and transforms it, with idProcessingParams
     *
     * @param subscriptionSetup the subscriptionSetup for which the stop id must be recovered
     * @param idProcessingMap   the map that associate datasetId to idProcessingParams
     * @return the transformed line id
     */
    private List<String> extractAndTransformLineId(SubscriptionSetup subscriptionSetup, Map<String, IdProcessingParameters> idProcessingMap) {
        List<String> results = new ArrayList<>();

        for (String lineRefValue : subscriptionSetup.getLineRefValues()) {
            String datasetId = subscriptionSetup.getDatasetId();
            results.add(idProcessingMap.containsKey(datasetId) ? idProcessingMap.get(datasetId).applyTransformationToString(lineRefValue) : lineRefValue);
        }

        return results;
    }

    private Set<String> extractAndTransformLineId(String datasetId, Set<String> lineIds, Map<String, IdProcessingParameters> idProcessingMap) {
        if (!idProcessingMap.containsKey(datasetId)) {
            //no idProcessingMap, no transformation
            return lineIds;
        }

        IdProcessingParameters idProcessing = idProcessingMap.get(datasetId);

        return lineIds.stream()
                .map(idProcessing::applyTransformationToString)
                .collect(Collectors.toSet());
    }

    /**
     * Converts a line reference to an annotatedLineRef
     *
     * @param lineRefStr the line reference
     * @return the annotated lineref that will be included in siri response
     */
    private AnnotatedLineRef convertKeyToLineRef(String lineRefStr) {
        AnnotatedLineRef annotatedLineRef = new AnnotatedLineRef();
        LineRef lineRefSiri = new LineRef();
        lineRefSiri.setValue(lineRefStr);
        annotatedLineRef.setMonitored(true);
        annotatedLineRef.setLineRef(lineRefSiri);
        return annotatedLineRef;
    }
}
