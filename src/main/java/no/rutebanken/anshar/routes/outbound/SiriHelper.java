/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.*;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.util.IDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.*;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
@Component
public class SiriHelper {

    private static final Logger logger = LoggerFactory.getLogger(SiriHelper.class);


    @Autowired
    private Situations situations;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private MonitoredStopVisits monitoredStopVisits;

    @Autowired
    private GeneralMessages generalMessages;

    @Autowired
    private StopPlaceUpdaterService stopPlaceUpdaterService;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    private final SiriObjectFactory siriObjectFactory;

    public SiriHelper(@Autowired SiriObjectFactory siriObjectFactory) {
        this.siriObjectFactory = siriObjectFactory;
    }


    public Map<Class, Set<String>> getFilter(SubscriptionRequest subscriptionRequest, OutboundIdMappingPolicy outboundIdMappingPolicy, String datasetId) {
        if (containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {
            return getFilter(subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0));
        } else if (containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            return getFilter(subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0));
        } else if (containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            return getFilter(subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0));
        } else if (containsValues(subscriptionRequest.getStopMonitoringSubscriptionRequests())) {
            return getFilter(subscriptionRequest.getStopMonitoringSubscriptionRequests().get(0), outboundIdMappingPolicy, datasetId);
        }else if (containsValues(subscriptionRequest.getGeneralMessageSubscriptionRequests())) {
            return getFilter(subscriptionRequest.getGeneralMessageSubscriptionRequests().get(0), outboundIdMappingPolicy, datasetId);
        }

        return new HashMap<>();
    }

    private Map<Class, Set<String>> getFilter(GeneralMessageSubscriptionStructure generalMessageSubscriptionStructure, OutboundIdMappingPolicy outboundIdMappingPolicy, String datasetId) {

        Map<Class, Set<String>> filterMap = new HashMap<>();

        List<InfoChannelRefStructure> requestedChennels = generalMessageSubscriptionStructure.getGeneralMessageRequest().getInfoChannelReves();


        Set<String> requestedChannels = requestedChennels.stream()
                                                .map(InfoChannelRefStructure::getValue)
                                                .collect(Collectors.toSet());

        Map<ObjectType, Optional<IdProcessingParameters>> idProcessingParams = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);



        if (!requestedChannels.isEmpty()) {
            filterMap.put(InfoChannel.class, requestedChannels);
        }

        return filterMap;


    }

    private Map<Class, Set<String>> getFilter(SituationExchangeSubscriptionStructure subscriptionStructure) {
        SituationExchangeRequestStructure situationExchangeRequest = subscriptionStructure.getSituationExchangeRequest();

        Map<Class, Set<String>> filterMap = new HashMap<>();
        List<LineRef> lineReves = situationExchangeRequest.getLineReves();
        if (lineReves != null) {
            Set<String> linerefValues = new HashSet<>();

            lineReves.forEach(ref ->
                            linerefValues.add(ref.getValue())
            );

            filterMap.put(LineRef.class, linerefValues);
        }
        return filterMap;
    }


    private Map<Class, Set<String>> getFilter(VehicleMonitoringSubscriptionStructure subscriptionStructure) {
        VehicleMonitoringRequestStructure vehicleMonitoringRequest = subscriptionStructure.getVehicleMonitoringRequest();

        Map<Class, Set<String>> filterMap = new HashMap<>();

        LineRef lineRef = vehicleMonitoringRequest.getLineRef();
        if (lineRef != null) {

            Set<String> linerefValues = new HashSet<>();
            linerefValues.add(lineRef.getValue());

            filterMap.put(LineRef.class, linerefValues);
        }
        return filterMap;
    }

    private Map<Class, Set<String>> getFilter(EstimatedTimetableSubscriptionStructure subscriptionStructure) {
        EstimatedTimetableRequestStructure request = subscriptionStructure.getEstimatedTimetableRequest();

        Map<Class, Set<String>> filterMap = new HashMap<>();
        Set<String> linerefValues = new HashSet<>();

        EstimatedTimetableRequestStructure.Lines lines = request.getLines();
        if (lines != null) {
            List<LineDirectionStructure> lineDirections = lines.getLineDirections();
            if (lineDirections != null) {
                for (LineDirectionStructure lineDirection : lineDirections) {
                    if (lineDirection.getLineRef() != null) {
                        linerefValues.add(lineDirection.getLineRef().getValue());
                    }
                }
            }

        }

        if (!linerefValues.isEmpty()) {
            filterMap.put(LineRef.class, linerefValues);
        }
        return filterMap;
    }

    /**
     * Returns optional filters for stopMonitoringSubscription
     *
     * @param stopMonitoringSubscription
     * @return
     */
    public Map<Class, Set<String>> getFilter(StopMonitoringSubscriptionStructure stopMonitoringSubscription, OutboundIdMappingPolicy outboundIdMappingPolicy, String datasetId) {
        Map<Class, Set<String>> filterMap = new HashMap<>();
        Set<String> stopPointRefValues = new HashSet<>();


        String requestedId = stopMonitoringSubscription.getStopMonitoringRequest().getMonitoringRef().getValue();
        if (OutboundIdMappingPolicy.DEFAULT.equals(outboundIdMappingPolicy)){
            requestedId = stopPlaceUpdaterService.canBeReverted(requestedId, datasetId) ? stopPlaceUpdaterService.getReverse(requestedId, datasetId) : requestedId;
        }

        HashSet<String> requestedIds = new HashSet<>(Arrays.asList(requestedId));

        Map<ObjectType, Optional<IdProcessingParameters>> idProcessingParams = subscriptionConfig.buildIdProcessingParams(null, requestedIds, ObjectType.STOP);

        Set<String> revertedMonitoringRefs = IDUtils.revertMonitoringRefs(requestedIds, idProcessingParams.get(ObjectType.STOP));

        if (!revertedMonitoringRefs.isEmpty()) {
            stopPointRefValues.add(revertedMonitoringRefs.iterator().next());
            filterMap.put(MonitoringRefStructure.class, stopPointRefValues);
        }

        return filterMap;
    }


    public Map<ObjectType, Optional<IdProcessingParameters>> getIdProcessingParamsFromSubscription(StopMonitoringSubscriptionStructure stopMonitoringSubscription, OutboundIdMappingPolicy outboundIdMappingPolicy, String datasetId) {
        Map<Class, Set<String>> filterMap = new HashMap<>();
        Set<String> stopPointRefValues = new HashSet<>();


        String requestedId = stopMonitoringSubscription.getStopMonitoringRequest().getMonitoringRef().getValue();
        if (OutboundIdMappingPolicy.DEFAULT.equals(outboundIdMappingPolicy)){
            requestedId = stopPlaceUpdaterService.canBeReverted(requestedId, datasetId) ? stopPlaceUpdaterService.getReverse(requestedId, datasetId) : requestedId;
        }

        HashSet<String> requestedIds = new HashSet<>(Arrays.asList(requestedId));

        return subscriptionConfig.buildIdProcessingParams(null, requestedIds, ObjectType.STOP);
    }



    Siri findInitialDeliveryData(OutboundSubscriptionSetup subscriptionRequest) {
        Siri delivery = null;

        switch (subscriptionRequest.getSubscriptionType()) {

            case SITUATION_EXCHANGE:
                Collection<PtSituationElement> situationElementList = situations.getAll(subscriptionRequest.getDatasetId());
                logger.info("Initial SX-delivery: {} elements", situationElementList.size());
                delivery = siriObjectFactory.createSXServiceDelivery(situationElementList);
                break;

            case VEHICLE_MONITORING:
                Collection<VehicleActivityStructure> vehicleActivityList = vehicleActivities.getAll(subscriptionRequest.getDatasetId());
                logger.info("Initial VM-delivery: {} elements", vehicleActivityList.size());
                delivery = siriObjectFactory.createVMServiceDelivery(vehicleActivityList);
                break;

            case ESTIMATED_TIMETABLE:
                Collection<EstimatedVehicleJourney> timetables = estimatedTimetables.getAll(subscriptionRequest.getDatasetId());
                logger.info("Initial ET-delivery: {} elements", timetables.size());
                delivery = siriObjectFactory.createETServiceDelivery(timetables);
                break;

            case STOP_MONITORING:
                Collection<MonitoredStopVisit> stopVisits = monitoredStopVisits.getAll(subscriptionRequest.getDatasetId());
                logger.info("Initial SM-delivery: {} elements", stopVisits.size());
                delivery = siriObjectFactory.createSMServiceDelivery(stopVisits);
                break;

            case GENERAL_MESSAGE:
                Collection<GeneralMessage> messages = generalMessages.getAll(subscriptionRequest.getDatasetId());
                logger.info("Initial GM-delivery: {} elements", messages.size());
                delivery = siriObjectFactory.createGMServiceDelivery(messages);
                break;


        }
        return delivery;
    }

    public List<Siri> splitDeliveries(Siri payload, int maximumSizePerDelivery) {

        List<Siri> siriList = new ArrayList<>();

        if (payload.getServiceDelivery() == null) {
            siriList.add(payload);
            return siriList;
        }

        if (containsValues(payload.getServiceDelivery().getSituationExchangeDeliveries())) {

            List<PtSituationElement> situationElementList = payload.getServiceDelivery()
                    .getSituationExchangeDeliveries().get(0)
                    .getSituations()
                    .getPtSituationElements();

            List<List> sxList = splitList(situationElementList, maximumSizePerDelivery);

            for (List<PtSituationElement> list : sxList) {
                siriList.add(siriObjectFactory.createSXServiceDelivery(list));
            }

        } else if (containsValues(payload.getServiceDelivery().getVehicleMonitoringDeliveries())) {

            List<VehicleActivityStructure> vehicleActivities = payload.getServiceDelivery()
                    .getVehicleMonitoringDeliveries().get(0)
                    .getVehicleActivities();

            List<List> vmList = splitList(vehicleActivities, maximumSizePerDelivery);

            for (List<VehicleActivityStructure> list : vmList) {
                siriList.add(siriObjectFactory.createVMServiceDelivery(list));
            }

        } else if (containsValues(payload.getServiceDelivery().getEstimatedTimetableDeliveries())) {

            List<EstimatedVehicleJourney> timetables = payload.getServiceDelivery()
                    .getEstimatedTimetableDeliveries().get(0)
                    .getEstimatedJourneyVersionFrames().get(0)
                    .getEstimatedVehicleJourneies();

            List<List> etList = splitList(timetables, maximumSizePerDelivery);

            for (List<EstimatedVehicleJourney> list : etList) {
                siriList.add(siriObjectFactory.createETServiceDelivery(list));
            }

        } else if (containsValues(payload.getServiceDelivery().getStopMonitoringDeliveries())) {

            List<MonitoredStopVisit> monitoredStopVisits = payload.getServiceDelivery()
                    .getStopMonitoringDeliveries().get(0)
                    .getMonitoredStopVisits();

            List<List> etList = splitList(monitoredStopVisits, maximumSizePerDelivery);

            for (List<MonitoredStopVisit> list : etList) {
                siriList.add(siriObjectFactory.createSMServiceDelivery(list));
            }
        }else if (containsValues(payload.getServiceDelivery().getGeneralMessageDeliveries())) {

            List<GeneralMessage> generalMsgList = payload.getServiceDelivery()
                                    .getGeneralMessageDeliveries().get(0)
                                    .getGeneralMessages();

            List<List> gmList = splitList(generalMsgList, maximumSizePerDelivery);

            for (List<GeneralMessage> list : gmList) {
                siriList.add(siriObjectFactory.createGMServiceDelivery(list));
            }

        }

        return siriList;
    }

    private List<List> splitList(List fullList, int maximumSizePerDelivery) {
        int startIndex = 0;
        int endIndex = Math.min(startIndex + maximumSizePerDelivery, fullList.size());

        List<List> list = new ArrayList<>();
        boolean hasMoreElements = true;
        while(hasMoreElements) {

            list.add(fullList.subList(startIndex, endIndex));
            if (endIndex >= fullList.size()) {
                hasMoreElements = false;
            }
            startIndex += maximumSizePerDelivery;
            endIndex = Math.min(startIndex + maximumSizePerDelivery, fullList.size());
        }
        return list;
    }

    static boolean containsValues(List list) {
        return (list != null && !list.isEmpty());
    }


    public static Siri filterSiriPayload(Siri siri, Map<Class, Set<String>> filter) {
        return filterSiriPayload(siri,filter, true);
    }
    public static Siri filterSiriPayload(Siri siri, Map<Class, Set<String>> filter, boolean shouldPerformDeepCopy) {
        if (filter == null || filter.isEmpty()) {
            logger.debug("No filter to apply");
            return siri;
        }

        if (siri.getServiceDelivery() != null) {

            Siri filtered;
            if (shouldPerformDeepCopy){
                try {
                    filtered = SiriObjectFactory.deepCopy(siri);
                } catch (Exception e) {
                    return siri;
                }
            }else{
                filtered = siri;
            }


            if (containsValues(filtered.getServiceDelivery().getVehicleMonitoringDeliveries()) |
                    containsValues(filtered.getServiceDelivery().getEstimatedTimetableDeliveries())) {
                return applySingleMatchFilter(filtered, filter);
            } else if (containsValues(filtered.getServiceDelivery().getSituationExchangeDeliveries())) {
                return applyMultipleMatchFilter(filtered, filter);
            } else if (containsValues(filtered.getServiceDelivery().getStopMonitoringDeliveries())) {
                return applySingleMatchFilter(filtered, filter);
            } else if (containsValues(filtered.getServiceDelivery().getGeneralMessageDeliveries())){
                return applyGeneralMessageFilter(filtered, filter);
            }
        }

        return siri;
    }

    private static Siri applyGeneralMessageFilter(Siri filtered, Map<Class, Set<String>> filter) {

        Set<String> channels = filter.get(InfoChannel.class);
        if (channels == null || channels.isEmpty()) {
            return filtered;
        }

        for (GeneralMessageDeliveryStructure generalMessageDelivery : filtered.getServiceDelivery().getGeneralMessageDeliveries()) {

            List<GeneralMessage> filteredGeneralMessages = new ArrayList<>();
            for (GeneralMessage generalMessage : generalMessageDelivery.getGeneralMessages()) {

                if (channels.contains(generalMessage.getInfoChannelRef().getValue())){
                    filteredGeneralMessages.add(generalMessage);
                }
            }

            generalMessageDelivery.getGeneralMessages().clear();
            generalMessageDelivery.getGeneralMessages().addAll(filteredGeneralMessages);
        }
        return filtered;
    }

    /*
     * Filters elements with 1 - one - possible match per element
     */
    private static Siri applySingleMatchFilter(Siri siri, Map<Class, Set<String>> filter) {


        filterLineRef(siri, filter.get(LineRef.class));
        filterVehicleRef(siri, filter.get(VehicleRef.class));
        filterMonitoringRef(siri, filter.get(MonitoringRefStructure.class));

        return siri;
    }

    private static void filterLineRef(Siri siri, Set<String> linerefValues) {
        if (linerefValues == null || linerefValues.isEmpty()) {
            return;
        }

        //VM-deliveries
        List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = siri.getServiceDelivery().getVehicleMonitoringDeliveries();
        for (VehicleMonitoringDeliveryStructure delivery : vehicleMonitoringDeliveries) {
            List<VehicleActivityStructure> vehicleActivities = delivery.getVehicleActivities();
            List<VehicleActivityStructure> filteredActivities = new ArrayList<>();

            for (VehicleActivityStructure vehicleActivity : vehicleActivities) {
                VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = vehicleActivity.getMonitoredVehicleJourney();
                if (monitoredVehicleJourney != null) {
                    if (monitoredVehicleJourney.getLineRef() != null) {
                        if (isLineRefMatch(linerefValues, monitoredVehicleJourney.getLineRef().getValue())) {
                            filteredActivities.add(vehicleActivity);
                        }
                    }
                }
            }
            delivery.getVehicleActivities().clear();
            delivery.getVehicleActivities().addAll(filteredActivities);
        }

        //ET-deliveries
        List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
        for (EstimatedTimetableDeliveryStructure delivery : etDeliveries) {
            List<EstimatedVersionFrameStructure> etVersionFrames = delivery.getEstimatedJourneyVersionFrames();

            for (EstimatedVersionFrameStructure version : etVersionFrames) {
                List<EstimatedVehicleJourney> matches = new ArrayList<>();
                List<EstimatedVehicleJourney> estimatedVehicleJourneies = version.getEstimatedVehicleJourneies();
                for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {
                    if (estimatedVehicleJourney.getLineRef() != null) {
                        if (isLineRefMatch(linerefValues, estimatedVehicleJourney.getLineRef().getValue())) {
                            matches.add(estimatedVehicleJourney);
                        }
                    }
                }
                version.getEstimatedVehicleJourneies().clear();
                version.getEstimatedVehicleJourneies().addAll(matches);
            }
        }
    }

    private static boolean isLineRefMatch(Set<String> linerefValues, String completeValue) {
        if (completeValue.contains(SiriValueTransformer.SEPARATOR)) {
            String mappedId = OutboundIdAdapter.getMappedId(completeValue);
            String originalId = OutboundIdAdapter.getOriginalId(completeValue);
            return linerefValues.contains(mappedId) || linerefValues.contains(originalId);
        } else return linerefValues.contains(completeValue);
    }

    private static void filterVehicleRef(Siri siri, Set<String> vehiclerefValues) {
        if (vehiclerefValues == null || vehiclerefValues.isEmpty()) {
            return;
        }
        //VM-deliveries
        List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = siri.getServiceDelivery().getVehicleMonitoringDeliveries();
        for (VehicleMonitoringDeliveryStructure delivery : vehicleMonitoringDeliveries) {
            List<VehicleActivityStructure> vehicleActivities = delivery.getVehicleActivities();
            List<VehicleActivityStructure> filteredActivities = new ArrayList<>();

            for (VehicleActivityStructure vehicleActivity : vehicleActivities) {
                VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = vehicleActivity.getMonitoredVehicleJourney();
                if (monitoredVehicleJourney != null) {
                    if (monitoredVehicleJourney.getVehicleRef() != null) {
                        if (vehiclerefValues.contains(monitoredVehicleJourney.getVehicleRef().getValue())) {
                            filteredActivities.add(vehicleActivity);
                        }
                    }
                }
            }
            delivery.getVehicleActivities().clear();
            delivery.getVehicleActivities().addAll(filteredActivities);
        }

        //ET-deliveries
        List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
        for (EstimatedTimetableDeliveryStructure delivery : etDeliveries) {
            List<EstimatedVersionFrameStructure> etVersionFrames = delivery.getEstimatedJourneyVersionFrames();

            for (EstimatedVersionFrameStructure version : etVersionFrames) {
                List<EstimatedVehicleJourney> matches = new ArrayList<>();
                List<EstimatedVehicleJourney> estimatedVehicleJourneies = version.getEstimatedVehicleJourneies();
                for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {
                    if (estimatedVehicleJourney.getVehicleRef() != null) {
                        if (vehiclerefValues.contains(estimatedVehicleJourney.getVehicleRef().getValue())) {
                            matches.add(estimatedVehicleJourney);
                        }
                    }
                }
                version.getEstimatedVehicleJourneies().clear();
                version.getEstimatedVehicleJourneies().addAll(matches);
            }
        }
    }



    private static void filterMonitoringRef(Siri siri, Set<String> monitoringRef) {
        if (monitoringRef == null || monitoringRef.isEmpty()) {
            return;

        }
        //SM-deliveries
        List<StopMonitoringDeliveryStructure> stopMonitoringDeliveries = siri.getServiceDelivery().getStopMonitoringDeliveries();
        for (StopMonitoringDeliveryStructure delivery : stopMonitoringDeliveries) {
            List<MonitoredStopVisit> monitoredStopVisits = delivery.getMonitoredStopVisits();
            List<MonitoredStopVisit> filteredStopVisits = new ArrayList<>();
            for (MonitoredStopVisit monitoredStopVisit : monitoredStopVisits) {
                if (monitoringRef.contains(monitoredStopVisit.getMonitoringRef().getValue())){
                    filteredStopVisits.add(monitoredStopVisit);
                }
            }
            delivery.getMonitoredStopVisits().clear();
            delivery.getMonitoredStopVisits().addAll(filteredStopVisits);
        }
    }


    /*
     * Filters elements with multiple possible matches per element
     */
    private static Siri applyMultipleMatchFilter(Siri siri, Map<Class, Set<String>> filter) {

        Set<String> linerefValues = filter.get(LineRef.class);
        if (linerefValues == null || linerefValues.isEmpty()) {
            return siri;
        }
        List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = siri.getServiceDelivery().getSituationExchangeDeliveries();
        for (SituationExchangeDeliveryStructure delivery : situationExchangeDeliveries) {
            SituationExchangeDeliveryStructure.Situations situations = delivery.getSituations();


            List<PtSituationElement> ptSituationElements = situations.getPtSituationElements();
            List<PtSituationElement> filteredSituationElements = new ArrayList<>();
            for (PtSituationElement s : ptSituationElements) {
                if (s.getAffects() != null &&
                        s.getAffects().getNetworks() != null &&
                        s.getAffects().getNetworks().getAffectedNetworks() != null) {

                    List<AffectsScopeStructure.Networks.AffectedNetwork> affectedNetworks = s.getAffects().getNetworks().getAffectedNetworks();
                    for (AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork : affectedNetworks) {
                        List<AffectedLineStructure> affectedLines = affectedNetwork.getAffectedLines();
                        if (affectedLines != null) {
                            for (AffectedLineStructure affectedLine : affectedLines) {
                                LineRef lineRef = affectedLine.getLineRef();
                                if (!filteredSituationElements.contains(s) && lineRef != null) {
                                    if (isLineRefMatch(linerefValues, lineRef.getValue())) {
                                        filteredSituationElements.add(s);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            situations.getPtSituationElements().clear();
            situations.getPtSituationElements().addAll(filteredSituationElements);
        }

        return siri;
    }

    public Siri getAllVM() { return siriObjectFactory.createVMServiceDelivery(vehicleActivities.getAll()); }
    public Siri getAllSX() {
        return siriObjectFactory.createSXServiceDelivery(situations.getAll());
    }
    public Siri getAllET() {
        return siriObjectFactory.createETServiceDelivery(estimatedTimetables.getAll());
    }
    public Siri getAllSM() { return siriObjectFactory.createSMServiceDelivery(monitoredStopVisits.getAll()); }
}
