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

import com.hazelcast.map.IMap;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.util.TimingTracer;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import no.rutebanken.anshar.util.SiriUtils;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.*;

import javax.xml.datatype.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.MILLIS;
import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;


@SuppressWarnings("unchecked")
@Service
@Configuration
public class ServerSubscriptionManager {

    private static final Logger logger = LoggerFactory.getLogger(ServerSubscriptionManager.class);

    public static final String CODESPACE_ID_KAFKA_HEADER_NAME = "codespaceId";

    @Autowired
    IMap<String, OutboundSubscriptionSetup> subscriptions;

    @Autowired
    @Qualifier("getFailTrackerMap")
    private IMap<String, Instant> failTrackerMap;

    @Autowired
    @Qualifier("getHeartbeatTimestampMap")
    private IMap<String, Instant> heartbeatTimestampMap;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Value("${anshar.outbound.heartbeatinterval.minimum}")
    private long minimumHeartbeatInterval = 10000;

    @Value("${anshar.outbound.heartbeatinterval.maximum}")
    private long maximumHeartbeatInterval = 300000;

    @Value("${anshar.outbound.error.consumeraddress}")
    private String errorConsumerAddressMissing = "Error";

    @Value("${anshar.outbound.error.initialtermination}")
    private String initialTerminationTimePassed = "Error";

    @Value("${anshar.outbound.pubsub.topic.enabled}")
    private boolean pushToTopicEnabled;

    @Value("${external.sx.consumer.enabled}")
    private boolean pushToExternalSxConsumer;

    @Produce(uri = "direct:send.to.pubsub.topic.estimated_timetable")
    protected ProducerTemplate siriEtTopicProducer;

    @Produce(uri = "direct:send.to.pubsub.topic.vehicle_monitoring")
    protected ProducerTemplate siriVmTopicProducer;

    @Produce(uri = "direct:send.to.pubsub.topic.situation_exchange")
    protected ProducerTemplate siriSxTopicProducer;

    @Produce(uri = "direct:send.to.pubsub.topic.stop_monitoring")
    protected ProducerTemplate siriSmTopicProducer;

    @Autowired
    private CamelRouteManager camelRouteManager;

    @Autowired
    private SiriHelper siriHelper;

    @Value("${send.activemq.kafka}")
    private boolean sendActivemqKafka;

    @Produce(uri = "direct:send.sm.to.kafka")
    protected ProducerTemplate sendSMToKafka;

    @Produce(uri = "direct:send.sx.to.kafka")
    protected ProducerTemplate sendSXToKafka;

    @Produce(uri = "direct:send.sx.to.external.consumer")
    protected ProducerTemplate sendSXToExternalConsumer;

    @Produce(uri = "direct:send.vm.to.kafka")
    protected ProducerTemplate sendVMToKafka;

    @Produce(uri = "direct:send.et.to.kafka")
    protected ProducerTemplate sendETToKafka;

    @Produce(uri = "direct:send.gm.to.kafka")
    protected ProducerTemplate sendGMToKafka;

    @Produce(uri = "direct:send.fm.to.kafka")
    protected ProducerTemplate sendFMToKafka;

    @Autowired
    private SubscriptionConfig incomingSubscriptionConfig;

    @Autowired
    private StopPlaceUpdaterService stopPlaceUpdaterService;

    Map<String, List<OutboundSubscriptionSetup>> outboundSubscriptionsByMonitoringRef = new HashMap<>();

    @Value("${anshar.push.updated.thread.pool:10}")
    private int pushUpdatedThreadPool;

    private int pushIteration = 0;


    ThreadPoolExecutor outboundSenderExecutorService;


    public Collection getSubscriptions() {
        return Collections.unmodifiableCollection(subscriptions.values());
    }

    public JSONArray getSubscriptionsAsJson() {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

        JSONArray stats = new JSONArray();

        for (String key : subscriptions.keySet()) {

            OutboundSubscriptionSetup subscription = subscriptions.get(key);

            JSONObject obj = new JSONObject();
            obj.put("subscriptionRef", "" + key);
            obj.put("subscriptionType", "" + subscription.getSubscriptionType());
            obj.put("address", "" + subscription.getAddress());
            obj.put("heartbeatInterval", "" + (subscription.getHeartbeatInterval() / 1000) + " s");
            obj.put("datasetId", subscription.getDatasetId() != null ? subscription.getDatasetId() : "");
            obj.put("requestReceived", formatter.format(subscription.getRequestTimestamp()));
            obj.put("initialTerminationTime", formatter.format(subscription.getInitialTerminationTime()));
            obj.put("clientTrackingName", subscription.getClientTrackingName() != null ? subscription.getClientTrackingName() : "");
            obj.put("filteredRefs", getFilteredRefs(subscription.getFilterMap()));

            stats.add(obj);
        }

        return stats;
    }

    private String getFilteredRefs(Map<Class, Set<String>> filterMap) {
        StringBuilder filteredRefs = new StringBuilder();
        boolean hasStopsFiltered = false;

        if (filterMap != null) {

            if (filterMap.containsKey(MonitoringRefStructure.class)) {
                String stopRefs = String.join(",", filterMap.get(MonitoringRefStructure.class));
                filteredRefs.append("stops:" + stopRefs);
                hasStopsFiltered = true;
            }

            if (filterMap.containsKey(LineRef.class)) {

                if (hasStopsFiltered) {
                    filteredRefs.append("/");
                }

                String lineRefs = String.join(",", filterMap.get(LineRef.class));
                filteredRefs.append("lines:" + lineRefs);
            }
        }
        return filteredRefs.toString();
    }

