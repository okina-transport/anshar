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
import no.rutebanken.anshar.routes.siri.handlers.outbound.SituationExchangeOutbound;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.util.IDUtils;
import org.entur.siri.validator.SiriValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.*;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
@Component
public class SiriHelper {
    public static final String FALLBACK_SIRI_VERSION = "2.1";
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
    private FacilityMonitoring facilityMonitoring;

    @Autowired
    private StopPlaceUpdaterService stopPlaceUpdaterService;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private SituationExchangeOutbound situationExchangeOutbound;

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
        } else if (containsValues(subscriptionRequest.getGeneralMessageSubscriptionRequests())) {
            return getFilter(subscriptionRequest.getGeneralMessageSubscriptionRequests().get(0), outboundIdMappingPolicy, datasetId);
        } else if (containsValues(subscriptionRequest.getFacilityMonitoringSubscriptionRequests())) {
            return getFilter(subscriptionRequest.getFacilityMonitoringSubscriptionRequests().get(0), outboundIdMappingPolicy, datasetId);
        }

        return new HashMap<>();
    }

    private Map<Class, Set<String>> getFilter(FacilityMonitoringSubscriptionStructure facilityMonitoringSubscriptionStructure, OutboundIdMappingPolicy outboundIdMappingPolicy, String datasetId) {
        Map<Class, Set<String>> filterMap = new HashMap<>();

        List<FacilityRef> requestedFacilities = facilityMonitoringSubscriptionStructure.getFacilityMonitoringRequest().getFacilityReves();


        Set<String> requestedFacilitiesSet = requestedFacilities.stream()
                .map(FacilityRef::getValue)
                .collect(Collectors.toSet());

        Map<ObjectType, Optional<IdProcessingParameters>> idProcessingParams = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);

        if (!requestedFacilitiesSet.isEmpty()) {
            filterMap.put(FacilityStructure.class, requestedFacilitiesSet);
        }

        return filterMap;
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

            lineReves.forEach(ref -> linerefValues.add(ref.getValue()));

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
            String rawLineValue = lineRef.getValue();
            rawLineValue = rawLineValue.replaceAll(":FlexibleLine:", ":Line:");
            Set<String> searchedValues = new HashSet<>();
            searchedValues.add(rawLineValue);
            Optional<String> datasetOpt = subscriptionConfig.findDatasetFromSearch(searchedValues, ObjectType.LINE);
            if (datasetOpt.isPresent()) {
                Optional<IdProcessingParameters> idProcLineOpt = subscriptionConfig.getIdParametersForDataset(datasetOpt.get(), ObjectType.LINE);
                Set<String> revertedLineIds = IDUtils.revertMonitoringRefs(searchedValues, idProcLineOpt);
                linerefValues.addAll(revertedLineIds);
            } else {
                linerefValues.addAll(searchedValues);
            }
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
        List<String> originalRequestedIds = Collections.singletonList(requestedId);
        if (OutboundIdMappingPolicy.DEFAULT.equals(outboundIdMappingPolicy)) {
            originalRequestedIds = stopPlaceUpdaterService.canBeReverted(requestedId, datasetId) ? stopPlaceUpdaterService.getReverse(requestedId, datasetId) : Arrays.asList(requestedId);
        }

        HashSet<String> requestedIds = new HashSet<>(originalRequestedIds);

        Map<ObjectType, Optional<IdProcessingParameters>> idProcessingParams = subscriptionConfig.buildIdProcessingParams(null, requestedIds, ObjectType.STOP);

        Set<String> revertedMonitoringRefs = IDUtils.revertMonitoringRefs(requestedIds, idProcessingParams.get(ObjectType.STOP));

        if (!revertedMonitoringRefs.isEmpty()) {
            stopPointRefValues.add(revertedMonitoringRefs.iterator().next());
            filterMap.put(MonitoringRefStructure.class, stopPointRefValues);
        }

        return filterMap;
    }


    public Map<ObjectType, Optional<IdProcessingParameters>> getIdProcessingParamsFromSubscription(StopMonitoringSubscriptionStructure stopMonitoringSubscription, OutboundIdMappingPolicy outboundIdMappingPolicy, String datasetId) {

        String requestedId = stopMonitoringSubscription.getStopMonitoringRequest().getMonitoringRef().getValue();
        List<String> originalRequestedIds = new ArrayList<>();
        if (OutboundIdMappingPolicy.DEFAULT.equals(outboundIdMappingPolicy)) {
            originalRequestedIds = stopPlaceUpdaterService.canBeReverted(requestedId, datasetId) ? stopPlaceUpdaterService.getReverse(requestedId, datasetId) : Arrays.asList(requestedId);
        }

        HashSet<String> requestedIds = new HashSet<>(originalRequestedIds);

        return subscriptionConfig.buildIdProcessingParams(null, requestedIds, ObjectType.STOP);
    }

    public Map<ObjectType, Optional<IdProcessingParameters>> getIdProcessingParamsFromSubscription(VehicleMonitoringSubscriptionStructure vehMonitoringSubscription, OutboundIdMappingPolicy outboundIdMappingPolicy, String datasetId) {

        String requestedId = vehMonitoringSubscription.getVehicleMonitoringRequest().getLineRef().getValue();

        HashSet<String> requestedIds = new HashSet<>();
        requestedIds.add(requestedId);

        return subscriptionConfig.buildIdProcessingParams(null, requestedIds, ObjectType.LINE);
    }


    Siri findInitialDeliveryData(OutboundSubscriptionSetup subscriptionRequest) {
        Siri delivery = null;

        switch (subscriptionRequest.getSubscriptionType()) {

            case SITUATION_EXCHANGE:
                OutboundIdMappingPolicy policy = subscriptionRequest.isUseOriginalId() ? OutboundIdMappingPolicy.ORIGINAL_ID : OutboundIdMappingPolicy.DEFAULT;
                delivery = situationExchangeOutbound.createServiceDelivery(subscriptionRequest.getRequestorRef(), subscriptionRequest.getDatasetId(), subscriptionRequest.getClientTrackingName(), policy, 1000);
                logger.info("Initial SX-delivery: {} elements", delivery.getServiceDelivery().getSituationExchangeDeliveries().size());
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

            case FACILITY_MONITORING:
                Collection<FacilityConditionStructure> facility = facilityMonitoring.getAll(subscriptionRequest.getDatasetId());
                logger.info("Initial FM-delivery: {} elements", facility.size());
                delivery = siriObjectFactory.createFMServiceDelivery(facility);
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
        } else if (containsValues(payload.getServiceDelivery().getGeneralMessageDeliveries())) {

            List<GeneralMessage> generalMsgList = payload.getServiceDelivery()
                    .getGeneralMessageDeliveries().get(0)
                    .getGeneralMessages();

            List<List> gmList = splitList(generalMsgList, maximumSizePerDelivery);

            for (List<GeneralMessage> list : gmList) {
                siriList.add(siriObjectFactory.createGMServiceDelivery(list));
            }

        } else if (containsValues(payload.getServiceDelivery().getFacilityMonitoringDeliveries())) {

            List<FacilityConditionStructure> facilityConditionsList = payload.getServiceDelivery()
                    .getFacilityMonitoringDeliveries().get(0)
                    .getFacilityConditions();

            List<List> fmList = splitList(facilityConditionsList, maximumSizePerDelivery);

            for (List<FacilityConditionStructure> list : fmList) {
                siriList.add(siriObjectFactory.createFMServiceDelivery(list));
            }

        }

        return siriList;
    }

    private List<List> splitList(List fullList, int maximumSizePerDelivery) {
        int startIndex = 0;
        int endIndex = Math.min(startIndex + maximumSizePerDelivery, fullList.size());

        List<List> list = new ArrayList<>();
        boolean hasMoreElements = true;
        while (hasMoreElements) {

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
        return filterSiriPayload(siri, filter, true);
    }

    public static Siri filterSiriPayload(Siri siri, Map<Class, Set<String>> filter, boolean shouldPerformDeepCopy) {
        if (filter == null || filter.isEmpty()) {
            logger.debug("No filter to apply");
            return siri;
        }

        if (siri.getServiceDelivery() != null) {
            return filterSiriByCreatingNewObject(siri, filter, shouldPerformDeepCopy);
        }

        return siri;
    }

    private static Siri filterSiriByCreatingNewObject(Siri siri, Map<Class, Set<String>> filter, boolean shouldPerformDeepCopy) {

        Siri result = new Siri();
        result.setVersion(siri.getVersion());

        ServiceDelivery serviceDel = new ServiceDelivery();
        serviceDel.setResponseTimestamp(siri.getServiceDelivery().getResponseTimestamp());
        serviceDel.setProducerRef(siri.getServiceDelivery().getProducerRef());
        serviceDel.setStatus(siri.getServiceDelivery().isStatus());

        if (containsValues(siri.getServiceDelivery().getStopMonitoringDeliveries())) {
            List<StopMonitoringDeliveryStructure> stopMonitoringDeliveries = getFilteredMonitoringRef(siri, filter.get(MonitoringRefStructure.class));
            serviceDel.getStopMonitoringDeliveries().addAll(stopMonitoringDeliveries);
        } else if (containsValues(siri.getServiceDelivery().getEstimatedTimetableDeliveries())) {
            List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = getFilteredEstimatedRef(siri, filter.get(LineRef.class), filter.get(VehicleRef.class));
            serviceDel.getEstimatedTimetableDeliveries().addAll(estimatedTimetableDeliveries);
        } else if (containsValues(siri.getServiceDelivery().getVehicleMonitoringDeliveries())) {
            List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = getFilteredVehicleRef(siri, filter.get(LineRef.class), filter.get(VehicleRef.class));
            serviceDel.getVehicleMonitoringDeliveries().addAll(vehicleMonitoringDeliveries);
        } else if (containsValues(siri.getServiceDelivery().getSituationExchangeDeliveries())) {
            List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = getFilteredSituationRef(siri, filter.get(LineRef.class));
            serviceDel.getSituationExchangeDeliveries().addAll(situationExchangeDeliveries);
        } else if (containsValues(siri.getServiceDelivery().getFacilityMonitoringDeliveries())) {
            List<FacilityMonitoringDeliveryStructure> facilityMonitoringDeliveries = getFilteredFaciliesRef(siri, filter.get(FacilityStructure.class));
            serviceDel.getFacilityMonitoringDeliveries().addAll(facilityMonitoringDeliveries);
        } else if (containsValues(siri.getServiceDelivery().getGeneralMessageDeliveries())) {
            List<GeneralMessageDeliveryStructure> generalMessageDeliveries = getFilteredGeneralRef(siri, filter.get(InfoChannel.class));
            serviceDel.getGeneralMessageDeliveries().addAll(generalMessageDeliveries);
        } else {
            return siri;
        }

        result.setServiceDelivery(serviceDel);
        return result;
    }

    private static List<FacilityMonitoringDeliveryStructure> getFilteredFaciliesRef(Siri siri, Set<String> facility) {
        if (facility == null || facility.isEmpty()) {
            return null;
        }

        //FM-deliveries
        List<FacilityMonitoringDeliveryStructure> facilityMonitoringDeliveries = siri.getServiceDelivery().getFacilityMonitoringDeliveries();
        List<FacilityMonitoringDeliveryStructure> results = new ArrayList<>();

        for (FacilityMonitoringDeliveryStructure delivery : facilityMonitoringDeliveries) {
            List<FacilityConditionStructure> facilityConditions = delivery.getFacilityConditions();
            List<FacilityConditionStructure> filteredFacilityCondition = new ArrayList<>();

            for (FacilityConditionStructure facilityCondition : facilityConditions) {

                if (facility.contains(facilityCondition.getFacilityRef().getValue())) {
                    filteredFacilityCondition.add(facilityCondition);
                }
            }
            if (!facilityConditions.isEmpty()) {
                FacilityMonitoringDeliveryStructure facilityMonitoringDelStruct = new FacilityMonitoringDeliveryStructure();
                facilityMonitoringDelStruct.setResponseTimestamp(delivery.getResponseTimestamp());
                facilityMonitoringDelStruct.setVersion(delivery.getVersion());

                facilityMonitoringDelStruct.getFacilityConditions().addAll(filteredFacilityCondition);
                results.add(facilityMonitoringDelStruct);
            }
        }
        return results;
    }

    private static List<EstimatedTimetableDeliveryStructure> getFilteredEstimatedRef(Siri siri, Set<String> lineRef, Set<String> vehicleRef) {

        List<EstimatedTimetableDeliveryStructure> results = new ArrayList<>();

        //ET-deliveries
        List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
        for (EstimatedTimetableDeliveryStructure delivery : estimatedTimetableDeliveries) {
            List<EstimatedVersionFrameStructure> etVersionFrames = delivery.getEstimatedJourneyVersionFrames();
            List<EstimatedVersionFrameStructure> filteredVersionFrames = new ArrayList<>();

            for (EstimatedVersionFrameStructure version : etVersionFrames) {
                List<EstimatedVehicleJourney> matches = new ArrayList<>();
                List<EstimatedVehicleJourney> estimatedVehicleJourneies = version.getEstimatedVehicleJourneies();
                for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {

                    String lineRefValue = estimatedVehicleJourney.getLineRef() != null ? estimatedVehicleJourney.getLineRef().getValue() : null;
                    String vehicleRefValue = estimatedVehicleJourney.getVehicleRef() != null ? estimatedVehicleJourney.getVehicleRef().getValue() : null;

                    if (lineRef != null && !lineRef.isEmpty() && vehicleRef != null && !vehicleRef.isEmpty()) {
                        if (isLineRefMatch(lineRef, lineRefValue) && vehicleRef.contains(vehicleRefValue)) {
                            matches.add(estimatedVehicleJourney);
                        }
                    } else if (lineRef != null && !lineRef.isEmpty()) {
                        if (isLineRefMatch(lineRef, lineRefValue)) {
                            matches.add(estimatedVehicleJourney);
                        }
                    } else if (vehicleRef != null && !vehicleRef.isEmpty()) {
                        if (vehicleRef.contains(vehicleRefValue)) {
                            matches.add(estimatedVehicleJourney);
                        }
                    }
                }

                EstimatedVersionFrameStructure newVersion = new EstimatedVersionFrameStructure();
                newVersion.getEstimatedVehicleJourneies().addAll(matches);

                if (!matches.isEmpty()) {
                    filteredVersionFrames.add(newVersion);
                }
            }

            if (!filteredVersionFrames.isEmpty()) {
                EstimatedTimetableDeliveryStructure estimatedTimetableDelStruct = new EstimatedTimetableDeliveryStructure();
                estimatedTimetableDelStruct.setResponseTimestamp(delivery.getResponseTimestamp());
                estimatedTimetableDelStruct.setVersion(delivery.getVersion());

                estimatedTimetableDelStruct.getEstimatedJourneyVersionFrames().addAll(filteredVersionFrames);
                results.add(estimatedTimetableDelStruct);
            }
        }
        return results;
    }

    private static List<GeneralMessageDeliveryStructure> getFilteredGeneralRef(Siri siri, Set<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return null;
        }
        List<GeneralMessageDeliveryStructure> results = new ArrayList<>();

        //GM-deliveries
        List<GeneralMessageDeliveryStructure> estimatedTimetableDeliveries = siri.getServiceDelivery().getGeneralMessageDeliveries();
        for (GeneralMessageDeliveryStructure delivery : estimatedTimetableDeliveries) {

            List<GeneralMessage> generalMessages = delivery.getGeneralMessages();
            List<GeneralMessage> filteredGeneralMessages = new ArrayList<>();
            for (GeneralMessage generalMessage : generalMessages) {

                if (channels.contains(generalMessage.getInfoChannelRef().getValue())) {
                    filteredGeneralMessages.add(generalMessage);
                }
            }
            if (!generalMessages.isEmpty()) {
                GeneralMessageDeliveryStructure generalMessageDelStruct = new GeneralMessageDeliveryStructure();
                generalMessageDelStruct.setResponseTimestamp(delivery.getResponseTimestamp());
                generalMessageDelStruct.setVersion(delivery.getVersion());

                generalMessageDelStruct.getGeneralMessages().addAll(filteredGeneralMessages);
                results.add(generalMessageDelStruct);
            }
        }
        return results;

    }

    private static List<SituationExchangeDeliveryStructure> getFilteredSituationRef(Siri siri, Set<String> linerefValues) {
        if (linerefValues == null || linerefValues.isEmpty()) {
            return getAllSituationExcahnges(siri);
        }

        List<SituationExchangeDeliveryStructure> results = new ArrayList<>();

        //SX-deliveries
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
            if (!filteredSituationElements.isEmpty()) {
                SituationExchangeDeliveryStructure situationDelStruct = new SituationExchangeDeliveryStructure();
                situationDelStruct.setResponseTimestamp(delivery.getResponseTimestamp());
                situationDelStruct.setVersion(delivery.getVersion());
                SituationExchangeDeliveryStructure.Situations newSituations = new SituationExchangeDeliveryStructure.Situations();
                newSituations.getPtSituationElements().addAll(filteredSituationElements);
                situationDelStruct.setSituations(newSituations);
                results.add(situationDelStruct);
            }
        }
        return results;
    }

    private static List<SituationExchangeDeliveryStructure> getAllSituationExcahnges(Siri siri) {
        List<SituationExchangeDeliveryStructure> results = new ArrayList<>();

        //SX-deliveries
        List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = siri.getServiceDelivery().getSituationExchangeDeliveries();
        for (SituationExchangeDeliveryStructure delivery : situationExchangeDeliveries) {
            SituationExchangeDeliveryStructure.Situations situations = delivery.getSituations();
            List<PtSituationElement> ptSituationElements = situations.getPtSituationElements();

            List<PtSituationElement> filteredSituationElements = new ArrayList<>();
            filteredSituationElements.addAll(ptSituationElements);

            if (!filteredSituationElements.isEmpty()) {
                SituationExchangeDeliveryStructure situationDelStruct = new SituationExchangeDeliveryStructure();
                situationDelStruct.setResponseTimestamp(delivery.getResponseTimestamp());
                situationDelStruct.setVersion(delivery.getVersion());
                SituationExchangeDeliveryStructure.Situations newSituations = new SituationExchangeDeliveryStructure.Situations();
                newSituations.getPtSituationElements().addAll(filteredSituationElements);
                situationDelStruct.setSituations(newSituations);
                results.add(situationDelStruct);
            }
        }
        return results;
    }

    private static List<VehicleMonitoringDeliveryStructure> getFilteredVehicleRef(Siri siri, Set<String> lineRef, Set<String> vehicleRef) {
        List<VehicleMonitoringDeliveryStructure> results = new ArrayList<>();

        //VM-deliveries
        List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = siri.getServiceDelivery().getVehicleMonitoringDeliveries();
        for (VehicleMonitoringDeliveryStructure delivery : vehicleMonitoringDeliveries) {
            List<VehicleActivityStructure> vehicleActivities = delivery.getVehicleActivities();
            List<VehicleActivityStructure> filteredActivities = new ArrayList<>();

            for (VehicleActivityStructure vehicleActivity : vehicleActivities) {
                VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = vehicleActivity.getMonitoredVehicleJourney();
                if (monitoredVehicleJourney != null) {

                    String lineRefValue = monitoredVehicleJourney.getLineRef() != null ? monitoredVehicleJourney.getLineRef().getValue() : null;
                    String vehicleRefValue = monitoredVehicleJourney.getVehicleRef() != null ? monitoredVehicleJourney.getVehicleRef().getValue() : null;

                    if (lineRef != null && !lineRef.isEmpty() && vehicleRef != null && !vehicleRef.isEmpty()) {
                        if (isLineRefMatch(lineRef, lineRefValue) && vehicleRef.contains(vehicleRefValue)) {
                            filteredActivities.add(vehicleActivity);
                        }
                    } else if (lineRef != null && !lineRef.isEmpty()) {
                        if (isLineRefMatch(lineRef, lineRefValue)) {
                            filteredActivities.add(vehicleActivity);
                        }
                    } else if (vehicleRef != null && !vehicleRef.isEmpty()) {
                        if (vehicleRef.contains(vehicleRefValue)) {
                            filteredActivities.add(vehicleActivity);
                        }
                    }
                }
            }
            if (!vehicleActivities.isEmpty()) {
                VehicleMonitoringDeliveryStructure vehicleMonitoringDelStruct = new VehicleMonitoringDeliveryStructure();
                vehicleMonitoringDelStruct.setVersion(delivery.getVersion());
                vehicleMonitoringDelStruct.setResponseTimestamp(delivery.getResponseTimestamp());

                vehicleMonitoringDelStruct.getVehicleActivities().addAll(filteredActivities);
                results.add(vehicleMonitoringDelStruct);
            }
        }
        return results;
    }

    private static List<StopMonitoringDeliveryStructure> getFilteredMonitoringRef(Siri siri, Set<String> monitoringRef) {
        if (monitoringRef == null || monitoringRef.isEmpty()) {
            return null;
        }
        List<StopMonitoringDeliveryStructure> results = new ArrayList<>();

        //SM-deliveries
        List<StopMonitoringDeliveryStructure> stopMonitoringDeliveries = siri.getServiceDelivery().getStopMonitoringDeliveries();
        for (StopMonitoringDeliveryStructure delivery : stopMonitoringDeliveries) {
            List<MonitoredStopVisit> monitoredStopVisits = delivery.getMonitoredStopVisits();
            List<MonitoredStopVisit> filteredStopVisits = new ArrayList<>();
            for (MonitoredStopVisit monitoredStopVisit : monitoredStopVisits) {
                if (monitoringRef.contains(monitoredStopVisit.getMonitoringRef().getValue())) {
                    filteredStopVisits.add(monitoredStopVisit);
                }
            }
            if (!monitoredStopVisits.isEmpty()) {
                StopMonitoringDeliveryStructure monitoringDelStruct = new StopMonitoringDeliveryStructure();
                monitoringDelStruct.setResponseTimestamp(delivery.getResponseTimestamp());
                monitoringDelStruct.setVersion(delivery.getVersion());

                monitoringDelStruct.getMonitoredStopVisits().addAll(filteredStopVisits);
                results.add(monitoringDelStruct);
            }
        }
        return results;
    }

    private static boolean isLineRefMatch(Set<String> linerefValues, String completeValue) {
        if (completeValue == null) {
            return false;
        }

        if (linerefValues.isEmpty()) {
            // no specific line requested. Considered as a match : data must be kept
            return true;
        }

        if (completeValue.contains(SiriValueTransformer.SEPARATOR)) {
            String mappedId = OutboundIdAdapter.getMappedId(completeValue);
            String originalId = OutboundIdAdapter.getOriginalId(completeValue);
            return linerefValues.contains(mappedId) || linerefValues.contains(originalId);
        } else return linerefValues.contains(completeValue);
    }

    public static String resolveSiriVersionStr(SiriValidator.Version version) {
        switch (version) {
            case VERSION_1_0:
                return "1.0";
            case VERSION_1_3:
                return "1.3";
            case VERSION_1_4:
                return "1.4";
            case VERSION_2_0:
                return "2.0";
            case VERSION_2_1:
                return "2.1";
            default:
                return FALLBACK_SIRI_VERSION;
        }
    }


    public Siri getAllVM() {
        return siriObjectFactory.createVMServiceDelivery(vehicleActivities.getAll());
    }

    public Siri getAllSX() {
        return siriObjectFactory.createSXServiceDelivery(situations.getAll());
    }

    public Siri getAllET() {
        return siriObjectFactory.createETServiceDelivery(estimatedTimetables.getAll());
    }

    public Siri getAllSM() {
        return siriObjectFactory.createSMServiceDelivery(monitoredStopVisits.getAll());
    }
}
