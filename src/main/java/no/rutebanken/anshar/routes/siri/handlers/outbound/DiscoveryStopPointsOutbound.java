package no.rutebanken.anshar.routes.siri.handlers.outbound;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.DiscoveryCache;
import no.rutebanken.anshar.routes.mapping.OutputExternalIdsService;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.AnnotatedStopPointStructure;
import uk.org.siri.siri21.NaturalLanguageStringStructure;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopPointRefStructure;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DiscoveryStopPointsOutbound {

    private final OutputExternalIdsService outputExternalIdsService;
    private final SiriObjectFactory siriObjectFactory;
    private final Utils utils;
    private final StopPlaceUpdaterService stopPlaceUpdaterService;
    private final DiscoveryCache discoveryCache;

    public DiscoveryStopPointsOutbound(OutputExternalIdsService outputExternalIdsService, SiriObjectFactory siriObjectFactory, Utils utils, StopPlaceUpdaterService stopPlaceUpdaterService, DiscoveryCache discoveryCache) {
        this.outputExternalIdsService = outputExternalIdsService;
        this.siriObjectFactory = siriObjectFactory;
        this.utils = utils;
        this.stopPlaceUpdaterService = stopPlaceUpdaterService;
        this.discoveryCache = discoveryCache;
    }

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
            //for each datasetId
            Set<String> stopList = stopsByDatasetEntry.getValue();
            String currentDatasetId = stopsByDatasetEntry.getKey();

            if (stopList == null || stopList.isEmpty()) {
                continue;
            }
            for (String originalStopId : stopList) {
                Optional<String> transformedStopId = extractAndTransformStopId(currentDatasetId, originalStopId,
                        idProcessingMap);
                if (transformedStopId.isPresent()) {
                    monitoringRefList.add(Pair.of(transformedStopId.get(),
                            stopPlaceUpdaterService.getStopName(originalStopId, currentDatasetId)));
                }
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
            result.add(Pair.of(outputExternalIdsService.getAltId(datasetId, stopRef, ObjectType.STOP).orElse(stopRef), stopRefAndName.getRight()));
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


    private Optional<String> extractAndTransformStopId(String datasetId, String stopId,
                                                       Map<String, IdProcessingParameters> idProcessingMap) {
        if (!idProcessingMap.containsKey(datasetId)) {
            //no idProcessingMap, no transformation
            return Optional.of(stopId);
        }
        IdProcessingParameters idProcessing = idProcessingMap.get(datasetId);

        String transformedId = idProcessing.applyTransformationToString(stopId);

        if (stopPlaceUpdaterService.isKnownId(transformedId)) {
            return Optional.of(transformedId);
        } else if (stopPlaceUpdaterService.isKnownId(transformedId.replace(":Quay:", ":StopPlace:"))) {
            return Optional.of(transformedId.replace(":Quay:", ":StopPlace:"));
        }

        return Optional.empty();
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


        if (StringUtils.isNotEmpty(stopRefAndName.getRight())) {
            NaturalLanguageStringStructure pointName = new NaturalLanguageStringStructure();
            pointName.setValue(stopRefAndName.getRight());
            pointStruct.getStopNames().add(pointName);
        }
        return pointStruct;
    }

}
