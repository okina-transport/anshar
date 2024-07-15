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

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.UnmarshalException;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.*;
import no.rutebanken.anshar.data.util.TimingTracer;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.inbound.*;
import no.rutebanken.anshar.routes.siri.handlers.outbound.*;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.validation.SiriXmlValidator;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import no.rutebanken.anshar.util.GeneralMessageHelper;
import no.rutebanken.anshar.util.IDUtils;
import org.json.simple.JSONObject;
import org.entur.siri21.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.ErrorCodeStructure;
import uk.org.siri.siri21.ErrorDescriptionStructure;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.RequestorRef;
import uk.org.siri.siri21.ServiceDeliveryErrorConditionElement;
import uk.org.siri.siri21.ServiceRequest;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;
import uk.org.siri.siri21.SubscriptionResponseStructure;
import uk.org.siri.siri21.TerminateSubscriptionRequestStructure;
import uk.org.siri.siri21.TerminateSubscriptionResponseStructure;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleMonitoringDeliveryStructure;
import uk.org.siri.siri21.VehicleMonitoringRequestStructure;
import uk.org.siri.siri21.VehicleRef;

import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getOriginalId;

@Service
public class SiriHandler {

    private static final Logger logger = LoggerFactory.getLogger(SiriHandler.class);

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private Situations situations;

    @Autowired
    private GeneralMessages generalMessages;

    @Autowired
    private GeneralMessagesCancellations generalMessageCancellations;

    @Autowired
    private FacilityMonitoring facilityMonitoring;

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
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private SituationExchangeOutbound situationExchangeOutbound;

    @Autowired
    private DiscoveryStopPointsOutbound discoveryStopPointsOutbound;

    @Autowired
    private DiscoveryLinesOutbound discoveryLinesOutbound;


    @Autowired
    private Utils utils;

    @Autowired
    private SituationExchangeInbound situationExchangeInbound;

    @Autowired
    private EstimatedTimetableInbound estimatedTimetableInbound;

    @Autowired
    private StopMonitoringInbound stopMonitoringInbound;

    @Autowired
    private VehicleMonitoringInbound vehicleMonitoringInbound;

    @Autowired
    private FacilityMonitoringInbound facilityMonitoringInbound;

    @Autowired
    private VehicleMonitoringOutbound vehicleMonitoringOutbound;

    @Autowired
    private EstimatedTimetableOutbound estimatedTimetableOutbound;

    @Autowired
    private StopMonitoringOutbound stopMonitoringOutbound;

    @Autowired
    private FacilityMonitoringOutbound facilityMonitoringOutbound;

    @Autowired
    private GeneralMessageInbound generalMessageInbound;


