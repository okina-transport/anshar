package no.rutebanken.anshar.routes.siri.handlers.outbound;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.mapping.ExternalIdsService;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.AnnotatedStopPointStructure;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.StopPointRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DiscoveryStopPointsOutbound {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryStopPointsOutbound.class);

    @Autowired
    private ExternalIdsService externalIdsService;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private Utils utils;

    @Autowired
    private StopPlaceUpdaterService stopPlaceUpdaterService;


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

        List<SubscriptionSetup> subscriptionList = subscriptionManager.getAllSubscriptions(SiriDataType.STOP_MONITORING).stream()
                .filter(subscriptionSetup -> (datasetId == null || subscriptionSetup.getDatasetId().equals(datasetId)))
                .collect(Collectors.toList());

        List<String> datasetList = subscriptionList.stream()
                .map(SubscriptionSetup::getDatasetId)
                .distinct()
                .collect(Collectors.toList());

        Map<String, IdProcessingParameters> idProcessingMap = utils.buildIdProcessingMap(datasetList, ObjectType.STOP);


        List<String> monitoringRefList = subscriptionList.stream()
                .map(subscription -> extractAndTransformStopId(subscription, idProcessingMap))
                .flatMap(List::stream)
                .collect(Collectors.toList());

        if (OutboundIdMappingPolicy.DEFAULT.equals(outboundIdMappingPolicy)) {
            monitoringRefList = monitoringRefList.stream()
                    .filter(stopPlaceUpdaterService::isKnownId)
                    .map(stopPlaceUpdaterService::get)
                    .collect(Collectors.toList());

        } else if (OutboundIdMappingPolicy.ALT_ID.equals(outboundIdMappingPolicy) && datasetId != null) {
            monitoringRefList = monitoringRefList.stream()
                    .map(id -> externalIdsService.getAltId(datasetId, id, ObjectType.STOP).orElse(id))
                    .collect(Collectors.toList());
        }


        //  traceUnknownStopPoints(monitoringRefList);

        List<AnnotatedStopPointStructure> resultList = monitoringRefList.stream()
                .map(this::convertKeyToPointStructure)
                .collect(Collectors.toList());

        return siriObjectFactory.createStopPointsDiscoveryDelivery(resultList);
    }

    /**
     * Extract a stopId from a subscriptionSetup and transforms it, with idProcessingParams
     *
     * @param subscriptionSetup the subscriptionSetup for which the stop id must be recovered
     * @param idProcessingMap   the map that associate datasetId to idProcessingParams
     * @return the transformed stop id
     */
    private List<String> extractAndTransformStopId(SubscriptionSetup subscriptionSetup, Map<String, IdProcessingParameters> idProcessingMap) {
        List<String> stopIds = subscriptionSetup.getStopMonitoringRefValues();
        String datasetId = subscriptionSetup.getDatasetId();
        List<String> results = new ArrayList<>();

        for (String stopId : stopIds) {
            results.add(idProcessingMap.containsKey(datasetId) ? idProcessingMap.get(datasetId).applyTransformationToString(stopId) : stopId);
        }

        return results;
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