    /**
     * Handle subscription request that can contain one or multiple subcriptions
     *
     * @param incomingSiri
     * @param datasetId
     * @param outboundIdMappingPolicy
     * @param clientTrackingName
     * @param soapTransformation
     * @return
     */
    public Siri handleMultipleSubscriptionsRequest(Siri incomingSiri, String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy, String clientTrackingName, boolean soapTransformation, boolean useOriginalId) {
        SubscriptionRequest subscriptionRequest = incomingSiri.getSubscriptionRequest();
        if (subscriptionRequest.getStopMonitoringSubscriptionRequests() != null && subscriptionRequest.getStopMonitoringSubscriptionRequests().size() > 1) {
            return handleMultipleStopMonitoringRequest(incomingSiri, datasetId, outboundIdMappingPolicy, clientTrackingName, soapTransformation, useOriginalId);
        } else if (subscriptionRequest.getVehicleMonitoringSubscriptionRequests() != null && subscriptionRequest.getVehicleMonitoringSubscriptionRequests().size() > 1) {
            return handleMultipleVehicleMonitoringRequest(incomingSiri, datasetId, outboundIdMappingPolicy, clientTrackingName, soapTransformation, useOriginalId);
        } else {
            return handleSingleSubscriptionRequest(incomingSiri, datasetId, outboundIdMappingPolicy, clientTrackingName, soapTransformation, useOriginalId);
        }
    }

    private Siri handleMultipleVehicleMonitoringRequest(Siri incomingSiri, String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy, String clientTrackingName, boolean soapTransformation, boolean useOriginalId) {

        SubscriptionRequest subscriptionRequest = incomingSiri.getSubscriptionRequest();
        List<Siri> resultList = new ArrayList<>();
        RequestorRef requestorRef = subscriptionRequest.getRequestorRef();
        String consumerAddress = subscriptionRequest.getConsumerAddress();
        SubscriptionContextStructure subscriptionContext = subscriptionRequest.getSubscriptionContext();
        MessageQualifierStructure messageIdentifier = subscriptionRequest.getMessageIdentifier();

        for (VehicleMonitoringSubscriptionStructure vehicleMonitoringSubscriptionRequest : subscriptionRequest.getVehicleMonitoringSubscriptionRequests()) {
            Siri singleSiriRequest = new Siri();
            SubscriptionRequest singleRequest = new SubscriptionRequest();
            singleRequest.getVehicleMonitoringSubscriptionRequests().add(vehicleMonitoringSubscriptionRequest);
            singleRequest.setRequestorRef(requestorRef);
            singleRequest.setConsumerAddress(consumerAddress);
            singleRequest.setSubscriptionContext(subscriptionContext);
            singleRequest.setMessageIdentifier(messageIdentifier);
            singleSiriRequest.setSubscriptionRequest(singleRequest);
            singleSiriRequest.setVersion(incomingSiri.getVersion());

            Siri currentResult = handleSingleSubscriptionRequest(singleSiriRequest, datasetId, outboundIdMappingPolicy, clientTrackingName, soapTransformation, useOriginalId);
            resultList.add(currentResult);
        }

        return aggregateResults(resultList);
    }

    private Siri handleMultipleStopMonitoringRequest(Siri incomingSiri, String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy, String clientTrackingName, boolean soapTransformation, boolean useOriginalId) {

        SubscriptionRequest subscriptionRequest = incomingSiri.getSubscriptionRequest();
        List<Siri> resultList = new ArrayList<>();
        RequestorRef requestorRef = subscriptionRequest.getRequestorRef();
        String consumerAddress = subscriptionRequest.getConsumerAddress();
        SubscriptionContextStructure subscriptionContext = subscriptionRequest.getSubscriptionContext();
        MessageQualifierStructure messageIdentifier = subscriptionRequest.getMessageIdentifier();

        for (StopMonitoringSubscriptionStructure stopMonitoringSubscriptionRequest : subscriptionRequest.getStopMonitoringSubscriptionRequests()) {
            Siri singleSiriRequest = new Siri();
            SubscriptionRequest singleRequest = new SubscriptionRequest();
            singleRequest.getStopMonitoringSubscriptionRequests().add(stopMonitoringSubscriptionRequest);
            singleRequest.setRequestorRef(requestorRef);
            singleRequest.setConsumerAddress(consumerAddress);
            singleRequest.setSubscriptionContext(subscriptionContext);
            singleRequest.setMessageIdentifier(messageIdentifier);
            singleSiriRequest.setSubscriptionRequest(singleRequest);
            singleSiriRequest.setVersion(incomingSiri.getVersion());

            Siri currentResult = handleSingleSubscriptionRequest(singleSiriRequest, datasetId, outboundIdMappingPolicy, clientTrackingName, soapTransformation, useOriginalId);
            resultList.add(currentResult);
        }

        return aggregateResults(resultList);
    }

    private Siri aggregateResults(List<Siri> resultList) {

        Siri result = null;
        for (Siri currentSiri : resultList) {
            if (result == null) {
                result = currentSiri;
                continue;
            }
            result.getSubscriptionResponse().getResponseStatuses().add(currentSiri.getSubscriptionResponse().getResponseStatuses().get(0));
        }
        return result;
    }


