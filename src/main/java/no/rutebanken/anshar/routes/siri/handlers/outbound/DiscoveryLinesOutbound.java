package no.rutebanken.anshar.routes.siri.handlers.outbound;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.DiscoveryCache;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.routes.mapping.ExternalIdsService;
import no.rutebanken.anshar.routes.mapping.LineUpdaterService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.AnnotatedLineRef;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.NaturalLanguageStringStructure;
import uk.org.siri.siri21.Siri;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DiscoveryLinesOutbound {

    @Autowired
    private ExternalIdsService externalIdsService;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private Utils utils;

    @Autowired
    private DiscoveryCache discoveryCache;

    @Autowired
    private LineUpdaterService lineUpdaterService;

    /**
     * Creates a siri response with all lines existing in the cache, for vehicle Monitoring
     *
     * @return the siri response with all points
     */
    public Siri getDiscoveryLines(String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy) {
        Map<String, Set<String>> linesByDataset;

        if (datasetId == null) {
            linesByDataset = discoveryCache.getDiscoveryLines();
        } else {
            linesByDataset = new HashMap<>();
            linesByDataset.put(datasetId, discoveryCache.getDiscoveryLinesForDataset(datasetId));
        }

        Map<String, IdProcessingParameters> idProcessingMap = utils.buildIdProcessingMapByObjectType(ObjectType.LINE);


        Set<Pair<String, String>> lineRefSetVM = new HashSet<>();

        for (Map.Entry<String, Set<String>> linesByDatasetEntry : linesByDataset.entrySet()) {
            Set<String> rawLines = new HashSet<>();
            //for each datasetId
            Set<String> lineList = linesByDatasetEntry.getValue();
            if (lineList == null || lineList.isEmpty()) {
                continue;
            }

            rawLines.addAll(extractAndTransformLineId(linesByDatasetEntry.getKey(), lineList, idProcessingMap));

            for (String rawLine : rawLines) {
                lineRefSetVM.add(Pair.of(rawLine, lineUpdaterService.getLineName(rawLine).orElse(null)));
            }
        }


        if (OutboundIdMappingPolicy.ALT_ID.equals(outboundIdMappingPolicy) && datasetId != null) {
            lineRefSetVM = replaceByAltId(lineRefSetVM, datasetId);
        }

        Set<String> rawLinesET = estimatedTimetables.getAll(datasetId)
                .stream()
                .filter(estimatedVehicleJourney -> estimatedVehicleJourney.getLineRef() != null)
                .map(estimatedVehicleJourney -> {
                            String lineRef = estimatedVehicleJourney.getLineRef().getValue();
                            return idProcessingMap.containsKey(datasetId) ? idProcessingMap.get(datasetId).applyTransformationToString(lineRef) : lineRef;
                        }
                )
                .collect(Collectors.toSet());


        Set<Pair<String, String>> lineRefSetET = new HashSet<>();

        for (String rawLine : rawLinesET) {
            lineRefSetET.add(Pair.of(rawLine, lineUpdaterService.getLineName(rawLine).orElse(null)));
        }

        if (OutboundIdMappingPolicy.ALT_ID.equals(outboundIdMappingPolicy) && datasetId != null) {
            lineRefSetET = replaceByAltId(lineRefSetET, datasetId);
        }

        Set<Pair<String, String>> lineRefSet = new HashSet<>();
        lineRefSet.addAll(lineRefSetVM);
        lineRefSet.addAll(lineRefSetET);

        List<AnnotatedLineRef> resultList = lineRefSet.stream()
                .map(this::convertKeyToLineRef)
                .collect(Collectors.toList());

        return siriObjectFactory.createLinesDiscoveryDelivery(resultList);

    }

    private Set<Pair<String, String>> replaceByAltId(Set<Pair<String, String>> lineRefAndNames, String datasetId) {
        Set<Pair<String, String>> result = new HashSet<>();
        for (Pair<String, String> lineRefAndName : lineRefAndNames) {
            String lineRef = lineRefAndName.getLeft();
            result.add(Pair.of(externalIdsService.getAltId(datasetId, lineRef, ObjectType.LINE).orElse(lineRef), lineRefAndName.getRight()));
        }

        return result;

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
     * @param lineRefAndName left : line ref
     *                       right : line name
     * @return the annotated lineref that will be included in siri response
     */
    private AnnotatedLineRef convertKeyToLineRef(Pair<String, String> lineRefAndName) {
        AnnotatedLineRef annotatedLineRef = new AnnotatedLineRef();
        LineRef lineRefSiri = new LineRef();
        lineRefSiri.setValue(lineRefAndName.getLeft());
        annotatedLineRef.setMonitored(true);
        annotatedLineRef.setLineRef(lineRefSiri);
        if (StringUtils.isNotEmpty(lineRefAndName.getRight())) {
            NaturalLanguageStringStructure lineName = new NaturalLanguageStringStructure();
            lineName.setValue(lineRefAndName.getRight());
            annotatedLineRef.getLineNames().add(lineName);
        }
        return annotatedLineRef;
    }
}
