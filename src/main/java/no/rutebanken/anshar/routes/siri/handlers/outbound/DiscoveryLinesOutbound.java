package no.rutebanken.anshar.routes.siri.handlers.outbound;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.DiscoveryCache;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.routes.mapping.ExternalIdsService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.AnnotatedLineRef;
import uk.org.siri.siri21.LineRef;
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
