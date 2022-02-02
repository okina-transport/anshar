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

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.outbound.SiriHelper;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.validation.SiriXmlValidator;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.json.simple.JSONObject;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.ErrorCodeStructure;
import uk.org.siri.siri20.ErrorDescriptionStructure;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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


    @Produce(uri = "direct:forward.position.data")
    ProducerTemplate positionForwardRoute;


    public Siri handleIncomingSiri(String subscriptionId, InputStream xml) throws UnmarshalException {
        return handleIncomingSiri(subscriptionId, xml, null, -1);
    }

    private Siri handleIncomingSiri(String subscriptionId, InputStream xml, String datasetId, int maxSize) throws UnmarshalException {
        return handleIncomingSiri(subscriptionId, xml, datasetId, null, maxSize, null);
    }

    /**
     *
     * @param subscriptionId SubscriptionId
     * @param xml SIRI-request as XML
     * @param datasetId Optional datasetId
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

    /**
     * Handling incoming requests from external clients
     *
     * @param incoming incoming message
     * @param excludedDatasetIdList dataset to exclude
     *
     */
    private Siri processSiriServerRequest(Siri incoming, String datasetId, List<String> excludedDatasetIdList, OutboundIdMappingPolicy outboundIdMappingPolicy, int maxSize, String clientTrackingName) {

        if (maxSize < 0) {
            maxSize = configuration.getDefaultMaxSize();

            if (datasetId != null) {
                maxSize = Integer.MAX_VALUE;
            }
        }

        if (incoming.getSubscriptionRequest() != null) {
            logger.info("Handling subscriptionrequest with ID-policy {}.", outboundIdMappingPolicy);
            return serverSubscriptionManager.handleSubscriptionRequest(incoming.getSubscriptionRequest(), datasetId, outboundIdMappingPolicy, clientTrackingName);

        } else if (incoming.getTerminateSubscriptionRequest() != null) {
            logger.info("Handling terminateSubscriptionrequest...");
            TerminateSubscriptionRequestStructure terminateSubscriptionRequest = incoming.getTerminateSubscriptionRequest();
            if (terminateSubscriptionRequest.getSubscriptionReves() != null && !terminateSubscriptionRequest.getSubscriptionReves().isEmpty()) {
                String subscriptionRef = terminateSubscriptionRequest.getSubscriptionReves().get(0).getValue();

                serverSubscriptionManager.terminateSubscription(subscriptionRef);
                return siriObjectFactory.createTerminateSubscriptionResponse(subscriptionRef);
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
                serviceResponse = situations.createServiceDelivery(requestorRef, datasetId, clientTrackingName, maxSize);
            } else if (hasValues(serviceRequest.getVehicleMonitoringRequests())) {
                dataType = SiriDataType.VEHICLE_MONITORING;
                Map<Class, Set<String>> filterMap = new HashMap<>();
                for (VehicleMonitoringRequestStructure req : serviceRequest.getVehicleMonitoringRequests()) {
                    LineRef lineRef = req.getLineRef();
                    if (lineRef != null) {
                        Set<String> linerefList = filterMap.get(LineRef.class) != null ? filterMap.get(LineRef.class): new HashSet<>();
                        linerefList.add(lineRef.getValue());
                        filterMap.put(LineRef.class, linerefList);
                    }
                    VehicleRef vehicleRef = req.getVehicleRef();
                    if (vehicleRef != null) {
                        Set<String> vehicleRefList = filterMap.get(VehicleRef.class) != null ? filterMap.get(VehicleRef.class): new HashSet<>();
                        vehicleRefList.add(vehicleRef.getValue());
                        filterMap.put(VehicleRef.class, vehicleRefList);
                    }
                }


                Siri siri = vehicleActivities.createServiceDelivery(requestorRef, datasetId, clientTrackingName, excludedDatasetIdList, maxSize);

                serviceResponse = SiriHelper.filterSiriPayload(siri, filterMap);
                List<String> invalidDataReferences  = filterMap.get(LineRef.class).stream()
                                                            .filter(lineRef -> !subscriptionManager.isLineRefExistingInSubscriptions(lineRef))
                                                            .collect(Collectors.toList());


                handleInvalidDataReferences(serviceResponse,invalidDataReferences);
                String requestMsgRef = siri.getServiceDelivery().getRequestMessageRef().getValue();
                logger.info("Filtering done. Returning :  {} for requestorRef {}", countVehicleActivityResults(serviceResponse), requestMsgRef);

            } else if (hasValues(serviceRequest.getEstimatedTimetableRequests())) {
                dataType = SiriDataType.ESTIMATED_TIMETABLE;
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

                for (StopMonitoringRequestStructure req : serviceRequest.getStopMonitoringRequests()) {
                    MonitoringRefStructure monitoringRef = req.getMonitoringRef();
                    if (monitoringRef != null) {
                        Set<String> monitoringRefs = filterMap.get(MonitoringRefStructure.class) != null ? filterMap.get(MonitoringRefStructure.class): new HashSet<>();
                        monitoringRefs.add(monitoringRef.getValue());
                        filterMap.put(MonitoringRefStructure.class, monitoringRefs);
                    }
                }

                Siri siri = monitoredStopVisits.createServiceDelivery(requestorRef, datasetId, clientTrackingName, maxSize);
                serviceResponse = SiriHelper.filterSiriPayload(siri, filterMap);
            }


            if (serviceResponse != null) {
                metrics.countOutgoingData(serviceResponse, SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE);
                return SiriValueTransformer.transform(
                    serviceResponse,
                    MappingAdapterPresets.getOutboundAdapters(dataType, outboundIdMappingPolicy),
                    false,
                    false
                );
            }
        }

        return null;
    }

    /**
     * Check if there are invalid references and write them to server response
     * @param siri
     *    the response that will be sent to client
     * @param invalidDataReferences
     *    list of invalid data references (invalid references are references requested by client that doesn't exist in subcriptions)
     */
    private void handleInvalidDataReferences(Siri siri, List<String> invalidDataReferences){
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
        siri.getServiceDelivery().getVehicleMonitoringDeliveries().forEach(vm-> logger.info("requestorRef:"+requestMsgRef + " - " + getErrorContents(vm.getErrorCondition())));

    }


    /**
     * Count the number of vehicleActivities existing in the response
     * @param siri
     * @return
     * the number of vehicle activities
     */
    private int countVehicleActivityResults(Siri siri){
        int nbOfResults = 0;
        if (siri.getServiceDelivery() != null && siri.getServiceDelivery().getVehicleMonitoringDeliveries() != null){
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
     * @param xml the incoming message
     */
    private void processSiriClientRequest(String subscriptionId, InputStream xml)
        throws XMLStreamException {
        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);

        if (subscriptionSetup != null) {

            int receivedBytes;
            try {
                receivedBytes = xml.available();
            } catch (IOException e) {
                receivedBytes = 0;
            }

            Siri originalInput = siriXmlValidator.parseXml(subscriptionSetup, xml);

            Siri incoming = SiriValueTransformer.transform(originalInput, subscriptionSetup.getMappingAdapters());

            if (incoming.getHeartbeatNotification() != null) {
                subscriptionManager.touchSubscription(subscriptionId);
                logger.info("Heartbeat - {}", subscriptionSetup);
            } else if (incoming.getCheckStatusResponse() != null) {
                logger.info("Incoming CheckStatusResponse [{}], reporting ServiceStartedTime: {}", subscriptionSetup, incoming.getCheckStatusResponse().getServiceStartedTime());
                subscriptionManager.touchSubscription(subscriptionId, incoming.getCheckStatusResponse().getServiceStartedTime(),null);
            } else if (incoming.getSubscriptionResponse() != null) {
                SubscriptionResponseStructure subscriptionResponse = incoming.getSubscriptionResponse();
                subscriptionResponse.getResponseStatuses().forEach(responseStatus -> {
                    if (responseStatus.isStatus() != null && responseStatus.isStatus()) {
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
                                                if (subscriptionSetup.isUseCodespaceFromParticipantRef()) {
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
                    logger.debug("Got VM-delivery: Subscription [{}] {}", subscriptionSetup, subscriptionSetup.forwardPositionData() ? "- Position only":"");
                    monitoredRef = getLineRef(incoming);

                    List<VehicleActivityStructure> addedOrUpdated = new ArrayList<>();
                    if (vehicleMonitoringDeliveries != null) {
                        vehicleMonitoringDeliveries.forEach(vm -> {
                                    if (vm != null) {
                                        if (vm.isStatus() != null && !vm.isStatus()) {
                                            logger.info(getErrorContents(vm.getErrorCondition()));
                                        } else {
                                            if (vm.getVehicleActivities() != null) {
                                                if (subscriptionSetup.forwardPositionData()) {
                                                    positionForwardRoute.sendBody(incoming);
                                                    addedOrUpdated.addAll(vm.getVehicleActivities());
                                                    logger.info("Forwarding VM positiondata for {} vehicles.", vm.getVehicleActivities().size());
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
                                                        addedOrUpdated.addAll(ingestEstimatedTimeTables(subscriptionSetup.getDatasetId(), versionFrame.getEstimatedVehicleJourneies()));
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
                                                addedOrUpdated.addAll(
                                                        monitoredStopVisits.addAll(subscriptionSetup.getDatasetId(), sm.getMonitoredStopVisits()));
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



    public Collection<VehicleActivityStructure> ingestVehicleActivities(String subscriptionId, List<VehicleActivityStructure> incomingVehicleActivities) {
        return vehicleActivities.addAll(subscriptionId, incomingVehicleActivities);
    }


    public Collection<EstimatedVehicleJourney> ingestEstimatedTimeTables(String subscriptionId, List<EstimatedVehicleJourney> incomingEstimatedTimeTables) {
        return  estimatedTimetables.addAll(subscriptionId, incomingEstimatedTimeTables);
    }

    public Collection<PtSituationElement> ingestSituations(String subscriptionId, List<PtSituationElement> incomingSituations) {
        return situations.addAll(subscriptionId, incomingSituations);
    }


    /**
     * Read incoming data to find the stopRefs (to identify which stops has received data)
     * @param incomingData
     *  incoming message
     * @return
     *  A string with all stops found in the incoming message
     */
    private String getStopRefs(Siri incomingData){
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
     * @param incomingData
     *  incoming message
     * @return
     *  A string with all lines found in the incoming message
     */
    private String getLineRef(Siri incomingData){
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

            if (accessNotAllowed != null) {errorMap.put("accessNotAllowed", accessNotAllowed);}
            if (allowedResourceUsageExceeded != null) {errorMap.put("allowedResourceUsageExceeded", allowedResourceUsageExceeded);}
            if (beyondDataHorizon != null) {errorMap.put("beyondDataHorizon", beyondDataHorizon);}
            if (capabilityNotSupportedError != null) {errorMap.put("capabilityNotSupportedError", capabilityNotSupportedError);}
            if (endpointDeniedAccessError != null) {errorMap.put("endpointDeniedAccessError", endpointDeniedAccessError);}
            if (endpointNotAvailableAccessError != null) {errorMap.put("endpointNotAvailableAccessError", endpointNotAvailableAccessError);}
            if (invalidDataReferencesError != null) {errorMap.put("invalidDataReferencesError", invalidDataReferencesError);}
            if (parametersIgnoredError != null) {errorMap.put("parametersIgnoredError", parametersIgnoredError);}
            if (serviceNotAvailableError != null) {errorMap.put("serviceNotAvailableError", serviceNotAvailableError);}
            if (unapprovedKeyAccessError != null) {errorMap.put("unapprovedKeyAccessError", unapprovedKeyAccessError);}
            if (unknownEndpointError != null) {errorMap.put("unknownEndpointError", unknownEndpointError);}
            if (unknownExtensionsError != null) {errorMap.put("unknownExtensionsError", unknownExtensionsError);}
            if (unknownParticipantError != null) {errorMap.put("unknownParticipantError", unknownParticipantError);}
            if (noInfoForTopicError != null) {errorMap.put("noInfoForTopicError", noInfoForTopicError);}
            if (otherError != null) {errorMap.put("otherError", otherError);}
            if (description != null) {errorMap.put("description", description);}

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
