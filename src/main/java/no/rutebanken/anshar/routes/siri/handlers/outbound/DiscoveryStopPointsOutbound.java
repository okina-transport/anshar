package no.rutebanken.anshar.routes.siri.handlers.outbound;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.DiscoveryCache;
import no.rutebanken.anshar.routes.mapping.ExternalIdsService;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.AnnotatedStopPointStructure;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.StopPointRef;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DiscoveryStopPointsOutbound {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryStopPointsOutbound.class);

    @Autowired
    private ExternalIdsService externalIdsService;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private Utils utils;

    @Autowired
    private StopPlaceUpdaterService stopPlaceUpdaterService;

    @Autowired
    private DiscoveryCache discoveryCache;


    /**
     * Creates a siri response with all points existing in the cache
     *
     * @return the siri response with all points
     */
    public Siri getDiscoveryStopPoints(String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy) {

        if (datasetId == null && !OutboundIdMappingPolicy.DEFAULT.equals(outboundIdMappingPolicy)) {
            // no result if user chose original format without specifying a dataset
            return siriObjectFactory.createStopPointsDiscoveryDelivery(new ArrayList<>());
        }


        Map<String, Set<String>> stopsByDatasetId;

        if (datasetId == null) {
            stopsByDatasetId = discoveryCache.getDiscoveryStops();
        } else {
            stopsByDatasetId = new HashMap<>();
            stopsByDatasetId.put(datasetId, discoveryCache.getDiscoveryStopsForDataset(datasetId));
        }


        Set<String> datasetList = stopsByDatasetId.keySet();

        Map<String, IdProcessingParameters> idProcessingMap = utils.buildIdProcessingMap(datasetList, ObjectType.STOP);

        Set<String> monitoringRefList = new HashSet<>();

        for (Map.Entry<String, Set<String>> stopsByDatasetEntry : stopsByDatasetId.entrySet()) {
            //for each datasetId
            Set<String> stopList = stopsByDatasetEntry.getValue();

            if (stopList == null || stopList.isEmpty()) {
                continue;
            }
            monitoringRefList.addAll(extractAndTransformStopId(stopsByDatasetEntry.getKey(), stopList, idProcessingMap));
        }

        if (OutboundIdMappingPolicy.DEFAULT.equals(outboundIdMappingPolicy)) {
            monitoringRefList = monitoringRefList.stream()
                    .filter(stopPlaceUpdaterService::isKnownId)
                    .map(stopPlaceUpdaterService::get)
                    .collect(Collectors.toSet());

        } else if (OutboundIdMappingPolicy.ALT_ID.equals(outboundIdMappingPolicy) && datasetId != null) {
            monitoringRefList = monitoringRefList.stream()
                    .map(id -> externalIdsService.getAltId(datasetId, id, ObjectType.STOP).orElse(id))
                    .collect(Collectors.toSet());
        }


        List<AnnotatedStopPointStructure> resultList = monitoringRefList.stream()
                .map(this::convertKeyToPointStructure)
                .collect(Collectors.toList());

        return siriObjectFactory.createStopPointsDiscoveryDelivery(resultList);
    }


    private Set<String> extractAndTransformStopId(String datasetId, Set<String> stopIds, Map<String, IdProcessingParameters> idProcessingMap) {

        if (!idProcessingMap.containsKey(datasetId)) {
            //no idProcessingMap, no transformation
            return stopIds;
        }

        IdProcessingParameters idProcessing = idProcessingMap.get(datasetId);

        return stopIds.stream()
                .map(idProcessing::applyTransformationToString)
                .collect(Collectors.toSet());

    }

    /**
     * Converts a stop reference to an annotatedStopPointStructure
     *
     * @param stopRef the stop reference
     * @return the annotated stop point structure that will be included in siri response
     */
    private AnnotatedStopPointStructure convertKeyToPointStructure(String stopRef) {
        AnnotatedStopPointStructure pointStruct = new AnnotatedStopPointStructure();
        StopPointRef stopPointRef = new StopPointRef();
        stopPointRef.setValue(stopRef);
        pointStruct.setStopPointRef(stopPointRef);
        pointStruct.setMonitored(true);
        return pointStruct;
    }

    /**
     * Function to trace the list of points that are unknown from theorical data
     *
     * @param stopPointList The list of sto points id to check
     */
    private void traceUnknownStopPoints(List<String> stopPointList) {
        List<String> unknownPoints = stopPointList.stream()
                .filter(stopPointId -> stopPointId.startsWith("MOBIITI:Quay:"))
                .collect(Collectors.toList());

        logger.warn("These points were received in real-time data but are unknown from theorical data :" + unknownPoints.stream().collect(Collectors.joining(",")));

    }
}