    /**
     * Handle a subcription request that contains only one subscription
     *
     * @param incomingSiri
     * @param datasetId
     * @param outboundIdMappingPolicy
     * @param clientTrackingName
     * @return
     */
    public Siri handleSingleSubscriptionRequest(Siri incomingSiri, String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy, String clientTrackingName, boolean soapTransformation, boolean useOriginalId) {

        OutboundSubscriptionSetup subscription = createSubscription(incomingSiri, datasetId, outboundIdMappingPolicy, clientTrackingName, useOriginalId);
        subscription.setSOAPSubscription(soapTransformation);

        boolean hasError = false;
        String errorText = null;

        if (subscription.getAddress() == null) {
            hasError = true;
            errorText = errorConsumerAddressMissing;
        } else if (subscription.getInitialTerminationTime() == null || subscription.getInitialTerminationTime().isBefore(ZonedDateTime.now())) {
            //Subscription has already expired
            hasError = true;
            errorText = initialTerminationTimePassed;
        }

        if (subscriptions.containsKey(subscription.getSubscriptionId())) {

            final OutboundSubscriptionSetup subscriptionSetup = subscriptions.get(subscription.getSubscriptionId());

            if (subscription.getSubscriptionType() != subscriptionSetup.getSubscriptionType()) {
                hasError = true;
                errorText = "A different subscription with id=" + subscription.getSubscriptionId() + " already exists";
            }
        }

        if (hasError) {
            return siriObjectFactory.createSubscriptionResponse(subscription.getSubscriptionId(), false, errorText, incomingSiri.getVersion());
        } else {
            addSubscription(subscription);

            Siri subscriptionResponse = siriObjectFactory.createSubscriptionResponse(subscription.getSubscriptionId(), true, null, incomingSiri.getVersion());

            if (subscription.getSubscriptionType().equals(SiriDataType.SITUATION_EXCHANGE)) {
                sendInitialDelivery(subscription);
            }
            return subscriptionResponse;
        }
    }

    private void sendInitialDelivery(OutboundSubscriptionSetup subscription) {
        final String breadcrumbId = MDC.get("camel.breadcrumbId");
        Executors.newSingleThreadScheduledExecutor().execute(() -> {
            try {
                MDC.put("camel.breadcrumbId", breadcrumbId);

                //Send initial ServiceDelivery
                logger.info("Find initial delivery for {}", subscription);
                Siri delivery = siriHelper.findInitialDeliveryData(subscription);

                if (delivery != null) {
                    logger.info("Sending initial delivery to {}", subscription.getAddress());
                    camelRouteManager.pushSiriData(delivery, subscription, false);
                } else {
                    logger.info("No initial delivery found for {}", subscription);
                }
            } catch (Exception e) {
                logger.error("Error while sending initial delivery", e);
            } finally {
                MDC.remove("camel.breadcrumbId");
            }
        });
    }


    private OutboundSubscriptionSetup createSubscription(Siri incomingSiri, String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy, String clientTrackingName, boolean useOrignalId) {
        SubscriptionRequest subscriptionRequest = incomingSiri.getSubscriptionRequest();
        List<ValueAdapter> mappers;
        String version = getVersion(incomingSiri);
        if (subscriptionRequest.getStopMonitoringSubscriptionRequests() != null && subscriptionRequest.getStopMonitoringSubscriptionRequests().size() > 0) {
            Map<ObjectType, Optional<IdProcessingParameters>> idProcessingParams = siriHelper.getIdProcessingParamsFromSubscription(subscriptionRequest.getStopMonitoringSubscriptionRequests().get(0), outboundIdMappingPolicy, datasetId);
            mappers = MappingAdapterPresets.getOutboundAdapters(SiriDataType.STOP_MONITORING, outboundIdMappingPolicy, idProcessingParams);
        } else if (subscriptionRequest.getVehicleMonitoringSubscriptionRequests() != null && subscriptionRequest.getVehicleMonitoringSubscriptionRequests().size() > 0) {
            Map<ObjectType, Optional<IdProcessingParameters>> idProcessingParams = siriHelper.getIdProcessingParamsFromSubscription(subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0), outboundIdMappingPolicy, datasetId);
            mappers = MappingAdapterPresets.getOutboundAdapters(SiriDataType.VEHICLE_MONITORING, outboundIdMappingPolicy, idProcessingParams);
        } else {
            mappers = MappingAdapterPresets.getOutboundAdapters(outboundIdMappingPolicy);
        }