    public Siri handleIncomingSiri(IncomingSiriParameters incomingSiriParameters) throws UnmarshalException {
        try {
            InputStream xml = incomingSiriParameters.getIncomingSiriStream();
            if (incomingSiriParameters.getSubscriptionId() != null) {
                inboundProcessSiriClientRequest(incomingSiriParameters.getSubscriptionId(), xml); // Response to a request we made on behalf of one of the subscriptions
            } else {
                Siri incoming = SiriValueTransformer.parseXml(xml); // Someone asking us for siri update
                Siri response = outboundProcessSiriServerRequest(incoming, incomingSiriParameters.getDatasetId(), incomingSiriParameters.getExcludedDatasetIdList(),
                        incomingSiriParameters.getOutboundIdMappingPolicy(), incomingSiriParameters.getMaxSize(), incomingSiriParameters.getClientTrackingName(),
                        incomingSiriParameters.isSoapTransformation(), incomingSiriParameters.isUseOriginalId());
                utils.handleFlexibleLines(response);
                return response;

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
                serviceResponse =  siriObjectFactory.createSXServiceDelivery(elements);

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
    private Siri outboundProcessSiriServerRequest(Siri incoming, String datasetId, List<String> excludedDatasetIdList, OutboundIdMappingPolicy outboundIdMappingPolicy, int maxSize, String clientTrackingName, boolean soapTransformation, boolean useOriginalId) {

        if (maxSize < 0) {
            maxSize = configuration.getDefaultMaxSize();

            if (datasetId != null) {
                maxSize = Integer.MAX_VALUE;
            }
        }

        List<ValueAdapter> valueAdapters = new ArrayList();

        Siri results;
        if (incoming.getSubscriptionRequest() != null) {
            logger.info("Handling subscriptionrequest with ID-policy {}.", outboundIdMappingPolicy);
            return serverSubscriptionManager.handleMultipleSubscriptionsRequest(incoming.getSubscriptionRequest(), datasetId, outboundIdMappingPolicy, clientTrackingName, soapTransformation, useOriginalId);

        } else if (incoming.getTerminateSubscriptionRequest() != null) {
            logger.info("Handling terminateSubscriptionrequest...");
            TerminateSubscriptionRequestStructure terminateSubscriptionRequest = incoming.getTerminateSubscriptionRequest();
            if (terminateSubscriptionRequest.getSubscriptionReves() != null && !terminateSubscriptionRequest.getSubscriptionReves().isEmpty()) {
                List<String> terminatedSubscriptions = new ArrayList<>();

                for (SubscriptionQualifierStructure subscriptionReve : terminateSubscriptionRequest.getSubscriptionReves()) {
                    String subscriptionRef = subscriptionReve.getValue();
                    serverSubscriptionManager.terminateSubscription(subscriptionRef, configuration.processAdmin());
                    terminatedSubscriptions.add(subscriptionRef);
                }

                if (configuration.processAdmin()) {
                    return siriObjectFactory.createTerminateSubscriptionResponse(terminatedSubscriptions);
                }
            } else if (terminateSubscriptionRequest.getAll() != null) {
                List<String> terminatedSubscriptions = serverSubscriptionManager.terminateAllsubscriptionsForRequestor(terminateSubscriptionRequest.getRequestorRef().getValue(), configuration.processAdmin());
                if (configuration.processAdmin()) {
                    return siriObjectFactory.createTerminateSubscriptionResponse(terminatedSubscriptions);
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

            if (hasValues(serviceRequest.getSituationExchangeRequests())) {
                serviceResponse = situationExchangeOutbound.createServiceDelivery(requestorRef, datasetId, clientTrackingName, outboundIdMappingPolicy, maxSize);
            } else if (hasValues(serviceRequest.getVehicleMonitoringRequests())) {
                Set<String> lineRefOriginalList = vehicleMonitoringOutbound.getLineRefOriginalList(serviceRequest, outboundIdMappingPolicy, datasetId);
                Set<String> vehicleRefList = vehicleMonitoringOutbound.getVehicleRefList(serviceRequest);

                Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParams(datasetId, lineRefOriginalList, ObjectType.LINE);
                Set<String> revertedLineRefs = IDUtils.revertMonitoringRefs(lineRefOriginalList, idMap.get(ObjectType.LINE));

                valueAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.VEHICLE_MONITORING, outboundIdMappingPolicy, idMap);
                Siri siri = vehicleActivities.createServiceDelivery(requestorRef, datasetId, clientTrackingName, excludedDatasetIdList, maxSize, revertedLineRefs, vehicleRefList);
                serviceResponse = siri;

                if (!revertedLineRefs.isEmpty()) {
                    List<String> invalidDataReferences = revertedLineRefs.stream()
                            .filter(lineRef -> !subscriptionManager.isLineRefExistingInSubscriptions(lineRef))
                            .collect(Collectors.toList());


                    utils.handleInvalidDataReferences(serviceResponse, invalidDataReferences);
                }


                String requestMsgRef = siri.getServiceDelivery().getRequestMessageRef().getValue();
                logger.info("Filtering done. Returning :  {} for requestorRef {}", utils.countVehicleActivityResults(serviceResponse), requestMsgRef);

            } else if (hasValues(serviceRequest.getEstimatedTimetableRequests())) {
                valueAdapters = estimatedTimetableOutbound.getValueAdapters(datasetId, outboundIdMappingPolicy);
                serviceResponse = estimatedTimetableOutbound.getEstimatedTimetableServiceDelivery(serviceRequest, datasetId, excludedDatasetIdList, maxSize, clientTrackingName, requestorRef);
            } else if (hasValues(serviceRequest.getStopMonitoringRequests())) {
                serviceResponse = stopMonitoringOutbound.getStopMonitoringServiceDelivery(serviceRequest, outboundIdMappingPolicy, datasetId, requestorRef, clientTrackingName, maxSize);
            } else if (hasValues(serviceRequest.getGeneralMessageRequests())) {
                Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);

                GeneralMessageRequestStructure request = serviceRequest.getGeneralMessageRequests().get(0);
                List<InfoChannelRefStructure> requestedChannels = request.getInfoChannelReves();
                valueAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.GENERAL_MESSAGE, outboundIdMappingPolicy, idMap);
                serviceResponse = generalMessages.createServiceDelivery(requestorRef, datasetId, clientTrackingName, maxSize, requestedChannels);

                //Ask for general message cancellations at the same time
                Siri cancellationResponses = generalMessageCancellations.createServiceDelivery(requestorRef, datasetId, clientTrackingName, maxSize, requestedChannels);
                // and add cancellations to the general message response

                if (cancellationResponses.getServiceDelivery().getGeneralMessageDeliveries() != null && !cancellationResponses.getServiceDelivery().getGeneralMessageDeliveries().get(0).getGeneralMessageCancellations().isEmpty()) {
                    serviceResponse.getServiceDelivery().getGeneralMessageDeliveries().addAll(cancellationResponses.getServiceDelivery().getGeneralMessageDeliveries());
                }


                GeneralMessageHelper.applyTransformationsInContent(serviceResponse, valueAdapters, idMap);

            } else if (hasValues(serviceRequest.getFacilityMonitoringRequests())) {
                Set<String> facilityRefList = facilityMonitoringOutbound.getFacilityRevesList(serviceRequest);
                Set<String> lineRefOriginalList = facilityMonitoringOutbound.getLineRefOriginalList(serviceRequest, outboundIdMappingPolicy, datasetId);
                Set<String> stopPointRefList = facilityMonitoringOutbound.getStopPointRefList(serviceRequest);
                Set<String> vehicleRefList = facilityMonitoringOutbound.getVehicleRefList(serviceRequest);

                //todo upgrade pour avoir les siteRef
/*                    SiteRefStructure siteRef = req.getSiteRef;
                    if (stopPlaceComponentRef != null) {
                        Set<String> stopPlaceComponentRefList = filterMap.get(StopPlaceComponentRefStructure.class) != null ? filterMap.get(StopPlaceComponentRefStructure.class) : new HashSet<>();
                        stopPlaceComponentRefList.add(stopPlaceComponentRef.getValue());
                        filterMap.put(StopPlaceComponentRefStructure.class, stopPlaceComponentRefList);
                    }*/


                //todo ajouter quand on aura les siteRef
                //Set<String> siteRefList = filterMap.get(SiteRef.class) != null ? filterMap.get(SiteRef.class) : new HashSet<>();


                Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParams(datasetId, lineRefOriginalList, ObjectType.LINE);
                Set<String> revertedLineRefs = IDUtils.revertMonitoringRefs(lineRefOriginalList, idMap.get(ObjectType.LINE));


                if (!revertedLineRefs.isEmpty()) {
                    List<String> invalidDataReferences = revertedLineRefs.stream()
                            .filter(lineRef -> !subscriptionManager.isLineRefExistingInSubscriptions(lineRef))
                            .collect(Collectors.toList());

                    utils.handleInvalidDataReferences(serviceResponse, invalidDataReferences);
                }

                Siri siri = facilityMonitoring.createServiceDelivery(requestorRef, datasetId, clientTrackingName, excludedDatasetIdList, maxSize,
                        revertedLineRefs, facilityRefList, vehicleRefList, stopPointRefList);
                serviceResponse = siri;
                String requestMsgRef = siri.getServiceDelivery().getRequestMessageRef().getValue();
                logger.info("Filtering done. Returning :  {} for requestorRef {}", utils.countVehicleActivityResults(serviceResponse), requestMsgRef);


                valueAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.FACILITY_MONITORING, outboundIdMappingPolicy, idMap);
                GeneralMessageHelper.applyTransformationsInContent(serviceResponse, valueAdapters, idMap);

            }


            if (serviceResponse != null) {
                metrics.countOutgoingData(serviceResponse, SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE);
                return shouldExecuteLastIdTransformation(serviceRequest) ? SiriValueTransformer.transform(serviceResponse, valueAdapters, false, false) : serviceResponse;
            }
        } else if (incoming.getStopPointsRequest() != null) {
            TimingTracer timingTracer = new TimingTracer("StopDiscovery-" + datasetId);
            // stop discovery request
            results = discoveryStopPointsOutbound.getDiscoveryStopPoints(datasetId, outboundIdMappingPolicy);
            timingTracer.mark("extraction completed");
            if (timingTracer.getTotalTime() > 3000) {
                logger.warn(timingTracer.toString());
            }
            return results;
        } else if (incoming.getLinesRequest() != null) {
            TimingTracer timingTracer = new TimingTracer("LinesDiscovery-" + datasetId);
            // lines discovery request (for vehicle monitoring)
            results = discoveryLinesOutbound.getDiscoveryLines(datasetId, outboundIdMappingPolicy);
            timingTracer.mark("extraction completed");
            if (timingTracer.getTotalTime() > 3000) {
                logger.warn(timingTracer.toString());
            }
            return results;
        }

        return null;
    }


    /**
     * Defines if ids should be transformed at the end of the process.
     * For stop monitoring and situation exchange : requests are done dataset by dataset and are already transformed
     * For others : no transformations have been done. Need to execute a last transformation on the file
     *
     * @param serviceRequest original request made by user
     * @return true : last id transformation must be executed
     * false : last id transformation must not be done
     */
    private boolean shouldExecuteLastIdTransformation(ServiceRequest serviceRequest) {
        return !hasValues(serviceRequest.getStopMonitoringRequests()) && !hasValues(serviceRequest.getSituationExchangeRequests());
    }


    private boolean hasValues(List list) {
        return (list != null && !list.isEmpty());
    }

    /**
     * Handling incoming requests from external servers
     *
     * @param subscriptionId
     * @param xml
     * @return
     * @throws JAXBException
     */
    private void inboundProcessSiriClientRequest(String subscriptionId, InputStream xml)
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
                    deliveryContainsData = situationExchangeInbound.ingestSituationExchange(subscriptionSetup, incoming);
                }
                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.VEHICLE_MONITORING)) {
                    monitoredRef = vehicleMonitoringInbound.getLineRef(incoming);
                    deliveryContainsData = vehicleMonitoringInbound.ingestVehicleMonitoring(subscriptionSetup, incoming);
                }
                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.ESTIMATED_TIMETABLE)) {
                    deliveryContainsData = estimatedTimetableInbound.ingestEstimatedTimetable(subscriptionSetup, incoming);
                }

                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.STOP_MONITORING)) {
                    monitoredRef = utils.getStopRefs(incoming);
                    deliveryContainsData = stopMonitoringInbound.ingestStopVisit(subscriptionSetup, incoming);
                }

                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.GENERAL_MESSAGE)) {
                    monitoredRef = utils.getStopRefs(incoming);
                    deliveryContainsData = generalMessageInbound.ingestGeneralMessage(subscriptionSetup, incoming);
                }

                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.FACILITY_MONITORING)) {
                    monitoredRef = utils.getStopRefs(incoming);
                    deliveryContainsData = facilityMonitoringInbound.ingestFacility(subscriptionSetup, incoming);
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


    /**
     * Handling incoming requests from external servers
     *
     * @param subscriptionIds
     * @param xml
     * @param dataFormat
     * @param dataSetId
     * @throws XMLStreamException
     */
    public void processSiriClientRequestFromApis(List<String> subscriptionIds, InputStream xml, SiriDataType dataFormat, String dataSetId) throws XMLStreamException {
        List<SubscriptionSetup> subscriptionSetupList = subscriptionManager.getAll(subscriptionIds);

        if (subscriptionSetupList.isEmpty()) {
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
                    deliveryContainsData = situationExchangeInbound.ingestSituationExchangeFromApi(dataFormat, dataSetId, incoming, subscriptionSetupList);
                }
                if (dataFormat.equals(SiriDataType.ESTIMATED_TIMETABLE)) {
                    deliveryContainsData = estimatedTimetableInbound.ingestEstimatedTimetableFromApi(dataFormat, dataSetId, incoming, subscriptionSetupList);
                }
                if (dataFormat.equals(SiriDataType.STOP_MONITORING)) {
                    monitoredRef = utils.getStopRefs(incoming);
                    deliveryContainsData = stopMonitoringInbound.ingestStopVisitFromApi(dataFormat, dataSetId, incoming, subscriptionSetupList);
                }
                if (dataFormat.equals(SiriDataType.VEHICLE_MONITORING)) {
                    monitoredRef = vehicleMonitoringInbound.getVehicleRefs(incoming);
                    deliveryContainsData = vehicleMonitoringInbound.ingestVehicleMonitoringFromApi(dataFormat, dataSetId, incoming, subscriptionSetupList);
                }
                if (dataFormat.equals(SiriDataType.GENERAL_MESSAGE)) {
                    monitoredRef = utils.getStopRefs(incoming);
                    deliveryContainsData = generalMessageInbound.ingestGeneralMessageFromApi(dataFormat, dataSetId, incoming, subscriptionSetupList);
                }
                if (dataFormat.equals(SiriDataType.FACILITY_MONITORING)) {
                    monitoredRef = utils.getStopRefs(incoming);
                    deliveryContainsData = facilityMonitoringInbound.ingestFacilityFromApi(dataFormat, dataSetId, incoming, subscriptionSetupList);
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

    public static OutboundIdMappingPolicy getIdMappingPolicy(String useOriginalId, String altId) {
        OutboundIdMappingPolicy outboundIdMappingPolicy = OutboundIdMappingPolicy.DEFAULT;
        if (altId != null) {
            if (Boolean.parseBoolean(altId)) {
                outboundIdMappingPolicy = OutboundIdMappingPolicy.ALT_ID;
            }
        }

        if (useOriginalId != null) {
            if (Boolean.parseBoolean(useOriginalId)) {
                outboundIdMappingPolicy = OutboundIdMappingPolicy.ORIGINAL_ID;
            }
        }
        return outboundIdMappingPolicy;
    }


    /**
     * Creates a json-string containing all potential errormessage-values
     *
     * @param errorCondition
     * @return
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

    public static OutboundIdMappingPolicy getIdMappingPolicy(String useOriginalId, String altId) {
        OutboundIdMappingPolicy outboundIdMappingPolicy = OutboundIdMappingPolicy.DEFAULT;
        if (altId != null) {
            if (Boolean.parseBoolean(altId)) {
                outboundIdMappingPolicy = OutboundIdMappingPolicy.ALT_ID;
            }
        }

        if (useOriginalId != null) {
            if (Boolean.parseBoolean(useOriginalId)) {
                outboundIdMappingPolicy = OutboundIdMappingPolicy.ORIGINAL_ID;
            }
        }
        return outboundIdMappingPolicy;
    }
}
