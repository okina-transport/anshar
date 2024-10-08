package no.rutebanken.anshar.routes.siri.handlers.outbound;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.DiscoveryCache;
import no.rutebanken.anshar.routes.mapping.ExternalIdsService;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.AnnotatedStopPointStructure;
import uk.org.siri.siri21.NaturalLanguageStringStructure;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopPointRefStructure;

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

        Set<Pair<String, String>> monitoringRefList = new HashSet<>();


        for (Map.Entry<String, Set<String>> stopsByDatasetEntry : stopsByDatasetId.entrySet()) {
            Set<String> rawStops = new HashSet<>();
            //for each datasetId
            Set<String> stopList = stopsByDatasetEntry.getValue();
            String currentDatasetId = stopsByDatasetEntry.getKey();

            if (stopList == null || stopList.isEmpty()) {
                continue;
            }
            rawStops.addAll(extractAndTransformStopId(currentDatasetId, stopList, idProcessingMap));

            for (String rawStop : rawStops) {
                monitoringRefList.add(Pair.of(rawStop, stopPlaceUpdaterService.getStopName(rawStop, currentDatasetId)));
            }
        }


        if (OutboundIdMappingPolicy.DEFAULT.equals(outboundIdMappingPolicy)) {
            monitoringRefList = replaceByDefaultId(monitoringRefList);
        } else if (OutboundIdMappingPolicy.ALT_ID.equals(outboundIdMappingPolicy) && datasetId != null) {
            monitoringRefList = replaceByAltId(monitoringRefList, datasetId);
        }

        List<AnnotatedStopPointStructure> resultList = monitoringRefList.stream()
                .map(this::convertKeyToPointStructure)
                .collect(Collectors.toList());

        return siriObjectFactory.createStopPointsDiscoveryDelivery(resultList);
    }

    private Set<Pair<String, String>> replaceByAltId(Set<Pair<String, String>> monitoringRefList, String datasetId) {
        Set<Pair<String, String>> result = new HashSet<>();
        for (Pair<String, String> stopRefAndName : monitoringRefList) {
            String stopRef = stopRefAndName.getLeft();
            result.add(Pair.of(externalIdsService.getAltId(datasetId, stopRef, ObjectType.STOP).orElse(stopRef), stopRefAndName.getRight()));
        }
        return result;
    }

    private Set<Pair<String, String>> replaceByDefaultId(Set<Pair<String, String>> monitoringRefList) {

        Set<Pair<String, String>> result = new HashSet<>();
        for (Pair<String, String> stopRefAndName : monitoringRefList) {
            String stopRef = stopRefAndName.getLeft();
            if (stopPlaceUpdaterService.isKnownId(stopRef)) {
                result.add(Pair.of(stopPlaceUpdaterService.get(stopRef), stopRefAndName.getRight()));
            } else {
                result.add(stopRefAndName);
            }
        }
        return result;
    }


    private Set<String> extractAndTransformStopId(String datasetId, Set<String> stopIds, Map<String, IdProcessingParameters> idProcessingMap) {

        if (!idProcessingMap.containsKey(datasetId)) {
            //no idProcessingMap, no transformation
            return stopIds;
        }

        Set<String> results = new HashSet<>();

        IdProcessingParameters idProcessing = idProcessingMap.get(datasetId);

        Set<String> transformedIds = stopIds.stream()
                .map(idProcessing::applyTransformationToString)
                .collect(Collectors.toSet());

        for (String transformedId : transformedIds) {
            if (stopPlaceUpdaterService.isKnownId(transformedId)) {
                results.add(transformedId);
            } else if (stopPlaceUpdaterService.isKnownId(transformedId.replace(":Quay:", ":StopPlace:"))) {
                results.add(transformedId.replace(":Quay:", ":StopPlace:"));
            }
        }
        return results;
    }

    /**
     * Converts a stop reference to an annotatedStopPointStructure
     *
     * @param stopRefAndName left : stop ref
     *                       right : stop name
     * @return the annotated stop point structure that will be included in siri response
     */
    private AnnotatedStopPointStructure convertKeyToPointStructure(Pair<String, String> stopRefAndName) {


        AnnotatedStopPointStructure pointStruct = new AnnotatedStopPointStructure();
        StopPointRefStructure stopPointRef = new StopPointRefStructure();
        stopPointRef.setValue(stopRefAndName.getLeft());
        pointStruct.setStopPointRef(stopPointRef);
        pointStruct.setMonitored(true);

        if (StringUtils.isNotEmpty(stopRefAndName.getRight())) {
            NaturalLanguageStringStructure pointName = new NaturalLanguageStringStructure();
            pointName.setValue(stopRefAndName.getRight());
            pointStruct.getStopNames().add(pointName);
        }
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
