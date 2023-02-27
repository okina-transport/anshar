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

package no.rutebanken.anshar.routes.siri.handlers;

import io.micrometer.core.instrument.util.StringUtils;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.data.StopPlaceIdCache;
import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.validation.SiriXmlValidator;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import no.rutebanken.anshar.util.IDUtils;
import org.json.simple.JSONObject;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.AnnotatedLineRef;
import uk.org.siri.siri20.AnnotatedStopPointStructure;
import uk.org.siri.siri20.ErrorCodeStructure;
import uk.org.siri.siri20.ErrorDescriptionStructure;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri20.InvalidDataReferencesErrorStructure;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.MonitoredStopVisit;
import uk.org.siri.siri20.MonitoringRefStructure;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.RequestorRef;
import uk.org.siri.siri20.ServiceDeliveryErrorConditionElement;
import uk.org.siri.siri20.ServiceRequest;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SituationExchangeDeliveryStructure;
import uk.org.siri.siri20.StopMonitoringDeliveryStructure;
import uk.org.siri.siri20.StopMonitoringRequestStructure;
import uk.org.siri.siri20.StopPointRef;
import uk.org.siri.siri20.SubscriptionResponseStructure;
import uk.org.siri.siri20.TerminateSubscriptionRequestStructure;
import uk.org.siri.siri20.TerminateSubscriptionResponseStructure;
import uk.org.siri.siri20.VehicleActivityStructure;
import uk.org.siri.siri20.VehicleMonitoringDeliveryStructure;
import uk.org.siri.siri20.VehicleMonitoringRequestStructure;
import uk.org.siri.siri20.VehicleRef;

import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.datatype.Duration;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getOriginalId;

@Service
public class SiriHandler {

    private final Logger logger = LoggerFactory.getLogger(SiriHandler.class);

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private Situations situations;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private MonitoredStopVisits monitoredStopVisits;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private HealthManager healthManager;

    @Autowired
    private SiriXmlValidator siriXmlValidator;

    @Autowired
    private PrometheusMetricsService metrics;

    @Autowired
    private StopPlaceIdCache idCache;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private StopPlaceUpdaterService stopPlaceUpdaterService;

    private Map<String, String> datasetByLine = new HashMap<>();

    public Siri handleIncomingSiri(String subscriptionId, InputStream xml) throws UnmarshalException {
        return handleIncomingSiri(subscriptionId, xml, null, -1);
    }

    private Siri handleIncomingSiri(String subscriptionId, InputStream xml, String datasetId, int maxSize) throws UnmarshalException {
        return handleIncomingSiri(subscriptionId, xml, datasetId, null, maxSize, null);
    }

    /**
     * @param subscriptionId          SubscriptionId
     * @param xml                     SIRI-request as XML
     * @param datasetId               Optional datasetId
     * @param outboundIdMappingPolicy Defines outbound idmapping-policy
     * @return the siri response
     */
    public Siri handleIncomingSiri(String subscriptionId, InputStream xml, String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy, int maxSize, String clientTrackingName) throws UnmarshalException {
        return handleIncomingSiri(subscriptionId, xml, datasetId, null, outboundIdMappingPolicy, maxSize, clientTrackingName);
    }

    public Siri handleIncomingSiri(String subscriptionId, InputStream xml, String datasetId, List<String> excludedDatasetIdList, OutboundIdMappingPolicy outboundIdMappingPolicy, int maxSize, String clientTrackingName) throws UnmarshalException {
        try {
            if (subscriptionId != null) {
                processSiriClientRequest(subscriptionId, xml); // Response to a request we made on behalf of one of the subscriptions
            } else {
                Siri incoming = SiriValueTransformer.parseXml(xml); // Someone asking us for siri update
                return processSiriServerRequest(incoming, datasetId, excludedDatasetIdList, outboundIdMappingPolicy, maxSize, clientTrackingName);
            }
        } catch (UnmarshalException e) {
            throw e;
        } catch (JAXBException | XMLStreamException e) {
            logger.warn("Caught exception when parsing incoming XML", e);
        }
        return null;
    }