        return new OutboundSubscriptionSetup(
                ZonedDateTime.now(),
                getSubscriptionType(subscriptionRequest),
                subscriptionRequest.getConsumerAddress() != null ? subscriptionRequest.getConsumerAddress() : subscriptionRequest.getAddress(),
                getHeartbeatInterval(subscriptionRequest),
                getIncrementalUpdates(subscriptionRequest),
                getChangeBeforeUpdates(subscriptionRequest),
                getUpdateInterval(subscriptionRequest),
                siriHelper.getFilter(subscriptionRequest, outboundIdMappingPolicy, datasetId),
                mappers,
                findSubscriptionIdentifier(subscriptionRequest),
                subscriptionRequest.getRequestorRef().getValue(),
                findInitialTerminationTime(subscriptionRequest),
                datasetId,
                clientTrackingName,
                useOrignalId,
                SiriUtils.getVersionEnum(version)
        );
    }

    private String getVersion(Siri incomingSiri) {

        String version = "";


        if (incomingSiri.getSubscriptionRequest() != null) {
            SubscriptionRequest subRequest = incomingSiri.getSubscriptionRequest();
            if (subRequest.getStopMonitoringSubscriptionRequests() != null && subRequest.getStopMonitoringSubscriptionRequests().size() > 0) {
                for (StopMonitoringSubscriptionStructure stopMonitoringSubscriptionRequest : subRequest.getStopMonitoringSubscriptionRequests()) {
                    if (stopMonitoringSubscriptionRequest.getStopMonitoringRequest() != null && stopMonitoringSubscriptionRequest.getStopMonitoringRequest().getVersion() != null) {
                        version = stopMonitoringSubscriptionRequest.getStopMonitoringRequest().getVersion();
                    }
                }
            }

            if (subRequest.getEstimatedTimetableSubscriptionRequests() != null && subRequest.getEstimatedTimetableSubscriptionRequests().size() > 0) {
                for (EstimatedTimetableSubscriptionStructure estimatedTimetableSubscriptionRequest : subRequest.getEstimatedTimetableSubscriptionRequests()) {
                    if (estimatedTimetableSubscriptionRequest.getEstimatedTimetableRequest() != null && estimatedTimetableSubscriptionRequest.getEstimatedTimetableRequest().getVersion() != null) {
                        version = estimatedTimetableSubscriptionRequest.getEstimatedTimetableRequest().getVersion();
                    }
                }


            }

            if (subRequest.getVehicleMonitoringSubscriptionRequests() != null && subRequest.getVehicleMonitoringSubscriptionRequests().size() > 0) {
                for (VehicleMonitoringSubscriptionStructure vehicleMonitoringSubscriptionRequest : subRequest.getVehicleMonitoringSubscriptionRequests()) {
                    if (vehicleMonitoringSubscriptionRequest.getVehicleMonitoringRequest() != null && vehicleMonitoringSubscriptionRequest.getVehicleMonitoringRequest().getVersion() != null) {
                        version = vehicleMonitoringSubscriptionRequest.getVehicleMonitoringRequest().getVersion();
                    }
                }
            }

            if (subRequest.getSituationExchangeSubscriptionRequests() != null && subRequest.getSituationExchangeSubscriptionRequests().size() > 0) {
                for (SituationExchangeSubscriptionStructure situationExchangeSubscriptionRequest : subRequest.getSituationExchangeSubscriptionRequests()) {
                    if (situationExchangeSubscriptionRequest.getSituationExchangeRequest() != null && situationExchangeSubscriptionRequest.getSituationExchangeRequest().getVersion() != null) {

                        version = situationExchangeSubscriptionRequest.getSituationExchangeRequest().getVersion();
                    }
                }
            }

            if (subRequest.getFacilityMonitoringSubscriptionRequests() != null && subRequest.getFacilityMonitoringSubscriptionRequests().size() > 0) {
                for (FacilityMonitoringSubscriptionStructure facilityMonitoringSubscriptionRequest : subRequest.getFacilityMonitoringSubscriptionRequests()) {
                    if (facilityMonitoringSubscriptionRequest.getFacilityMonitoringRequest() != null && facilityMonitoringSubscriptionRequest.getFacilityMonitoringRequest().getVersion() != null) {
                        version = facilityMonitoringSubscriptionRequest.getFacilityMonitoringRequest().getVersion();
                    }
                }
            }

            if (subRequest.getGeneralMessageSubscriptionRequests() != null && subRequest.getGeneralMessageSubscriptionRequests().size() > 0) {
                for (GeneralMessageSubscriptionStructure generalMessageSubscriptionRequest : subRequest.getGeneralMessageSubscriptionRequests()) {
                    if (generalMessageSubscriptionRequest.getGeneralMessageRequest() != null && generalMessageSubscriptionRequest.getGeneralMessageRequest().getVersion() != null) {
                        version = generalMessageSubscriptionRequest.getGeneralMessageRequest().getVersion();
                    }
                }
            }

        }


        return version == null ? incomingSiri.getVersion() : version;

    }

    // public for unittest
    public long getHeartbeatInterval(SubscriptionRequest subscriptionRequest) {
        long heartbeatInterval = 0;
        if (subscriptionRequest.getSubscriptionContext() != null &&
                subscriptionRequest.getSubscriptionContext().getHeartbeatInterval() != null) {
            Duration interval = subscriptionRequest.getSubscriptionContext().getHeartbeatInterval();
            heartbeatInterval = interval.getTimeInMillis(new Date(0));
        }
        heartbeatInterval = Math.max(heartbeatInterval, minimumHeartbeatInterval);
        heartbeatInterval = Math.min(heartbeatInterval, maximumHeartbeatInterval);

        return heartbeatInterval;
    }

    private SiriDataType getSubscriptionType(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {
            return SiriDataType.SITUATION_EXCHANGE;
        } else if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            return SiriDataType.VEHICLE_MONITORING;
        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            return SiriDataType.ESTIMATED_TIMETABLE;
        } else if (SiriHelper.containsValues(subscriptionRequest.getStopMonitoringSubscriptionRequests())) {
            return SiriDataType.STOP_MONITORING;
        } else if (SiriHelper.containsValues(subscriptionRequest.getGeneralMessageSubscriptionRequests())) {
            return SiriDataType.GENERAL_MESSAGE;
        } else if (SiriHelper.containsValues(subscriptionRequest.getFacilityMonitoringSubscriptionRequests())) {
            return SiriDataType.FACILITY_MONITORING;
        }
        return null;
    }

    private boolean getIncrementalUpdates(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            return subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).isIncrementalUpdates() == null || subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).isIncrementalUpdates();
        } else if (SiriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {
            return subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0).isIncrementalUpdates() == null || subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0).isIncrementalUpdates();
        }
        return true;
    }

    private int getChangeBeforeUpdates(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            return getMilliSeconds(subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getChangeBeforeUpdates());
        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            return getMilliSeconds(subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getChangeBeforeUpdates());
        }
        return 0;
    }

    private long getUpdateInterval(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests()) && subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getUpdateInterval() != null) {
            return subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getUpdateInterval().getTimeInMillis(new Date(0));
        }
        return 0;
    }

    private int getMilliSeconds(Duration changeBeforeUpdates) {
        if (changeBeforeUpdates != null) {
            return changeBeforeUpdates.getSeconds() * 1000;
        }
        return 0;
    }

    public void addSubscription(OutboundSubscriptionSetup subscription) {
        subscriptions.put(subscription.getSubscriptionId(), subscription);

        if (SiriDataType.STOP_MONITORING.equals(subscription.getSubscriptionType()) && subscription.getFilterMap().containsKey(MonitoringRefStructure.class)) {
            Set<String> filters = subscription.getFilterMap().get(MonitoringRefStructure.class);
            if (!filters.isEmpty()) {
                for (String monitoringRef : filters) {

                    if (StringUtils.isEmpty(monitoringRef)) {
                        continue;
                    }
                    addSubcriptionToReverseList(subscription, monitoringRef);
                }
            }
        }
    }

    private void addSubcriptionToReverseList(OutboundSubscriptionSetup subscription, String monitoringRef) {
        if (outboundSubscriptionsByMonitoringRef.containsKey(monitoringRef)) {

            for (OutboundSubscriptionSetup currentOutboundSub : outboundSubscriptionsByMonitoringRef.get(monitoringRef)) {
                if (currentOutboundSub.getSubscriptionId().equals(currentOutboundSub.getSubscriptionId())) {
                    //subscription is already existing in reverseList. no need to add it
                    return;
                }
            }
            outboundSubscriptionsByMonitoringRef.get(monitoringRef).add(subscription);
        } else {
            List<OutboundSubscriptionSetup> outboundSubscriptions = new ArrayList<>();
            outboundSubscriptions.add(subscription);
            outboundSubscriptionsByMonitoringRef.put(monitoringRef, outboundSubscriptions);
        }
    }

    private OutboundSubscriptionSetup removeSubscription(String subscriptionId) {
        logger.info("Removing subscription {}", subscriptionId);
        failTrackerMap.delete(subscriptionId);
        heartbeatTimestampMap.remove(subscriptionId);
        return subscriptions.remove(subscriptionId);
    }

    private String findSubscriptionIdentifier(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {

            SituationExchangeSubscriptionStructure situationExchangeSubscriptionStructure = subscriptionRequest.
                    getSituationExchangeSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(situationExchangeSubscriptionStructure);

        } else if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {

            VehicleMonitoringSubscriptionStructure vehicleMonitoringSubscriptionStructure =
                    subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(vehicleMonitoringSubscriptionStructure);

        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {

            EstimatedTimetableSubscriptionStructure estimatedTimetableSubscriptionStructure =
                    subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(estimatedTimetableSubscriptionStructure);
        } else if (SiriHelper.containsValues(subscriptionRequest.getStopMonitoringSubscriptionRequests())) {

            StopMonitoringSubscriptionStructure stopMonitoringSubscriptionStructure =
                    subscriptionRequest.getStopMonitoringSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(stopMonitoringSubscriptionStructure);
        } else if (SiriHelper.containsValues(subscriptionRequest.getGeneralMessageSubscriptionRequests())) {

            GeneralMessageSubscriptionStructure generalMessageSubscriptionStructure = subscriptionRequest.getGeneralMessageSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(generalMessageSubscriptionStructure);
        } else if (SiriHelper.containsValues(subscriptionRequest.getFacilityMonitoringSubscriptionRequests())) {

            FacilityMonitoringSubscriptionStructure facilityMonitoringSubscriptionStructure = subscriptionRequest.getFacilityMonitoringSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(facilityMonitoringSubscriptionStructure);
        }
        return null;
    }

    private String getSubscriptionIdentifier(AbstractSubscriptionStructure subscriptionStructure) {
        if (subscriptionStructure != null && subscriptionStructure.getSubscriptionIdentifier() != null) {
            return subscriptionStructure.getSubscriptionIdentifier().getValue();
        }
        return null;
    }

    private ZonedDateTime findInitialTerminationTime(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {

            return subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0).getInitialTerminationTime();
        } else if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {

            return subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getInitialTerminationTime();
        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {

            return subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getInitialTerminationTime();
        } else if (SiriHelper.containsValues(subscriptionRequest.getStopMonitoringSubscriptionRequests())) {

            return subscriptionRequest.getStopMonitoringSubscriptionRequests().get(0).getInitialTerminationTime();
        } else if (SiriHelper.containsValues(subscriptionRequest.getGeneralMessageSubscriptionRequests())) {

            return subscriptionRequest.getGeneralMessageSubscriptionRequests().get(0).getInitialTerminationTime();
        } else if (SiriHelper.containsValues(subscriptionRequest.getFacilityMonitoringSubscriptionRequests())) {

            return subscriptionRequest.getFacilityMonitoringSubscriptionRequests().get(0).getInitialTerminationTime();
        }
        return null;
    }

    public void terminateSubscription(String subscriptionRef, boolean postResponse) {
        OutboundSubscriptionSetup subscriptionRequest = removeSubscription(subscriptionRef);

        if (subscriptionRequest != null) {
            if (postResponse) {
                Siri terminateSubscriptionResponse = siriObjectFactory.createTerminateSubscriptionResponse(subscriptionRef);
                logger.info("Sending TerminateSubscriptionResponse to {}", subscriptionRequest.getAddress());

                camelRouteManager.pushSiriData(terminateSubscriptionResponse, subscriptionRequest, true);
            } else {
                logger.info("Subscription terminated, but no response was sent");
            }
        } else {
            logger.trace("Got TerminateSubscriptionRequest for non-existing subscription");
        }
    }

    public List<String> terminateAllsubscriptionsForRequestor(String requestorRef, boolean postResponse) {
        logger.info("Terminating all subscriptions for requestor:" + requestorRef);
        List<String> terminatedSubscriptions = new ArrayList<>();
        for (OutboundSubscriptionSetup subscription : subscriptions.values()) {
            if (subscription.getRequestorRef().equals(requestorRef)) {
                terminatedSubscriptions.add(subscription.getSubscriptionId());
                terminateSubscription(subscription.getSubscriptionId(), postResponse);
            }
        }
        return terminatedSubscriptions;
    }


    public Siri handleCheckStatusRequest(CheckStatusRequestStructure checkStatusRequest) {
        return siriObjectFactory.createCheckStatusResponse(checkStatusRequest);
    }

    public int getPushUpdatesWaitingQueueSize() {
        return outboundSenderExecutorService == null ? 0 : outboundSenderExecutorService.getQueue().size();
    }


    public void pushUpdatesAsync(SiriDataType datatype, List updates, String datasetId) {
        final String breadcrumbId = MDC.get("camel.breadcrumbId");

        if (outboundSenderExecutorService == null) {
            outboundSenderExecutorService = (ThreadPoolExecutor) Executors.newFixedThreadPool(pushUpdatedThreadPool);
        }

        pushIteration++;
        if (pushIteration == 100) {
            //    logger.info("push updated thread pool queue size:" + outboundSenderExecutorService.getQueue().size() + ", pool size:" + pushUpdatedThreadPool);
            pushIteration = 0;
        }


        ExecutorService executorService = Executors.newSingleThreadExecutor();
        switch (datatype) {
            case ESTIMATED_TIMETABLE:
                executorService.submit(() -> pushUpdatedEstimatedTimetables(updates, datasetId, breadcrumbId));
                break;
            case SITUATION_EXCHANGE:
                executorService.submit(() -> pushUpdatedSituations(updates, datasetId, breadcrumbId));
                break;
            case VEHICLE_MONITORING:
                executorService.submit(() -> pushUpdatedVehicleActivities(updates, datasetId, breadcrumbId));
                break;
            case STOP_MONITORING:
                outboundSenderExecutorService.execute(() -> pushUpdatedStopMonitoring(updates, datasetId, breadcrumbId));
                break;
            case GENERAL_MESSAGE:
                executorService.submit(() -> pushUpdatedGeneralMessages(updates, datasetId, breadcrumbId));
                break;
            case FACILITY_MONITORING:
                executorService.submit(() -> pushUpdatedFacilityMonitoring(updates, datasetId, breadcrumbId));
                break;
            default:
                // Ignore
                break;
        }
    }

    private void pushUpdatedVehicleActivities(List<VehicleActivityStructure> addedOrUpdated, String datasetId, String breadcrumbId) {
        MDC.put("camel.breadcrumbId", breadcrumbId);

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        Siri delivery = siriObjectFactory.createVMServiceDelivery(addedOrUpdated);

        if (pushToTopicEnabled) {
            siriVmTopicProducer.asyncRequestBodyAndHeader(siriVmTopicProducer.getDefaultEndpoint(), delivery, CODESPACE_ID_KAFKA_HEADER_NAME, datasetId);
        }

        if (sendActivemqKafka) {
            Map<String, Object> headers = new HashMap<>();
            headers.put(DATASET_ID_HEADER_NAME, datasetId);
            sendVMToKafka.asyncRequestBodyAndHeaders(sendVMToKafka.getDefaultEndpoint(), delivery, headers);
        }

        final List<OutboundSubscriptionSetup> recipients = subscriptions
                .values()
                .stream()
                .filter(subscriptionRequest -> (
                                subscriptionRequest.getSubscriptionType().equals(SiriDataType.VEHICLE_MONITORING)
                                        && (
                                        subscriptionRequest.getDatasetId() == null || (
                                                subscriptionRequest
                                                        .getDatasetId()
                                                        .equals(datasetId)
                                        )
                                )
                        )

                )
                .collect(Collectors.toList());

        boolean logFullContents = true;
        for (OutboundSubscriptionSetup recipient : recipients) {
            camelRouteManager.pushSiriData(delivery, recipient, logFullContents);
            logFullContents = false;
        }

        MDC.remove("camel.breadcrumbId");
    }


    private void pushUpdatedSituations(
            List<PtSituationElement> addedOrUpdated, String datasetId, String breadcrumbId
    ) {
        MDC.put("camel.breadcrumbId", breadcrumbId);

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        Siri delivery = siriObjectFactory.createSXServiceDelivery(addedOrUpdated);

        if (pushToTopicEnabled) {
            siriSxTopicProducer.asyncRequestBodyAndHeader(siriSxTopicProducer.getDefaultEndpoint(), delivery, CODESPACE_ID_KAFKA_HEADER_NAME, datasetId);
        }

        if (pushToExternalSxConsumer) {
            delivery = fillStopNames(delivery, datasetId);
            sendSXToExternalConsumer.asyncRequestBodyAndHeader(sendSXToExternalConsumer.getDefaultEndpoint(), delivery, CODESPACE_ID_KAFKA_HEADER_NAME, datasetId);
        }

        if (sendActivemqKafka) {
            Map<String, Object> headers = new HashMap<>();
            headers.put(DATASET_ID_HEADER_NAME, datasetId);
            sendSXToKafka.asyncRequestBodyAndHeaders(sendSXToKafka.getDefaultEndpoint(), delivery, headers);
        }

        final List<OutboundSubscriptionSetup> recipients = subscriptions
                .values()
                .stream()
                .filter(subscriptionRequest -> (
                                subscriptionRequest.getSubscriptionType().equals(SiriDataType.SITUATION_EXCHANGE)
                                        && (
                                        subscriptionRequest.getDatasetId() == null || (
                                                subscriptionRequest
                                                        .getDatasetId()
                                                        .equals(datasetId)
                                        )
                                )
                        )

                )
                .collect(Collectors.toList());

        boolean logFullContents = true;
        for (OutboundSubscriptionSetup recipient : recipients) {
            OutboundIdMappingPolicy policy = recipient.isUseOriginalId() ? OutboundIdMappingPolicy.ORIGINAL_ID : OutboundIdMappingPolicy.DEFAULT;
            Siri modifiedIdDelivery = convertIds(delivery, datasetId, policy);
            camelRouteManager.pushSiriData(modifiedIdDelivery, recipient, logFullContents);
            logFullContents = false;
        }

        MDC.remove("camel.breadcrumbId");
    }

    private Siri fillStopNames(Siri delivery, String datasetId) {

        if (delivery.getServiceDelivery() == null || delivery.getServiceDelivery().getSituationExchangeDeliveries() == null || delivery.getServiceDelivery().getSituationExchangeDeliveries().isEmpty()) {
            return delivery;
        }

        for (SituationExchangeDeliveryStructure situationExchangeDelivery : delivery.getServiceDelivery().getSituationExchangeDeliveries()) {
            if (situationExchangeDelivery.getSituations() == null) {
                continue;
            }
            for (PtSituationElement ptSituationElement : situationExchangeDelivery.getSituations().getPtSituationElements()) {
                if (ptSituationElement.getAffects() == null || ptSituationElement.getAffects().getStopPoints() == null) {
                    continue;
                }
                for (AffectedStopPointStructure affectedStopPoint : ptSituationElement.getAffects().getStopPoints().getAffectedStopPoints()) {
                    if (affectedStopPoint.getStopPointRef() == null) {
                        continue;
                    }

                    String stopPointRef = affectedStopPoint.getStopPointRef().getValue();
                    String stopName = stopPlaceUpdaterService.getStopName(stopPointRef, datasetId);
                    logger.info(" fillStopNames - datasetId:" + datasetId + ", stopPointRef:" + stopPointRef + " , stopName:" + stopName);

                    if (StringUtils.isNotEmpty(stopName)) {
                        NaturalLanguageStringStructure stopNameLangStruct = new NaturalLanguageStringStructure();
                        stopNameLangStruct.setValue(stopName);
                        stopNameLangStruct.setLang("FR");
                        affectedStopPoint.getStopPointNames().add(stopNameLangStruct);
                    }
                }
            }
        }
        return delivery;
    }


    /**
     * Apply transformations to get ids in the requested format
     *
     * @param delivery delivery that contains siri data
     * @return Siri data with ids converted
     */
    private Siri convertIds(Siri delivery, String datasetId, OutboundIdMappingPolicy policy) {
        return SiriValueTransformer.transform(
                delivery,
                MappingAdapterPresets.getOutboundAdapters(SiriDataType.SITUATION_EXCHANGE, policy, incomingSubscriptionConfig.buildIdProcessingParamsFromDataset(datasetId)),
                false,
                false
        );
    }

    private void pushUpdatedGeneralMessages(
            List<GeneralMessage> addedOrUpdated, String datasetId, String breadcrumbId
    ) {
        MDC.put("camel.breadcrumbId", breadcrumbId);

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        Siri delivery = siriObjectFactory.createGMServiceDelivery(addedOrUpdated);

//        if (sendActivemqKafka) {
//            Map<String, Object> headers = new HashMap<>();
//            headers.put(DATASET_ID_HEADER_NAME, datasetId);
//            sendGMToKafka.asyncRequestBodyAndHeaders(sendGMToKafka.getDefaultEndpoint(), delivery, headers);
//        }


        final List<OutboundSubscriptionSetup> recipients = subscriptions
                .values()
                .stream()
                .filter(subscriptionRequest -> (
                                subscriptionRequest.getSubscriptionType().equals(SiriDataType.GENERAL_MESSAGE)
                                        && (
                                        subscriptionRequest.getDatasetId() == null || (
                                                subscriptionRequest
                                                        .getDatasetId()
                                                        .equals(datasetId)
                                        )
                                )
                        )

                )
                .collect(Collectors.toList());

        boolean logFullContents = true;
        for (OutboundSubscriptionSetup recipient : recipients) {
            camelRouteManager.pushSiriData(delivery, recipient, logFullContents);
            logFullContents = false;
        }

        MDC.remove("camel.breadcrumbId");
    }


    private void pushUpdatedFacilityMonitoring(List updates, String datasetId, String breadcrumbId) {
        MDC.put("camel.breadcrumbId", breadcrumbId);

        if (updates == null || updates.isEmpty()) {
            return;
        }
        Siri delivery = siriObjectFactory.createFMServiceDelivery(updates);


//        if (sendActivemqKafka) {
//            Map<String, Object> headers = new HashMap<>();
//            headers.put(DATASET_ID_HEADER_NAME, datasetId);
//            sendFMToKafka.asyncRequestBodyAndHeaders(sendFMToKafka.getDefaultEndpoint(), delivery, headers);
//        }


        final List<OutboundSubscriptionSetup> recipients = subscriptions
                .values()
                .stream()
                .filter(subscriptionRequest -> (
                                subscriptionRequest.getSubscriptionType().equals(SiriDataType.FACILITY_MONITORING)
                                        && (
                                        subscriptionRequest.getDatasetId() == null || (
                                                subscriptionRequest
                                                        .getDatasetId()
                                                        .equals(datasetId)
                                        )
                                )
                        )

                )
                .collect(Collectors.toList());

        boolean logFullContents = true;
        for (OutboundSubscriptionSetup recipient : recipients) {
            camelRouteManager.pushSiriData(delivery, recipient, logFullContents);
            logFullContents = false;
        }

        MDC.remove("camel.breadcrumbId");
    }

    private void pushUpdatedEstimatedTimetables(List<EstimatedVehicleJourney> addedOrUpdated, String datasetId, String breadcrumbId) {

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }

        MDC.put("camel.breadcrumbId", breadcrumbId);

        Siri delivery = siriObjectFactory.createETServiceDelivery(addedOrUpdated);

        if (pushToTopicEnabled) {
            siriEtTopicProducer.asyncRequestBodyAndHeader(siriEtTopicProducer.getDefaultEndpoint(), delivery, CODESPACE_ID_KAFKA_HEADER_NAME, datasetId);
        }

        if (sendActivemqKafka) {
            Map<String, Object> headers = new HashMap<>();
            headers.put(DATASET_ID_HEADER_NAME, datasetId);
            sendETToKafka.asyncRequestBodyAndHeaders(sendETToKafka.getDefaultEndpoint(), delivery, headers);
        }

        final List<OutboundSubscriptionSetup> recipients = subscriptions
                .values()
                .stream()
                .filter(subscriptionRequest -> (
                                subscriptionRequest.getSubscriptionType().equals(SiriDataType.ESTIMATED_TIMETABLE)
                                        && (
                                        subscriptionRequest.getDatasetId() == null || (
                                                subscriptionRequest
                                                        .getDatasetId()
                                                        .equals(datasetId)
                                        )
                                )
                        )

                )
                .collect(Collectors.toList());

        logger.debug("Pushing {} ET updates to {} outbound subscriptions", addedOrUpdated.size(), recipients.size());

        boolean logFullContents = true;
        for (OutboundSubscriptionSetup recipient : recipients) {
            camelRouteManager.pushSiriData(delivery, recipient, logFullContents);
            logFullContents = false;
        }
        MDC.remove("camel.breadcrumbId");
    }


    private <T extends AbstractItemStructure> void pushUpdatedStopMonitoring(List<T> addedOrUpdated, String datasetId, String breadcrumbId
    ) {
        MDC.put("camel.breadcrumbId", breadcrumbId);

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }

        Siri delivery = siriObjectFactory.createSMServiceDelivery(addedOrUpdated);


        if (pushToTopicEnabled) {
            siriSmTopicProducer.asyncRequestBodyAndHeader(siriSmTopicProducer.getDefaultEndpoint(), delivery, CODESPACE_ID_KAFKA_HEADER_NAME, datasetId);
        }

        if (sendActivemqKafka) {
            Map<String, Object> headers = new HashMap<>();
            headers.put(DATASET_ID_HEADER_NAME, datasetId);
            sendSMToKafka.asyncRequestBodyAndHeaders(sendSMToKafka.getDefaultEndpoint(), delivery, headers);
        }

        Set<String> monitoredRefs = SiriHelper.extractMonitoringRefs(addedOrUpdated);
        List<OutboundSubscriptionSetup> impactedOutboundSubscriptions = getSubscriptionsRelatedToMonitoringRefs(datasetId, monitoredRefs);

        impactedOutboundSubscriptions.forEach(subscription -> camelRouteManager.pushSiriData(delivery, subscription, true));
        MDC.remove("camel.breadcrumbId");
    }

    private List<OutboundSubscriptionSetup> getSubscriptionsRelatedToMonitoringRefs(String datasetId, Set<String> monitoredRefs) {

        List<OutboundSubscriptionSetup> results = new ArrayList<>();
        for (String monitoredRef : monitoredRefs) {
            if (outboundSubscriptionsByMonitoringRef.containsKey(monitoredRef)) {
                results.addAll(outboundSubscriptionsByMonitoringRef.get(monitoredRef));
            }
        }
        return results;
    }

    private boolean isSubscriptionImpactedByRefs(OutboundSubscriptionSetup subscription, Set<String> incomingRefs) {
        TimingTracer subsimpactedTT = new TimingTracer("subsimpactedTT");

        if (!subscription.getFilterMap().containsKey(MonitoringRefStructure.class)) {
            return false;
        }
        subsimpactedTT.mark("filterMap");

        Set<String> filters = subscription.getFilterMap().get(MonitoringRefStructure.class);

        subsimpactedTT.mark("filters");
        for (String subscriptionMonitoringRef : filters) {
            if (incomingRefs.contains(subscriptionMonitoringRef)) {
                // this subscription is looking for a stop that is present in incomingRefs

                subsimpactedTT.mark("finBoucle true");

                return true;
            }
        }
        subsimpactedTT.mark("finBoucle");
        // logger.info(subsimpactedTT.toString());
        return false;
    }

    public void pushFailedForSubscription(String subscriptionId) {
        OutboundSubscriptionSetup outboundSubscriptionSetup = subscriptions.get(subscriptionId);
        if (outboundSubscriptionSetup != null) {

            //Grace-period is set to minimum 5 minutes
            long gracePeriod = Math.max(3 * outboundSubscriptionSetup.getHeartbeatInterval(), 5 * 60 * 1000L);

            Instant firstFail = failTrackerMap.getOrDefault(subscriptionId, Instant.now());

            long terminationTime = firstFail.until(Instant.now(), MILLIS);
            if (terminationTime > gracePeriod) {
                logger.info("Cancelling outbound subscription {} that has failed for {}s.", subscriptionId, terminationTime / 1000);
                removeSubscription(subscriptionId);
            } else {
                logger.info("Outbound subscription {} has not responded for {}s, will be cancelled after {}s.", subscriptionId, terminationTime / 1000, gracePeriod / 1000);
                failTrackerMap.set(subscriptionId, firstFail);
            }
        }
    }

    public void clearFailTracker(String subscriptionId) {
        if (failTrackerMap.containsKey(subscriptionId)) {
            logger.info("Subscription {} is now responding - clearing failtracker", subscriptionId);
            failTrackerMap.delete(subscriptionId);
        }
    }
}
