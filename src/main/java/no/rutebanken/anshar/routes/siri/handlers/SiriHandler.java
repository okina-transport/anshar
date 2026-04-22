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
import no.rutebanken.anshar.config.*;
import no.rutebanken.anshar.data.*;
import no.rutebanken.anshar.data.util.CustomStringUtils;
import no.rutebanken.anshar.data.util.TimingTracer;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.inbound.*;
import no.rutebanken.anshar.routes.siri.handlers.outbound.*;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.helpers.StopMonitoringServiceDeliveryParameter;
import no.rutebanken.anshar.routes.siri.processor.FacilityRefPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.GmSIVSicAQuayPostProcessor;
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
import org.apache.commons.collections4.CollectionUtils;
import org.entur.siri21.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.org.siri.siri21.*;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class SiriHandler {

    private static final Logger logger = LoggerFactory.getLogger(SiriHandler.class);

    @Value("${anshar.super.identifier}")
    private String superIdentifier;

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

    @Autowired
    StopPlaceUpdaterService stopPlaceUpdaterService;

    @Autowired
    AnsharConfiguration ansharConfiguration;

    @Value("${anshar.disable.outbound.subscription.checks:false}")
    private boolean disableOutboundSubscriptionChecks;

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

    public Siri handleIncomingSiri(IncomingSiriParameters incomingSiriParameters) throws UnmarshalException {
        try {
            InputStream xml = incomingSiriParameters.getIncomingSiriStream();
            if (incomingSiriParameters.getSubscriptionId() != null) {
                inboundProcessSiriClientRequest(incomingSiriParameters, xml); // Response to a request we made on behalf of one of the subscriptions
            } else {
                Siri incoming = SiriValueTransformer.parseXml(xml); // Someone asking us for siri update
                return buildSiriResponse(incomingSiriParameters, incoming);
            }
        } catch (UnmarshalException e) {
            throw e;
        } catch (JAXBException | XMLStreamException e) {
            logger.warn("Caught exception when parsing incoming XML", e);
        }
        return null;
    }

    public Siri buildSiriResponse(IncomingSiriParameters incomingSiriParameters, Siri request) {
        Siri response = outboundProcessSiriServerRequest(request, incomingSiriParameters);
        utils.handleFlexibleLines(response);
        incomingSiriParameters.setVersion(request.getVersion());
        return response;
    }

    public Siri handleSiriCacheRequest(InputStream body, String datasetId, String clientTrackingName) throws XMLStreamException, JAXBException {

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

                final Collection<PtSituationElement> elements = situations.getAllCachedUpdates(requestorRef, datasetId, clientTrackingName);
                logger.info("Returning {} elements from cache", elements.size());
                serviceResponse = siriObjectFactory.createSXServiceDelivery(elements, requestorRef, serviceRequest.getMessageIdentifier().getValue());

            } else if (hasValues(serviceRequest.getVehicleMonitoringRequests())) {
                dataType = SiriDataType.VEHICLE_MONITORING;

                final Collection<VehicleActivityStructure> elements = vehicleActivities.getAllCachedUpdates(requestorRef, datasetId, clientTrackingName);
                logger.info("Returning {} elements from cache", elements.size());
                serviceResponse = siriObjectFactory.createVMServiceDelivery(elements, requestorRef, serviceRequest.getMessageIdentifier().getValue());

            } else if (hasValues(serviceRequest.getEstimatedTimetableRequests())) {
                dataType = SiriDataType.ESTIMATED_TIMETABLE;

                final Collection<EstimatedVehicleJourney> elements = estimatedTimetables.getAllCachedUpdates(requestorRef, datasetId, clientTrackingName);

                logger.info("Returning {} elements from cache", elements.size());
                serviceResponse = siriObjectFactory.createETServiceDelivery(elements, requestorRef, serviceRequest.getMessageIdentifier().getValue());

            }


            if (serviceResponse != null) {
                metrics.countOutgoingData(serviceResponse, SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE);


                return SiriValueTransformer.transform(serviceResponse, MappingAdapterPresets.getOutboundAdapters(dataType, OutboundIdMappingPolicy.DEFAULT, subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId)), false, false);
            }
        }
        return null;
    }

    /**
     * Handling incoming requests from external clients
     *
     * @param incoming               incoming message
     * @param incomingSiriParameters received parameters
     */
    private Siri outboundProcessSiriServerRequest(Siri incoming, IncomingSiriParameters incomingSiriParameters) {
        String datasetId = incomingSiriParameters.getDatasetId();
        List<String> excludedDatasetIdList = incomingSiriParameters.getExcludedDatasetIdList();
        OutboundIdMappingPolicy outboundIdMappingPolicy = incomingSiriParameters.getOutboundIdMappingPolicy();
        int maxSize = incomingSiriParameters.getMaxSize();
        String clientTrackingName = incomingSiriParameters.getClientTrackingName();
        if (maxSize < 0) {
            maxSize = configuration.getDefaultMaxSize();

            if (datasetId != null) {
                maxSize = Integer.MAX_VALUE;
            }
        }

        List<ValueAdapter> valueAdapters = new ArrayList<>();

        Siri results;
        if (incoming.getSubscriptionRequest() != null) {
            logger.info("Handling subscriptionrequest with ID-policy {}.", outboundIdMappingPolicy);

            if (!ansharConfiguration.isInitialized()) {
                return generateServerIsIntializingError(incoming);
            }

            results = serverSubscriptionManager.handleMultipleSubscriptionsRequest(incoming, incomingSiriParameters);

            if (CollectionUtils.isNotEmpty(incoming.getSubscriptionRequest().getStopMonitoringSubscriptionRequests())) {
                List<String> unKnownStopMonitoring = new ArrayList<>();
                for (StopMonitoringSubscriptionStructure stopMonitoringSubcriptionRequest : incoming.getSubscriptionRequest().getStopMonitoringSubscriptionRequests()) {
                    String monitoringRef = stopMonitoringSubcriptionRequest.getStopMonitoringRequest().getMonitoringRef().getValue();

                    if (!validateStopReferences(monitoringRef, datasetId)) {
                        unKnownStopMonitoring.add("MonitoringRef: " + monitoringRef + ", datasetId: " + datasetId);
                    }
                }
                if (CollectionUtils.isNotEmpty(unKnownStopMonitoring)) {
                    results = utils.createInvalidDataReferencesSubscriptionResponse(unKnownStopMonitoring, results);
                }
            }
            return results;
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
            String messageId = serviceRequest.getMessageIdentifier() != null ? serviceRequest.getMessageIdentifier().getValue() : null;
            String requestorRef = null;

            Siri serviceResponse = null;

            if (serviceRequest.getRequestorRef() != null) {
                requestorRef = serviceRequest.getRequestorRef().getValue();
            }

            if (hasValues(serviceRequest.getSituationExchangeRequests())) {
                serviceResponse = situationExchangeOutbound.createServiceDelivery(requestorRef, datasetId, clientTrackingName, outboundIdMappingPolicy, maxSize, messageId);
            } else if (hasValues(serviceRequest.getVehicleMonitoringRequests())) {
                Set<String> lineRefOriginalList = vehicleMonitoringOutbound.getLineRefOriginalList(serviceRequest, outboundIdMappingPolicy, datasetId);
                Set<String> vehicleRefList = vehicleMonitoringOutbound.getVehicleRefList(serviceRequest);

                Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParams(datasetId, lineRefOriginalList, ObjectType.LINE);
                Set<String> revertedLineRefs = IDUtils.revertMonitoringRefs(lineRefOriginalList, idMap.get(ObjectType.LINE));

                revertedLineRefs = revertedLineRefs.stream().map(CustomStringUtils::revertChouetteIdTransformation).collect(Collectors.toSet());

                valueAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.VEHICLE_MONITORING, outboundIdMappingPolicy, idMap);
                Siri siri = vehicleActivities.createServiceDelivery(requestorRef, datasetId, clientTrackingName, excludedDatasetIdList, maxSize, revertedLineRefs, vehicleRefList, messageId);
                serviceResponse = siri;


                String requestMsgRef = siri.getServiceDelivery().getRequestMessageRef().getValue();
                logger.info("Filtering done. Returning :  {} for requestorRef {}", utils.countVehicleActivityResults(serviceResponse), requestMsgRef);

            } else if (hasValues(serviceRequest.getEstimatedTimetableRequests())) {
                valueAdapters = estimatedTimetableOutbound.getValueAdapters(datasetId, outboundIdMappingPolicy);
                serviceResponse = estimatedTimetableOutbound.getEstimatedTimetableServiceDelivery(serviceRequest, datasetId, excludedDatasetIdList, maxSize, clientTrackingName, requestorRef, messageId);
            } else if (hasValues(serviceRequest.getStopMonitoringRequests())) {
                incomingSiriParameters.setMaxSize(maxSize);
                StopMonitoringServiceDeliveryParameter stopMonitoringServiceDeliveryParameter = new StopMonitoringServiceDeliveryParameter(serviceRequest, incomingSiriParameters);
                serviceResponse = stopMonitoringOutbound.getStopMonitoringServiceDelivery(stopMonitoringServiceDeliveryParameter);
            } else if (hasValues(serviceRequest.getGeneralMessageRequests())) {
                Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);

                GeneralMessageRequestStructure request = serviceRequest.getGeneralMessageRequests().get(0);
                List<InfoChannelRefStructure> requestedChannels = request.getInfoChannelReves();
                valueAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.GENERAL_MESSAGE, outboundIdMappingPolicy, idMap);

                serviceResponse = generalMessages.createServiceDelivery(requestorRef, datasetId, clientTrackingName, maxSize, requestedChannels, messageId);

                if (incomingSiriParameters.isGmSIVSicAQuay()) {
                    // value adapters are cached
                    // create a new list, otherwise it will keep adding GmSIVSicAQuayPostProcessor to cached value adapters
                    valueAdapters = new ArrayList<>(valueAdapters);
                    valueAdapters.add(new GmSIVSicAQuayPostProcessor());

                    GmSIVSicAQuayPostProcessor.filteringSiriGMToKeepSicAQuayAlertMessages(serviceResponse);
                }

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
                    List<String> invalidDataReferences = revertedLineRefs.stream().filter(lineRef -> !subscriptionManager.isLineRefExistingInSubscriptions(lineRef)).collect(Collectors.toList());

                    utils.handleInvalidDataReferences(serviceResponse, invalidDataReferences);
                }

                Siri siri = facilityMonitoring.createServiceDelivery(requestorRef, datasetId, clientTrackingName, excludedDatasetIdList, maxSize, revertedLineRefs, facilityRefList, vehicleRefList, stopPointRefList, messageId);
                serviceResponse = siri;
                String requestMsgRef = siri.getServiceDelivery().getRequestMessageRef().getValue();
                logger.info("Filtering done. Returning :  {} for requestorRef {}", utils.countVehicleActivityResults(serviceResponse), requestMsgRef);


                valueAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.FACILITY_MONITORING, outboundIdMappingPolicy, idMap);
                valueAdapters.add(new FacilityRefPostProcessor(datasetId, outboundIdMappingPolicy));
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

    private Siri generateServerIsIntializingError(Siri incoming) {


        Siri errorResponse = new Siri();
        SubscriptionResponseStructure subscriptionResponse = new SubscriptionResponseStructure();
        ResponseStatus respStatus = new ResponseStatus();
        respStatus.setStatus(false);
        ServiceDeliveryErrorConditionElement errorConditionElement = new ServiceDeliveryErrorConditionElement();
        ServiceNotAvailableErrorStructure serviceNotAvailableError = new ServiceNotAvailableErrorStructure();
        serviceNotAvailableError.setErrorText("Server is initializing");
        errorConditionElement.setServiceNotAvailableError(serviceNotAvailableError);
        respStatus.setErrorCondition(errorConditionElement);
        subscriptionResponse.getResponseStatuses().add(respStatus);
        subscriptionResponse.setResponseTimestamp(ZonedDateTime.now());


        String msgId = getMsgIdentifier(incoming);
        MessageRefStructure msgStructure = new MessageRefStructure();
        msgStructure.setValue(msgId);
        subscriptionResponse.setRequestMessageRef(msgStructure);

        errorResponse.setSubscriptionResponse(subscriptionResponse);


        return errorResponse;
    }

    private String getMsgIdentifier(Siri incoming) {
        if (incoming.getSubscriptionRequest() == null) {
            return null;
        }
        List<String> messageIds = new ArrayList<>();
        SubscriptionRequest subscriptionRequest = incoming.getSubscriptionRequest();

        if (!subscriptionRequest.getStopMonitoringSubscriptionRequests().isEmpty()) {
            for (StopMonitoringSubscriptionStructure stopMonitoringSubscriptionRequest : incoming.getSubscriptionRequest().getStopMonitoringSubscriptionRequests()) {
                if (stopMonitoringSubscriptionRequest.getStopMonitoringRequest().getMessageIdentifier() != null) {
                    messageIds.add(stopMonitoringSubscriptionRequest.getStopMonitoringRequest().getMessageIdentifier().getValue());
                }
            }
        }

        if (!subscriptionRequest.getVehicleMonitoringSubscriptionRequests().isEmpty()) {
            for (VehicleMonitoringSubscriptionStructure vehicleMonitoringSubscriptionRequest : subscriptionRequest.getVehicleMonitoringSubscriptionRequests()) {
                if (vehicleMonitoringSubscriptionRequest.getVehicleMonitoringRequest().getMessageIdentifier() != null) {
                    messageIds.add(vehicleMonitoringSubscriptionRequest.getVehicleMonitoringRequest().getMessageIdentifier().getValue());
                }
            }
        }

        if (!subscriptionRequest.getEstimatedTimetableSubscriptionRequests().isEmpty()) {
            for (EstimatedTimetableSubscriptionStructure estimatedTimetableSubscriptionRequest : subscriptionRequest.getEstimatedTimetableSubscriptionRequests()) {
                if (estimatedTimetableSubscriptionRequest.getEstimatedTimetableRequest().getMessageIdentifier() != null) {
                    messageIds.add(estimatedTimetableSubscriptionRequest.getEstimatedTimetableRequest().getMessageIdentifier().getValue());
                }
            }
        }

        if (!subscriptionRequest.getSituationExchangeSubscriptionRequests().isEmpty()) {
            for (SituationExchangeSubscriptionStructure situationExchangeSubscriptionRequest : subscriptionRequest.getSituationExchangeSubscriptionRequests()) {
                if (situationExchangeSubscriptionRequest.getSituationExchangeRequest().getMessageIdentifier() != null) {
                    messageIds.add(situationExchangeSubscriptionRequest.getSituationExchangeRequest().getMessageIdentifier().getValue());
                }
            }
        }

        if (!subscriptionRequest.getFacilityMonitoringSubscriptionRequests().isEmpty()) {
            for (FacilityMonitoringSubscriptionStructure facilityMonitoringSubscriptionRequest : subscriptionRequest.getFacilityMonitoringSubscriptionRequests()) {
                if (facilityMonitoringSubscriptionRequest.getFacilityMonitoringRequest().getMessageIdentifier() != null) {
                    messageIds.add(facilityMonitoringSubscriptionRequest.getFacilityMonitoringRequest().getMessageIdentifier().getValue());
                }
            }
        }

        if (!subscriptionRequest.getGeneralMessageSubscriptionRequests().isEmpty()) {
            for (GeneralMessageSubscriptionStructure generalMessageSubscriptionRequest : subscriptionRequest.getGeneralMessageSubscriptionRequests()) {
                if (generalMessageSubscriptionRequest.getGeneralMessageRequest().getMessageIdentifier() != null) {
                    messageIds.add(generalMessageSubscriptionRequest.getGeneralMessageRequest().getMessageIdentifier().getValue());
                }
            }
        }


        return String.join(",", messageIds);
    }


    /**
     * Valide les références d'arrêt (stopRef) en fonction d'un nouvel algorithme.
     *
     * @param stopRef   L'identifiant de l'arrêt à valider.
     * @param datasetId Le ou les dataset(s) provenant du header, sous forme de chaîne de caractères séparés par des virgules.
     * @return true si la référence est valide, false sinon.
     */
    private boolean validateStopReferences(String stopRef, String datasetId) {

        if (disableOutboundSubscriptionChecks) {
            return true;
        }

        if (stopRef == null) {
            return false;
        }

        if (stopRef.startsWith(superIdentifier)) {
            // cas 1: le datasetId n'est pas précisé dans le header.
            if (!StringUtils.hasText(datasetId)) {
                return !stopPlaceUpdaterService.getReverse(stopRef, null).isEmpty();
            }

            // cas 2: le datasetId est précisé dans le header.
            List<String> partialHeaderDatasets = Arrays.asList(datasetId.split(","));
            Set<String> datasetsToActuallyCheck = new HashSet<>();

            for (String partialId : partialHeaderDatasets) {
                String trimmedPartialId = partialId.trim();
                if (stopPlaceUpdaterService.isDatasetKnown(trimmedPartialId)) {
                    datasetsToActuallyCheck.add(trimmedPartialId);
                }
            }

            if (datasetsToActuallyCheck.isEmpty()) {
                return false;
            }

            return datasetsToActuallyCheck.stream().anyMatch(knownDataset -> !stopPlaceUpdaterService.getReverse(stopRef, knownDataset).isEmpty());

        } else {
            // --- ID PRODUCTEUR ---
            String[] parts = stopRef.split(":", 2);
            if (parts.length < 2) {
                return false;
            }
            String producerDatasetId = parts[0];

            if (!stopPlaceUpdaterService.isDatasetKnown(producerDatasetId)) {
                return true;
            }

            return stopPlaceUpdaterService.isKnownId(stopRef);
        }
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
     * @param incomingSiriParameters the parameters
     * @param xml                    the incoming message
     */
    private void inboundProcessSiriClientRequest(IncomingSiriParameters incomingSiriParameters, InputStream xml) throws XMLStreamException, JAXBException {

        String subscriptionId = incomingSiriParameters.getSubscriptionId();

        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);


        Optional<DiscoverySubscription> discoverySubsOpt = subscriptionManager.getDiscoverySubscription(subscriptionId);
        if (subscriptionSetup != null || discoverySubsOpt.isPresent()) {

            int receivedBytes;
            try {
                receivedBytes = xml.available();
            } catch (IOException e) {
                receivedBytes = 0;
            }
            long t1 = System.currentTimeMillis();
            Siri incoming = SiriXml.parseXml(xml);

            if (discoverySubsOpt.isPresent()) {
                Optional<SubscriptionSetup> childSubscriptionOpt = subscriptionManager.getChildSubscriptionId(discoverySubsOpt.get(), incoming);
                if (childSubscriptionOpt.isPresent()) {
                    subscriptionSetup = childSubscriptionOpt.get();
                } else {
                    subscriptionSetup = new SubscriptionSetup();
                    subscriptionSetup.setDatasetId(discoverySubsOpt.get().getDatasetId());
                    subscriptionSetup.setSubscriptionType(discoverySubsOpt.get().getDiscoveryType());
                }
            }


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
                    logger.info("Subscription response received: {} with status: {}", subscriptionId, responseStatus.isStatus());
                });
                subscriptionManager.touchSubscription(subscriptionId);

            } else if (incoming.getTerminateSubscriptionResponse() != null) {
                logger.info("Terminate subscription received: {}", subscriptionId);

            } else if (incoming.getDataReadyNotification() != null) {
                //Handled using camel routing
            } else if (incoming.getServiceDelivery() != null) {
                boolean deliveryContainsData = false;
                healthManager.dataReceived();

                // used to store the object concerned by the incoming message (stop reference, line reference,etc)
                String monitoredRef = null;

                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.SITUATION_EXCHANGE)) {
                    deliveryContainsData = situationExchangeInbound.ingestSituationExchange(subscriptionSetup, incoming, incomingSiriParameters.getInboundTime());
                }
                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.VEHICLE_MONITORING)) {
                    monitoredRef = vehicleMonitoringInbound.getLineRef(incoming);
                    deliveryContainsData = vehicleMonitoringInbound.ingestVehicleMonitoring(subscriptionSetup, incoming, incomingSiriParameters.getInboundTime());
                }
                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.ESTIMATED_TIMETABLE)) {
                    deliveryContainsData = estimatedTimetableInbound.ingestEstimatedTimetable(subscriptionSetup, incoming, incomingSiriParameters.getInboundTime());
                }

                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.STOP_MONITORING)) {
                    monitoredRef = utils.getStopRefs(incoming);
                    deliveryContainsData = stopMonitoringInbound.ingestStopVisit(subscriptionSetup, incoming, incomingSiriParameters.getInboundTime());
                }

                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.GENERAL_MESSAGE)) {
                    monitoredRef = utils.getStopRefs(incoming);
                    deliveryContainsData = generalMessageInbound.ingestGeneralMessage(subscriptionSetup, incoming, incomingSiriParameters.getInboundTime());
                }

                if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.FACILITY_MONITORING)) {
                    monitoredRef = utils.getStopRefs(incoming);
                    deliveryContainsData = facilityMonitoringInbound.ingestFacility(subscriptionSetup, incoming, incomingSiriParameters.getInboundTime());
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
            Long inboundTime = System.currentTimeMillis();

            Siri originalInput = siriXmlValidator.parseXmlWithSubscriptionSetupList(subscriptionSetupList, xml);

            List<ValueAdapter> subscriptionSetupListMappingAdapters = subscriptionSetupList.stream().flatMap(subscriptionSetup -> subscriptionSetup.getMappingAdapters().stream()).collect(Collectors.toList());

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
                    deliveryContainsData = situationExchangeInbound.ingestSituationExchangeFromApi(dataFormat, dataSetId, incoming, subscriptionSetupList, inboundTime);
                }
                if (dataFormat.equals(SiriDataType.ESTIMATED_TIMETABLE)) {
                    deliveryContainsData = estimatedTimetableInbound.ingestEstimatedTimetableFromApi(dataFormat, dataSetId, incoming, subscriptionSetupList, inboundTime);
                }
                if (dataFormat.equals(SiriDataType.STOP_MONITORING)) {
                    monitoredRef = utils.getStopRefs(incoming);
                    deliveryContainsData = stopMonitoringInbound.ingestStopVisitFromApi(dataFormat, dataSetId, incoming, subscriptionSetupList, inboundTime);
                }
                if (dataFormat.equals(SiriDataType.VEHICLE_MONITORING)) {
                    monitoredRef = vehicleMonitoringInbound.getVehicleRefs(incoming);
                    deliveryContainsData = vehicleMonitoringInbound.ingestVehicleMonitoringFromApi(dataFormat, dataSetId, incoming, subscriptionSetupList, inboundTime);
                }
                if (dataFormat.equals(SiriDataType.GENERAL_MESSAGE)) {
                    monitoredRef = utils.getStopRefs(incoming);
                    deliveryContainsData = generalMessageInbound.ingestGeneralMessageFromApi(dataFormat, dataSetId, incoming, subscriptionSetupList, inboundTime);
                }
                if (dataFormat.equals(SiriDataType.FACILITY_MONITORING)) {
                    monitoredRef = utils.getStopRefs(incoming);
                    deliveryContainsData = facilityMonitoringInbound.ingestFacilityFromApi(dataFormat, dataSetId, incoming, subscriptionSetupList, inboundTime);
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
}