    public Siri handleSiriCacheRequest(
            InputStream body, String datasetId, String clientTrackingName
    ) throws XMLStreamException, JAXBException {

        Siri incoming = SiriValueTransformer.parseXml(body);

        if (incoming.getServiceRequest() != null) {
            ServiceRequest serviceRequest = incoming.getServiceRequest();
            String requestorRef = null;

            Siri serviceResponse = null;

            if (serviceRequest.getRequestorRef() != null) {
                requestorRef = serviceRequest.getRequestorRef().getValue();
            }

            SiriDataType dataType = null;
            if (hasValues(serviceRequest.getSituationExchangeRequests())) {
                dataType = SiriDataType.SITUATION_EXCHANGE;

                final Collection<PtSituationElement> elements = situations.getAllCachedUpdates(requestorRef,
                        datasetId,
                        clientTrackingName
                );
                logger.info("Returning {} elements from cache", elements.size());
                serviceResponse = siriObjectFactory.createSXServiceDelivery(elements);

            } else if (hasValues(serviceRequest.getVehicleMonitoringRequests())) {
                dataType = SiriDataType.VEHICLE_MONITORING;

                final Collection<VehicleActivityStructure> elements = vehicleActivities.getAllCachedUpdates(
                        requestorRef,
                        datasetId,
                        clientTrackingName
                );
                logger.info("Returning {} elements from cache", elements.size());
                serviceResponse = siriObjectFactory.createVMServiceDelivery(elements);

            } else if (hasValues(serviceRequest.getEstimatedTimetableRequests())) {
                dataType = SiriDataType.ESTIMATED_TIMETABLE;

                final Collection<EstimatedVehicleJourney> elements = estimatedTimetables.getAllCachedUpdates(requestorRef, datasetId, clientTrackingName);

                logger.info("Returning {} elements from cache", elements.size());
                serviceResponse = siriObjectFactory.createETServiceDelivery(elements);

            }


            if (serviceResponse != null) {
                metrics.countOutgoingData(serviceResponse, SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE);



                return SiriValueTransformer.transform(
                        serviceResponse,
                        MappingAdapterPresets.getOutboundAdapters(dataType, OutboundIdMappingPolicy.DEFAULT, subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId)),
                        false,
                        false
                );
            }
        }
        return null;
    }




    /**
     * Handling incoming requests from external clients
     *
     * @param incoming              incoming message
     * @param excludedDatasetIdList dataset to exclude
     */
    private Siri processSiriServerRequest(Siri incoming, String datasetId, List<String> excludedDatasetIdList, OutboundIdMappingPolicy outboundIdMappingPolicy, int maxSize, String clientTrackingName) {

        if (maxSize < 0) {
            maxSize = configuration.getDefaultMaxSize();

            if (datasetId != null) {
                maxSize = Integer.MAX_VALUE;
            }
        }

        List<ValueAdapter> valueAdapters = new ArrayList();

        if (incoming.getSubscriptionRequest() != null) {
            logger.info("Handling subscriptionrequest with ID-policy {}.", outboundIdMappingPolicy);
            return serverSubscriptionManager.handleSubscriptionRequest(incoming.getSubscriptionRequest(), datasetId, outboundIdMappingPolicy, clientTrackingName);

        } else if (incoming.getTerminateSubscriptionRequest() != null) {
            logger.info("Handling terminateSubscriptionrequest...");
            TerminateSubscriptionRequestStructure terminateSubscriptionRequest = incoming.getTerminateSubscriptionRequest();
            if (terminateSubscriptionRequest.getSubscriptionReves() != null && !terminateSubscriptionRequest.getSubscriptionReves().isEmpty()) {
                String subscriptionRef = terminateSubscriptionRequest.getSubscriptionReves().get(0).getValue();

                serverSubscriptionManager.terminateSubscription(subscriptionRef, configuration.processAdmin());
                if (configuration.processAdmin()) {
                    return siriObjectFactory.createTerminateSubscriptionResponse(subscriptionRef);
                }
            }
        } else if (incoming.getCheckStatusRequest() != null) {
            logger.info("Handling checkStatusRequest...");
            return serverSubscriptionManager.handleCheckStatusRequest(incoming.getCheckStatusRequest());
        } else if (incoming.getServiceRequest() != null) {
            logger.debug("Handling serviceRequest with ID-policy {}.", outboundIdMappingPolicy);
            ServiceRequest serviceRequest = incoming.getServiceRequest();
            String requestorRef = null;

            Siri serviceResponse = null;

            if (serviceRequest.getRequestorRef() != null) {
                requestorRef = serviceRequest.getRequestorRef().getValue();
            }
            SiriDataType dataType = null;
            if (hasValues(serviceRequest.getSituationExchangeRequests())) {
                dataType = SiriDataType.SITUATION_EXCHANGE;
                Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);
                valueAdapters = MappingAdapterPresets.getOutboundAdapters(dataType, outboundIdMappingPolicy,idMap);
                serviceResponse = situations.createServiceDelivery(requestorRef, datasetId, clientTrackingName, maxSize);
            } else if (hasValues(serviceRequest.getVehicleMonitoringRequests())) {
                dataType = SiriDataType.VEHICLE_MONITORING;

                Map<Class, Set<String>> filterMap = new HashMap<>();
                for (VehicleMonitoringRequestStructure req : serviceRequest.getVehicleMonitoringRequests()) {
                    LineRef lineRef = req.getLineRef();
                    if (lineRef != null) {
                        Set<String> linerefList = filterMap.get(LineRef.class) != null ? filterMap.get(LineRef.class) : new HashSet<>();
                        linerefList.add(lineRef.getValue());
                        filterMap.put(LineRef.class, linerefList);
                    }
                    VehicleRef vehicleRef = req.getVehicleRef();
                    if (vehicleRef != null) {
                        Set<String> vehicleRefList = filterMap.get(VehicleRef.class) != null ? filterMap.get(VehicleRef.class) : new HashSet<>();
                        vehicleRefList.add(vehicleRef.getValue());
                        filterMap.put(VehicleRef.class, vehicleRefList);
                    }
                }


                Set<String> lineRefList = filterMap.get(LineRef.class) != null ? filterMap.get(LineRef.class) : new HashSet<>();
                Set<String> vehicleRefList = filterMap.get(VehicleRef.class) != null ? filterMap.get(VehicleRef.class) : new HashSet<>();


//                Map<ObjectType, Optional<IdProcessingParameters>> idProcessingMap = new HashMap<>();
//                idProcessingMap.put(ObjectType.STOP, findIdProcessingParamsFromSearchedLines(datasetId, lineRefList, ObjectType.STOP));
//                idProcessingMap.put(ObjectType.LINE, findIdProcessingParamsFromSearchedLines(datasetId, lineRefList, ObjectType.LINE));


                Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParams(datasetId,lineRefList, ObjectType.LINE);
                Set<String> revertedLineRefs = IDUtils.revertMonitoringRefs(lineRefList, idMap.get(ObjectType.LINE));

                valueAdapters = MappingAdapterPresets.getOutboundAdapters(dataType, outboundIdMappingPolicy,idMap);
                Siri siri = vehicleActivities.createServiceDelivery(requestorRef, datasetId, clientTrackingName, excludedDatasetIdList, maxSize, revertedLineRefs, vehicleRefList);
                serviceResponse = siri;

                if (revertedLineRefs.size() > 0) {
                    List<String> invalidDataReferences = revertedLineRefs.stream()
                            .filter(lineRef -> !subscriptionManager.isLineRefExistingInSubscriptions(lineRef))
                            .collect(Collectors.toList());


                    handleInvalidDataReferences(serviceResponse, invalidDataReferences);
                }


                String requestMsgRef = siri.getServiceDelivery().getRequestMessageRef().getValue();
                logger.info("Filtering done. Returning :  {} for requestorRef {}", countVehicleActivityResults(serviceResponse), requestMsgRef);

            } else if (hasValues(serviceRequest.getEstimatedTimetableRequests())) {
                dataType = SiriDataType.ESTIMATED_TIMETABLE;
                Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);
                valueAdapters = MappingAdapterPresets.getOutboundAdapters(dataType, outboundIdMappingPolicy, idMap);

                Duration previewInterval = serviceRequest.getEstimatedTimetableRequests().get(0).getPreviewInterval();
                long previewIntervalInMillis = -1;

                if (previewInterval != null) {
                    previewIntervalInMillis = previewInterval.getTimeInMillis(new Date());
                }

                serviceResponse = estimatedTimetables.createServiceDelivery(requestorRef, datasetId, clientTrackingName, excludedDatasetIdList, maxSize, previewIntervalInMillis);
            } else if (hasValues(serviceRequest.getStopMonitoringRequests())) {
                dataType = SiriDataType.STOP_MONITORING;
                Map<Class, Set<String>> filterMap = new HashMap<>();
                Duration previewInterval = serviceRequest.getStopMonitoringRequests().get(0).getPreviewInterval();
                long previewIntervalInMillis = -1;
                if (previewInterval != null) {
                    previewIntervalInMillis = previewInterval.getTimeInMillis(new Date());
                }

                String monitoringStringList = null;
                for (StopMonitoringRequestStructure req : serviceRequest.getStopMonitoringRequests()) {
                    MonitoringRefStructure monitoringRef = req.getMonitoringRef();
                    if (monitoringRef != null) {
                        Set<String> monitoringRefs = filterMap.get(MonitoringRefStructure.class) != null ? filterMap.get(MonitoringRefStructure.class) : new HashSet<>();
                        monitoringRefs.add(monitoringRef.getValue());
                        filterMap.put(MonitoringRefStructure.class, monitoringRefs);

                        if (StringUtils.isEmpty(monitoringStringList)) {
                            monitoringStringList = monitoringRef.getValue();
                        } else {
                            monitoringStringList = monitoringStringList + "|" + monitoringRef.getValue();
                        }
                    }
                }


                Set<String> originalMonitoringRefs = filterMap.get(MonitoringRefStructure.class) != null ? filterMap.get(MonitoringRefStructure.class) : new HashSet<>();
                Set<String> importedIds = OutboundIdMappingPolicy.DEFAULT.equals(outboundIdMappingPolicy) ? convertToImportedIds(originalMonitoringRefs, datasetId) : originalMonitoringRefs;

                Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParams(datasetId,importedIds, ObjectType.STOP);
                Set<String> revertedMonitoringRefs = IDUtils.revertMonitoringRefs(importedIds, idMap.get(ObjectType.STOP));
                valueAdapters = MappingAdapterPresets.getOutboundAdapters(dataType, outboundIdMappingPolicy, idMap);


                serviceResponse = monitoredStopVisits.createServiceDelivery(requestorRef, datasetId, clientTrackingName, maxSize, revertedMonitoringRefs);
                logger.info("Asking for service delivery for requestorId={}, monitoringRef={}, clientTrackingName={}, datasetId={}", requestorRef, monitoringStringList, clientTrackingName, datasetId);
            }


            if (serviceResponse != null) {
                metrics.countOutgoingData(serviceResponse, SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE);
                return SiriValueTransformer.transform(
                        serviceResponse,
                        valueAdapters,
                        false,
                        false
                );
            }
        } else if (incoming.getStopPointsRequest() != null) {
            // stop discovery request
            return getDiscoveryStopPoints(datasetId, outboundIdMappingPolicy);
        } else if (incoming.getLinesRequest() != null) {
            // lines discovery request (for vehicle monitoring)
            return getDiscoveryLines(datasetId);
        }

        return null;
    }


    /**
     * Converts netex Ids (MOBIITI:Quay:xxx) to imported Ids prefixed by producer (PROD123:Quay:xxx)
     *
     * @param originalMonitoringRefs
     * @return the converted ids
     */
    private Set<String> convertToImportedIds(Set<String> originalMonitoringRefs, String datasetId) {

        return originalMonitoringRefs.stream()
                              .map(id -> StringUtils.isNotEmpty(id) && stopPlaceUpdaterService.canBeReverted(id, datasetId) ? stopPlaceUpdaterService.getReverse(id, datasetId) : id)
                              .collect(Collectors.toSet());
    }







    /**
     * Creates a siri response with all lines existing in the cache, for vehicle Monitoring
     *
     * @return the siri response with all points
     */
    private Siri getDiscoveryLines(String datasetId) {


        List<SubscriptionSetup> subscriptionList =  subscriptionManager.getAllSubscriptions(SiriDataType.VEHICLE_MONITORING).stream()
                                                                       .filter(subscriptionSetup -> (datasetId == null || subscriptionSetup.getDatasetId().equals(datasetId)))
                                                                        .collect(Collectors.toList());


        List<String> datasetList = subscriptionList.stream()
                                                    .map(SubscriptionSetup::getDatasetId)
                                                    .distinct()
                                                    .collect(Collectors.toList());



        Map<String, IdProcessingParameters> idProcessingMap = buildIdProcessingMap(datasetList, ObjectType.LINE);


        List<String> lineRefList = subscriptionList.stream()
                                            .map(subscription -> extractAndTransformLineId(subscription, idProcessingMap))
                                            .filter(lineRef -> lineRef != null)
                                            .collect(Collectors.toList());


        List<AnnotatedLineRef> resultList = lineRefList.stream()
                                                    .map(this::convertKeyToLineRef)
                                                    .collect(Collectors.toList());

        return siriObjectFactory.createLinesDiscoveryDelivery(resultList);


    }

    /**
     * Creates a siri response with all points existing in the cache
     *
     * @return the siri response with all points
     */
    public Siri getDiscoveryStopPoints(String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy) {


        List<SubscriptionSetup> subscriptionList = subscriptionManager.getAllSubscriptions(SiriDataType.STOP_MONITORING).stream()
                                                                        .filter(subscriptionSetup -> (datasetId == null || subscriptionSetup.getDatasetId().equals(datasetId)))
                                                                        .collect(Collectors.toList());

        List<String> datasetList = subscriptionList.stream()
                                                   .map(SubscriptionSetup::getDatasetId)
                                                   .distinct()
                                                   .collect(Collectors.toList());

        Map<String, IdProcessingParameters> idProcessingMap = buildIdProcessingMap(datasetList, ObjectType.STOP);


        List<String> monitoringRefList = subscriptionList.stream()
                                                         .map(subscription -> extractAndTransformStopId(subscription, idProcessingMap))
                                                         .collect(Collectors.toList());

        if (OutboundIdMappingPolicy.DEFAULT.equals(outboundIdMappingPolicy)){
            monitoringRefList = monitoringRefList.stream()
                                                 .map(id -> stopPlaceUpdaterService.isKnownId(id) ? stopPlaceUpdaterService.get(id) : id)
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
     * @param subscriptionSetup
     *      the subscriptionSetup for which the stop id must be recovered
     * @param idProcessingMap
     *      the map that associate datasetId to idProcessingParams
     * @return
     *      the transformed stop id
     */
    private String extractAndTransformStopId(SubscriptionSetup subscriptionSetup,  Map<String, IdProcessingParameters> idProcessingMap){
            String stopId = subscriptionSetup.getStopMonitoringRefValue();
            String datasetId = subscriptionSetup.getDatasetId();

            return idProcessingMap.containsKey(datasetId) ? idProcessingMap.get(datasetId).applyTransformationToString(stopId) : stopId;
    }

    /**
     * Extract a lineId from a subscriptionSetup and transforms it, with idProcessingParams
     * @param subscriptionSetup
     *      the subscriptionSetup for which the stop id must be recovered
     * @param idProcessingMap
     *      the map that associate datasetId to idProcessingParams
     * @return
     *      the transformed line id
     */
    private String extractAndTransformLineId(SubscriptionSetup subscriptionSetup,  Map<String, IdProcessingParameters> idProcessingMap){
        String lineId = subscriptionSetup.getLineRefValue();
        String datasetId = subscriptionSetup.getDatasetId();

        return idProcessingMap.containsKey(datasetId) ? idProcessingMap.get(datasetId).applyTransformationToString(lineId) : lineId;
    }

    /**
     * Builds a map with key = datasetId and value = idProcessingParams for this dataset and objectType = stop
     * @param datasetList
     *
     * @return
     */
    private Map<String, IdProcessingParameters> buildIdProcessingMap(List<String> datasetList, ObjectType objectType){
        Map<String, IdProcessingParameters> resultMap = new HashMap<>();

        for (String dataset : datasetList) {
            Optional<IdProcessingParameters> idParamsOpt = subscriptionConfig.getIdParametersForDataset(dataset, objectType);
            idParamsOpt.ifPresent(idParams -> resultMap.put(dataset, idParams));
        }
        return resultMap;
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

    /**
     * Check if there are invalid references and write them to server response
     *
     * @param siri                  the response that will be sent to client
     * @param invalidDataReferences list of invalid data references (invalid references are references requested by client that doesn't exist in subcriptions)
     */
    private void handleInvalidDataReferences(Siri siri, List<String> invalidDataReferences) {
        if (invalidDataReferences.isEmpty() || siri.getServiceDelivery().getVehicleMonitoringDeliveries().size() == 0)
            return;

        //Error references need to be inserted in server response to inform client
        ServiceDeliveryErrorConditionElement errorCondition = new ServiceDeliveryErrorConditionElement();
        InvalidDataReferencesErrorStructure invalidDataStruct = new InvalidDataReferencesErrorStructure();
        invalidDataStruct.setErrorText("InvalidDataReferencesError");
        errorCondition.setInvalidDataReferencesError(invalidDataStruct);
        ErrorDescriptionStructure errorDesc = new ErrorDescriptionStructure();
        errorDesc.setValue(invalidDataReferences.stream().collect(Collectors.joining(",")));
        errorCondition.setDescription(errorDesc);
        siri.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).setErrorCondition(errorCondition);
        String requestMsgRef = siri.getServiceDelivery().getRequestMessageRef().getValue();
        siri.getServiceDelivery().getVehicleMonitoringDeliveries().forEach(vm -> logger.info("requestorRef:" + requestMsgRef + " - " + getErrorContents(vm.getErrorCondition())));

    }


    /**
     * Count the number of vehicleActivities existing in the response
     *
     * @param siri
     * @return the number of vehicle activities
     */
    private int countVehicleActivityResults(Siri siri) {
        int nbOfResults = 0;
        if (siri.getServiceDelivery() != null && siri.getServiceDelivery().getVehicleMonitoringDeliveries() != null) {
            for (VehicleMonitoringDeliveryStructure vehicleMonitoringDelivery : siri.getServiceDelivery().getVehicleMonitoringDeliveries()) {
                if (vehicleMonitoringDelivery.getVehicleActivities() == null)
                    continue;

                nbOfResults = nbOfResults + vehicleMonitoringDelivery.getVehicleActivities().size();
            }
        }
        return nbOfResults;
    }

    private boolean hasValues(List list) {
        return (list != null && !list.isEmpty());
    }

    /**
     * Handling incoming requests from external servers
     *
     * @param subscriptionId the subscription's id
     * @param xml            the incoming message
     */
    private void processSiriClientRequest(String subscriptionId, InputStream xml)
            throws XMLStreamException, JAXBException {
        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);

        if (subscriptionSetup != null) {

            int receivedBytes;
            try {
                receivedBytes = xml.available();
            } catch (IOException e) {
                receivedBytes = 0;
            }
            long t1 = System.currentTimeMillis();
            Siri incoming = SiriXml.parseXml(xml);
            long t2 = System.currentTimeMillis();
            logger.debug("Parsing XML took {} ms, {} bytes", (t2 - t1), receivedBytes);
            if (incoming == null) {
                return;
            }

            if (incoming.getHeartbeatNotification() != null) {
                subscriptionManager.touchSubscription(subscriptionId);
                logger.info("Heartbeat - {}", subscriptionSetup);
            } else if (incoming.getCheckStatusResponse() != null) {
                logger.info("Incoming CheckStatusResponse [{}], reporting ServiceStartedTime: {}", subscriptionSetup, incoming.getCheckStatusResponse().getServiceStartedTime());
                subscriptionManager.touchSubscription(subscriptionId, incoming.getCheckStatusResponse().getServiceStartedTime(), null);
            } else if (incoming.getSubscriptionResponse() != null) {
                SubscriptionResponseStructure subscriptionResponse = incoming.getSubscriptionResponse();
                subscriptionResponse.getResponseStatuses().forEach(responseStatus -> {
                    if (responseStatus.isStatus() == null ||
                            (responseStatus.isStatus() != null && responseStatus.isStatus())) {

                        // If no status is provided it is handled as "true"

                        subscriptionManager.activatePendingSubscription(subscriptionId);
                    }
                });

            } else if (incoming.getTerminateSubscriptionResponse() != null) {
                TerminateSubscriptionResponseStructure terminateSubscriptionResponse = incoming.getTerminateSubscriptionResponse();

                logger.info("Subscription terminated {}", subscriptionSetup);

            } else if (incoming.getDataReadyNotification() != null) {
                //Handled using camel routing
            } else if (incoming.getServiceDelivery() != null) {
                boolean deliveryContainsData = false;
                healthManager.dataReceived();

                // used to store the object concerned by the incoming message (stop reference, line reference,etc)
                String monitoredRef = null;

                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.SITUATION_EXCHANGE)) {
                    List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = incoming.getServiceDelivery().getSituationExchangeDeliveries();
                    logger.info("Got SX-delivery: Subscription [{}]", subscriptionSetup);

                    List<PtSituationElement> addedOrUpdated = new ArrayList<>();
                    if (situationExchangeDeliveries != null) {
                        situationExchangeDeliveries.forEach(sx -> {
                                    if (sx != null) {
                                        if (sx.isStatus() != null && !sx.isStatus()) {
                                            logger.info(getErrorContents(sx.getErrorCondition()));
                                        } else {
                                            if (sx.getSituations() != null && sx.getSituations().getPtSituationElements() != null) {
                                                setValidityPeriodAndStartTimeIfNull(sx.getSituations().getPtSituationElements(), subscriptionSetup.getDatasetId());

                                                if (subscriptionSetup.isUseProvidedCodespaceId()) {
                                                    Map<String, List<PtSituationElement>> situationsByCodespace = splitSituationsByCodespace(sx.getSituations().getPtSituationElements());
                                                    for (String codespace : situationsByCodespace.keySet()) {

                                                        // List containing added situations for current codespace
                                                        List<PtSituationElement> addedSituations = new ArrayList();

                                                        addedSituations.addAll(situations.addAll(
                                                                codespace,
                                                                situationsByCodespace.get(codespace)
                                                        ));

                                                        // Push updates to subscribers on this codespace
                                                        serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedSituations, codespace);

                                                        // Add to complete list of added situations
                                                        addedOrUpdated.addAll(addedSituations);

                                                    }

                                                } else {

                                                    addedOrUpdated.addAll(situations.addAll(
                                                            subscriptionSetup.getDatasetId(),
                                                            sx.getSituations().getPtSituationElements()
                                                    ));
                                                    serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedOrUpdated, subscriptionSetup.getDatasetId());
                                                }
                                            }
                                        }
                                    }
                                }
                        );
                    }
                    deliveryContainsData = addedOrUpdated.size() > 0;

                    subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());

                    logger.info("Active SX-elements: {}, current delivery: {}, {}", situations.getSize(), addedOrUpdated.size(), subscriptionSetup);
                }
                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.VEHICLE_MONITORING)) {
                    List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = incoming.getServiceDelivery().getVehicleMonitoringDeliveries();
                    logger.debug("Got VM-delivery: Subscription [{}] {}", subscriptionSetup, subscriptionSetup.forwardPositionData() ? "- Position only" : "");
                    monitoredRef = getLineRef(incoming);

                    List<VehicleActivityStructure> addedOrUpdated = new ArrayList<>();
                    if (vehicleMonitoringDeliveries != null) {
                        vehicleMonitoringDeliveries.forEach(vm -> {
                                    if (vm != null) {
                                        if (vm.isStatus() != null && !vm.isStatus()) {
                                            logger.info(getErrorContents(vm.getErrorCondition()));
                                        } else {
                                            if (vm.getVehicleActivities() != null) {
                                                if (subscriptionSetup.isUseProvidedCodespaceId()) {
                                                    Map<String, List<VehicleActivityStructure>> vehiclesByCodespace = splitVehicleMonitoringByCodespace(vm.getVehicleActivities());
                                                    for (String codespace : vehiclesByCodespace.keySet()) {

                                                        // List containing added situations for current codespace
                                                        List<VehicleActivityStructure> addedVehicles = new ArrayList();

                                                        addedVehicles.addAll(vehicleActivities.addAll(
                                                                codespace,
                                                                vehiclesByCodespace.get(codespace)
                                                        ));

                                                        // Push updates to subscribers on this codespace
                                                        serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedVehicles, codespace);

                                                        // Add to complete list of added situations
                                                        addedOrUpdated.addAll(addedVehicles);

                                                    }

                                                } else {
                                                    addedOrUpdated.addAll(ingestVehicleActivities(subscriptionSetup.getDatasetId(), vm.getVehicleActivities()));
                                                }
                                            }
                                        }
                                    }
                                }
                        );
                    }

                    deliveryContainsData = deliveryContainsData || (addedOrUpdated.size() > 0);

                    serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedOrUpdated, subscriptionSetup.getDatasetId());

                    subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());

                    logger.debug("Active VM-elements: {}, current delivery: {}, {}", vehicleActivities.getSize(), addedOrUpdated.size(), subscriptionSetup);
                }
                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.ESTIMATED_TIMETABLE)) {
                    List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = incoming.getServiceDelivery().getEstimatedTimetableDeliveries();
                    logger.info("Got ET-delivery: Subscription {}", subscriptionSetup);

                    List<EstimatedVehicleJourney> addedOrUpdated = new ArrayList<>();
                    if (estimatedTimetableDeliveries != null) {
                        estimatedTimetableDeliveries.forEach(et -> {
                                    if (et != null) {
                                        if (et.isStatus() != null && !et.isStatus()) {
                                            logger.info(getErrorContents(et.getErrorCondition()));
                                        } else {
                                            if (et.getEstimatedJourneyVersionFrames() != null) {
                                                et.getEstimatedJourneyVersionFrames().forEach(versionFrame -> {
                                                    if (versionFrame != null && versionFrame.getEstimatedVehicleJourneies() != null) {
                                                        if (subscriptionSetup.isUseProvidedCodespaceId()) {
                                                            Map<String, List<EstimatedVehicleJourney>> journeysByCodespace = splitEstimatedTimetablesByCodespace(versionFrame.getEstimatedVehicleJourneies());
                                                            for (String codespace : journeysByCodespace.keySet()) {

                                                                // List containing added situations for current codespace
                                                                List<EstimatedVehicleJourney> addedJourneys = new ArrayList();

                                                                addedJourneys.addAll(estimatedTimetables.addAll(
                                                                        codespace,
                                                                        journeysByCodespace.get(codespace)
                                                                ));

                                                                // Push updates to subscribers on this codespace
                                                                serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedJourneys, codespace);

                                                                // Add to complete list of added situations
                                                                addedOrUpdated.addAll(addedJourneys);

                                                            }

                                                        } else {
                                                            addedOrUpdated.addAll(ingestEstimatedTimeTables(subscriptionSetup.getDatasetId(), versionFrame.getEstimatedVehicleJourneies()));
                                                        }
                                                    }
                                                });
                                            }
                                        }
                                    }
                                }
                        );
                    }

                    deliveryContainsData = deliveryContainsData || (addedOrUpdated.size() > 0);

                    serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedOrUpdated, subscriptionSetup.getDatasetId());

                    subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());

                    logger.info("Active ET-elements: {}, current delivery: {}, {}", estimatedTimetables.getSize(), addedOrUpdated.size(), subscriptionSetup);
                }

                // TODO MHI
                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.STOP_MONITORING)) {
                    List<StopMonitoringDeliveryStructure> stopMonitoringDeliveries = incoming.getServiceDelivery().getStopMonitoringDeliveries();
                    logger.debug("Got SM-delivery: Subscription [{}] ", subscriptionSetup);
                    monitoredRef = getStopRefs(incoming);

                    List<MonitoredStopVisit> addedOrUpdated = new ArrayList<>();
                    if (stopMonitoringDeliveries != null) {
                        stopMonitoringDeliveries.forEach(sm -> {
                                    if (sm != null) {
                                        if (sm.isStatus() != null && !sm.isStatus() || sm.getErrorCondition() != null) {
                                            logger.info(getErrorContents(sm.getErrorCondition()));
                                        } else {
                                            if (sm.getMonitoredStopVisits() != null) {
                                                addedOrUpdated.addAll(ingestStopVisits(subscriptionSetup.getDatasetId(), sm.getMonitoredStopVisits()));
                                            }
                                        }
                                    }
                                }
                        );
                    }

                    deliveryContainsData = deliveryContainsData || (addedOrUpdated.size() > 0);

                    serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedOrUpdated, subscriptionSetup.getDatasetId());

                    subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());

                    logger.debug("Active SM-elements: {}, current delivery: {}, {}", monitoredStopVisits.getSize(), addedOrUpdated.size(), subscriptionSetup);
                }


                if (deliveryContainsData) {
                    subscriptionManager.dataReceived(subscriptionId, receivedBytes, monitoredRef);
                } else {
                    subscriptionManager.touchSubscription(subscriptionId);
                }
            } else {
                try {
                    logger.info("Unsupported SIRI-request:" + SiriXml.toXml(incoming));
                } catch (JAXBException e) {
                    //Ignore
                }
            }
        } else {
            logger.debug("ServiceDelivery for invalid subscriptionId [{}] ignored.", subscriptionId);
        }
    }

    private void setValidityPeriodAndStartTimeIfNull(List<PtSituationElement> situationExchangeDeliveries, String datasetId) {
        for(PtSituationElement situationElement : situationExchangeDeliveries) {
            ZoneId zoneId = ZoneId.systemDefault();
            for(HalfOpenTimestampOutputRangeStructure validityPeriod : situationElement.getValidityPeriods()){
                if(validityPeriod.getStartTime() == null){
                    ZonedDateTime timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.MIN_VALUE), zoneId);
                    validityPeriod.setStartTime(timestamp);
                    logger.info("PtSituationElement without start time and/or validity period for datasetId : " + datasetId +
                            " with situation element id : " + situationElement.getSituationNumber().getValue());
                }
            }
            if(situationElement.getValidityPeriods().isEmpty()){
                HalfOpenTimestampOutputRangeStructure validityPeriod = new HalfOpenTimestampOutputRangeStructure();
                ZonedDateTime timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.MIN_VALUE), zoneId);
                validityPeriod.setStartTime(timestamp);
                situationElement.getValidityPeriods().add(validityPeriod);
                logger.info("PtSituationElement without start time and/or validity period for datasetId : " + datasetId +
                        " with situation element id : " + situationElement.getSituationNumber().getValue());
            }
        }
    }

    /**
     * Handling incoming requests from external servers
     *
     * @param subscriptionIds
     * @param xml
     * @param dataFormat
     * @param dataSetId
     * @throws XMLStreamException
     */
    public void processSiriClientRequestFromApis(List<String> subscriptionIds, InputStream xml, SiriDataType dataFormat, String dataSetId)
            throws XMLStreamException {
        List<SubscriptionSetup> subscriptionSetupList = subscriptionManager.getAll(subscriptionIds);

        if (subscriptionSetupList.size() == 0) {
            logger.debug("ServiceDelivery for invalid subscriptionIds [{}] ignored.", subscriptionIds);
        } else {
            int receivedBytes;
            try {
                receivedBytes = xml.available();
            } catch (IOException e) {
                receivedBytes = 0;
            }

            Siri originalInput = siriXmlValidator.parseXmlWithSubscriptionSetupList(subscriptionSetupList, xml);

            List<ValueAdapter> subscriptionSetupListMappingAdapters = subscriptionSetupList
                    .stream()
                    .flatMap(subscriptionSetup -> subscriptionSetup.getMappingAdapters().stream())
                    .collect(Collectors.toList());

            Siri incoming = SiriValueTransformer.transform(originalInput, subscriptionSetupListMappingAdapters);

            if (incoming.getServiceDelivery() == null) {
                try {
                    logger.info("Unsupported SIRI-request:" + SiriXml.toXml(incoming));
                } catch (JAXBException e) {
                    //Ignore
                }
            } else {
                boolean deliveryContainsData = false;
                healthManager.dataReceived();

                // used to store the object concerned by the incoming message (stop reference, line reference,etc)
                String monitoredRef = null;

                if (dataFormat.equals(SiriDataType.SITUATION_EXCHANGE)) {
                    List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = incoming.getServiceDelivery().getSituationExchangeDeliveries();
                    logger.info("Got SX-delivery: Subscription [{}]", subscriptionSetupList);

                    List<PtSituationElement> addedOrUpdated = new ArrayList<>();
                    if (situationExchangeDeliveries != null) {
                        situationExchangeDeliveries.forEach(sx -> {
                                    if (sx != null) {
                                        if (sx.isStatus() != null && !sx.isStatus()) {
                                            logger.info(getErrorContents(sx.getErrorCondition()));
                                        } else {
                                            if (sx.getSituations() != null && sx.getSituations().getPtSituationElements() != null) {
                                                addedOrUpdated.addAll(situations.addAll(dataSetId, sx.getSituations().getPtSituationElements()));
                                                serverSubscriptionManager.pushUpdatesAsync(dataFormat, addedOrUpdated, dataSetId);
                                            }
                                        }
                                    }
                                }
                        );
                    }
                    deliveryContainsData = addedOrUpdated.size() > 0;

                    for (SubscriptionSetup subscriptionSetup : subscriptionSetupList) {
                        subscriptionManager.incrementObjectCounter(subscriptionSetup, 1);
//                        logger.info("Active SX-elements: {}, current delivery: {}, {}", situations.getSize(), addedOrUpdated.size(), subscriptionSetup);
                    }
                }
                if (dataFormat.equals(SiriDataType.ESTIMATED_TIMETABLE)) {
                    List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = incoming.getServiceDelivery().getEstimatedTimetableDeliveries();
                    logger.info("Got ET-delivery: Subscription {}", subscriptionSetupList);

                    List<EstimatedVehicleJourney> addedOrUpdated = new ArrayList<>();
                    if (estimatedTimetableDeliveries != null) {
                        estimatedTimetableDeliveries.forEach(et -> {
                                    if (et != null) {
                                        if (et.isStatus() != null && !et.isStatus()) {
                                            logger.info(getErrorContents(et.getErrorCondition()));
                                        } else {
                                            if (et.getEstimatedJourneyVersionFrames() != null) {
                                                et.getEstimatedJourneyVersionFrames().forEach(versionFrame -> {
                                                    if (versionFrame != null && versionFrame.getEstimatedVehicleJourneies() != null) {
                                                        addedOrUpdated.addAll(
                                                                estimatedTimetables.addAll(dataSetId, versionFrame.getEstimatedVehicleJourneies())
                                                        );
                                                    }
                                                });
                                            }
                                        }
                                    }
                                }
                        );
                    }

                    deliveryContainsData = deliveryContainsData || (addedOrUpdated.size() > 0);

                    serverSubscriptionManager.pushUpdatesAsync(dataFormat, addedOrUpdated, dataSetId);

                    for (SubscriptionSetup subscriptionSetup : subscriptionSetupList) {
                        List<EstimatedVehicleJourney> addedOrUpdatedBySubscription = addedOrUpdated
                                .stream()
                                .filter(estimatedVehicleJourney -> estimatedVehicleJourney.getLineRef().getValue().equals(subscriptionSetup.getLineRefValue()))
                                .collect(Collectors.toList());
                        subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdatedBySubscription.size());
//                        logger.info("Active ET-elements: {}, current delivery: {}, {}", estimatedTimetables.getSize(), addedOrUpdatedBySubscription.size(), subscriptionSetup);
                    }
                }

                if (dataFormat.equals(SiriDataType.STOP_MONITORING)) {
                    List<StopMonitoringDeliveryStructure> stopMonitoringDeliveries = incoming.getServiceDelivery().getStopMonitoringDeliveries();
                    logger.debug("Got SM-delivery: Subscription [{}] {}", subscriptionSetupList);
                    monitoredRef = getStopRefs(incoming);

                    List<MonitoredStopVisit> addedOrUpdated = new ArrayList<>();
                    if (stopMonitoringDeliveries != null) {
                        stopMonitoringDeliveries.forEach(sm -> {
                                    if (sm != null) {
                                        if (sm.isStatus() != null && !sm.isStatus() || sm.getErrorCondition() != null) {
                                            logger.info(getErrorContents(sm.getErrorCondition()));
                                        } else {
                                            if (sm.getMonitoredStopVisits() != null) {
                                                addedOrUpdated.addAll(
                                                        monitoredStopVisits.addAll(dataSetId, sm.getMonitoredStopVisits()));
                                            }
                                        }
                                    }
                                }
                        );
                    }

                    deliveryContainsData = deliveryContainsData || (addedOrUpdated.size() > 0);

                    serverSubscriptionManager.pushUpdatesAsync(dataFormat, addedOrUpdated, dataSetId);

                    for (SubscriptionSetup subscriptionSetup : subscriptionSetupList) {
                        List<MonitoredStopVisit> addedOrUpdatedBySubscription = addedOrUpdated
                                .stream()
                                .filter(monitoredStopVisit -> monitoredStopVisit.getMonitoringRef().getValue().equals(subscriptionSetup.getStopMonitoringRefValue()))
                                .collect(Collectors.toList());
                        subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdatedBySubscription.size());
//                        logger.info("Active SM-elements: {}, current delivery: {}, {}", monitoredStopVisits.getSize(), addedOrUpdatedBySubscription.size(), subscriptionSetup);
                    }
                }


                for (SubscriptionSetup subscriptionSetup : subscriptionSetupList) {
                    if (deliveryContainsData) {
                        subscriptionManager.dataReceived(subscriptionSetup.getSubscriptionId(), receivedBytes, monitoredRef, false);
                    } else {
                        subscriptionManager.touchSubscription(subscriptionSetup.getSubscriptionId(), null, false);
                    }
                }
            }
        }
    }


    public Collection<VehicleActivityStructure> ingestVehicleActivities(String datasetId, List<VehicleActivityStructure> incomingVehicleActivities) {
        Collection<VehicleActivityStructure> result = vehicleActivities.addAll(datasetId, incomingVehicleActivities);
        if (result.size() > 0) {
            serverSubscriptionManager.pushUpdatesAsync(SiriDataType.VEHICLE_MONITORING, incomingVehicleActivities, datasetId);
        }
        return result;
    }

    public Collection<MonitoredStopVisit> ingestStopVisits(String datasetId, List<MonitoredStopVisit> incomingMonitoredStopVisits) {
        Collection<MonitoredStopVisit> result = monitoredStopVisits.addAll(datasetId, incomingMonitoredStopVisits);
        if (result.size() > 0) {
            serverSubscriptionManager.pushUpdatesAsync(SiriDataType.STOP_MONITORING, incomingMonitoredStopVisits, datasetId);
        }
        return result;
    }


    public Collection<EstimatedVehicleJourney> ingestEstimatedTimeTables(String datasetId, List<EstimatedVehicleJourney> incomingEstimatedTimeTables) {
        Collection<EstimatedVehicleJourney> result = estimatedTimetables.addAll(datasetId, incomingEstimatedTimeTables);
        if (result.size() > 0) {
            serverSubscriptionManager.pushUpdatesAsync(SiriDataType.ESTIMATED_TIMETABLE, incomingEstimatedTimeTables, datasetId);
        }
        return result;
    }

    public Collection<PtSituationElement> ingestSituations(String datasetId, List<PtSituationElement> incomingSituations) {
        Collection<PtSituationElement> result = situations.addAll(datasetId, incomingSituations);
        if (result.size() > 0) {
            serverSubscriptionManager.pushUpdatesAsync(SiriDataType.SITUATION_EXCHANGE, incomingSituations, datasetId);
        }
        return result;
    }

    public void removeSituation(String datasetId, PtSituationElement situation) {
        situations.removeSituation(datasetId, situation);
    }


    /**
     * Read incoming data to find the stopRefs (to identify which stops has received data)
     *
     * @param incomingData incoming message
     * @return A string with all stops found in the incoming message
     */
    private String getStopRefs(Siri incomingData) {
        List<StopMonitoringDeliveryStructure> stopMonitoringDeliveries = incomingData.getServiceDelivery().getStopMonitoringDeliveries();
        List<String> stopRefs = new ArrayList<>();


        for (StopMonitoringDeliveryStructure stopMonitoringDelivery : stopMonitoringDeliveries) {
            for (MonitoredStopVisit monitoredStopVisit : stopMonitoringDelivery.getMonitoredStopVisits()) {
                stopRefs.add(monitoredStopVisit.getMonitoringRef().getValue());
            }
        }

        return stopRefs.stream()
                .distinct()
                .collect(Collectors.joining(","));
    }

    /**
     * Read incoming data to find the lineRef (to identify which line has received data)
     *
     * @param incomingData incoming message
     * @return A string with all lines found in the incoming message
     */
    private String getLineRef(Siri incomingData) {
        List<VehicleMonitoringDeliveryStructure> vehicleDeliveries = incomingData.getServiceDelivery().getVehicleMonitoringDeliveries();
        List<String> lineRefs = new ArrayList<>();

        for (VehicleMonitoringDeliveryStructure vehicleDelivery : vehicleDeliveries) {
            for (VehicleActivityStructure vehicleActivity : vehicleDelivery.getVehicleActivities()) {
                lineRefs.add(vehicleActivity.getMonitoredVehicleJourney().getLineRef().getValue());
            }
        }
        return lineRefs.stream()
                .distinct()
                .collect(Collectors.joining(","));
    }

    private Map<String, List<PtSituationElement>> splitSituationsByCodespace(
            List<PtSituationElement> ptSituationElements
    ) {
        Map<String, List<PtSituationElement>> result = new HashMap<>();
        for (PtSituationElement ptSituationElement : ptSituationElements) {
            final RequestorRef participantRef = ptSituationElement.getParticipantRef();
            if (participantRef != null) {
                final String codespace = getOriginalId(participantRef.getValue());

                //Override mapped value if present
                participantRef.setValue(codespace);

                final List<PtSituationElement> situations = result.getOrDefault(
                        codespace,
                        new ArrayList<>()
                );

                situations.add(ptSituationElement);
                result.put(codespace, situations);
            }
        }
        return result;
    }

    private Map<String, List<VehicleActivityStructure>> splitVehicleMonitoringByCodespace(
            List<VehicleActivityStructure> activityStructures
    ) {
        Map<String, List<VehicleActivityStructure>> result = new HashMap<>();
        for (VehicleActivityStructure vmElement : activityStructures) {
            if (vmElement.getMonitoredVehicleJourney() != null) {

                final String dataSource = vmElement.getMonitoredVehicleJourney().getDataSource();
                if (dataSource != null) {

                    final String codespace = getOriginalId(dataSource);
                    //Override mapped value if present
                    vmElement.getMonitoredVehicleJourney().setDataSource(codespace);

                    final List<VehicleActivityStructure> vehicles = result.getOrDefault(
                            codespace,
                            new ArrayList<>()
                    );

                    vehicles.add(vmElement);
                    result.put(codespace, vehicles);
                }
            }
        }
        return result;
    }

    private Map<String, List<EstimatedVehicleJourney>> splitEstimatedTimetablesByCodespace(
            List<EstimatedVehicleJourney> estimatedVehicleJourneys
    ) {
        Map<String, List<EstimatedVehicleJourney>> result = new HashMap<>();
        for (EstimatedVehicleJourney etElement : estimatedVehicleJourneys) {
            if (etElement.getDataSource() != null) {

                final String dataSource = etElement.getDataSource();
                if (dataSource != null) {

                    final String codespace = getOriginalId(dataSource);
                    //Override mapped value if present
                    etElement.setDataSource(codespace);

                    final List<EstimatedVehicleJourney> etJourneys = result.getOrDefault(
                            codespace,
                            new ArrayList<>()
                    );

                    etJourneys.add(etElement);
                    result.put(codespace, etJourneys);
                }
            }
        }
        return result;
    }


    /**
     * Creates a json-string containing all potential errormessage-values
     *
     * @param errorCondition the error condition to filter
     * @return the error contents
     */
    private String getErrorContents(ServiceDeliveryErrorConditionElement errorCondition) {
        String errorContents = "";
        if (errorCondition != null) {
            Map<String, String> errorMap = new HashMap<>();
            String accessNotAllowed = getErrorText(errorCondition.getAccessNotAllowedError());
            String allowedResourceUsageExceeded = getErrorText(errorCondition.getAllowedResourceUsageExceededError());
            String beyondDataHorizon = getErrorText(errorCondition.getBeyondDataHorizon());
            String capabilityNotSupportedError = getErrorText(errorCondition.getCapabilityNotSupportedError());
            String endpointDeniedAccessError = getErrorText(errorCondition.getEndpointDeniedAccessError());
            String endpointNotAvailableAccessError = getErrorText(errorCondition.getEndpointNotAvailableAccessError());
            String invalidDataReferencesError = getErrorText(errorCondition.getInvalidDataReferencesError());
            String parametersIgnoredError = getErrorText(errorCondition.getParametersIgnoredError());
            String serviceNotAvailableError = getErrorText(errorCondition.getServiceNotAvailableError());
            String unapprovedKeyAccessError = getErrorText(errorCondition.getUnapprovedKeyAccessError());
            String unknownEndpointError = getErrorText(errorCondition.getUnknownEndpointError());
            String unknownExtensionsError = getErrorText(errorCondition.getUnknownExtensionsError());
            String unknownParticipantError = getErrorText(errorCondition.getUnknownParticipantError());
            String noInfoForTopicError = getErrorText(errorCondition.getNoInfoForTopicError());
            String otherError = getErrorText(errorCondition.getOtherError());

            String description = getDescriptionText(errorCondition.getDescription());

            if (accessNotAllowed != null) {
                errorMap.put("accessNotAllowed", accessNotAllowed);
            }
            if (allowedResourceUsageExceeded != null) {
                errorMap.put("allowedResourceUsageExceeded", allowedResourceUsageExceeded);
            }
            if (beyondDataHorizon != null) {
                errorMap.put("beyondDataHorizon", beyondDataHorizon);
            }
            if (capabilityNotSupportedError != null) {
                errorMap.put("capabilityNotSupportedError", capabilityNotSupportedError);
            }
            if (endpointDeniedAccessError != null) {
                errorMap.put("endpointDeniedAccessError", endpointDeniedAccessError);
            }
            if (endpointNotAvailableAccessError != null) {
                errorMap.put("endpointNotAvailableAccessError", endpointNotAvailableAccessError);
            }
            if (invalidDataReferencesError != null) {
                errorMap.put("invalidDataReferencesError", invalidDataReferencesError);
            }
            if (parametersIgnoredError != null) {
                errorMap.put("parametersIgnoredError", parametersIgnoredError);
            }
            if (serviceNotAvailableError != null) {
                errorMap.put("serviceNotAvailableError", serviceNotAvailableError);
            }
            if (unapprovedKeyAccessError != null) {
                errorMap.put("unapprovedKeyAccessError", unapprovedKeyAccessError);
            }
            if (unknownEndpointError != null) {
                errorMap.put("unknownEndpointError", unknownEndpointError);
            }
            if (unknownExtensionsError != null) {
                errorMap.put("unknownExtensionsError", unknownExtensionsError);
            }
            if (unknownParticipantError != null) {
                errorMap.put("unknownParticipantError", unknownParticipantError);
            }
            if (noInfoForTopicError != null) {
                errorMap.put("noInfoForTopicError", noInfoForTopicError);
            }
            if (otherError != null) {
                errorMap.put("otherError", otherError);
            }
            if (description != null) {
                errorMap.put("description", description);
            }

            errorContents = JSONObject.toJSONString(errorMap);
        }
        return errorContents;
    }

    private String getErrorText(ErrorCodeStructure accessNotAllowedError) {
        if (accessNotAllowedError != null) {
            return accessNotAllowedError.getErrorText();
        }
        return null;
    }

    private String getDescriptionText(ErrorDescriptionStructure description) {
        if (description != null) {
            return description.getValue();
        }
        return null;
    }

    public static OutboundIdMappingPolicy getIdMappingPolicy(String useOriginalId) {
        OutboundIdMappingPolicy outboundIdMappingPolicy = OutboundIdMappingPolicy.DEFAULT;
        if (useOriginalId != null) {
            if (Boolean.valueOf(useOriginalId)) {
                outboundIdMappingPolicy = OutboundIdMappingPolicy.ORIGINAL_ID;
            }
        }
        return outboundIdMappingPolicy;
    }
}
