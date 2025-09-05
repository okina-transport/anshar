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
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.util.TimingTracer;
import no.rutebanken.anshar.routes.kafka.KafkaConfig;
import no.rutebanken.anshar.routes.kafka.KafkaRouteBuilder;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.processor.GmSIVSicAQuayPostProcessor;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import no.rutebanken.anshar.util.GeneralMessageHelper;
import no.rutebanken.anshar.util.SiriUtils;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.MILLIS;
import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;


@SuppressWarnings("unchecked")
@Service
@Configuration
public class ServerSubscriptionManager {

    public static final String CODESPACE_ID_KAFKA_HEADER_NAME = "codespaceId";
    private static final Logger logger = LoggerFactory.getLogger(ServerSubscriptionManager.class);
    public static String DEFAULT_DATASET = "ALL";
    private final int pushIteration = 0;
    @Produce("direct:send.to.pubsub.topic.estimated_timetable")
    protected ProducerTemplate siriEtTopicProducer;
    @Produce("direct:send.to.pubsub.topic.vehicle_monitoring")
    protected ProducerTemplate siriVmTopicProducer;
    @Produce("direct:send.to.pubsub.topic.situation_exchange")
    protected ProducerTemplate siriSxTopicProducer;
    @Produce("direct:send.to.pubsub.topic.stop_monitoring")
    protected ProducerTemplate siriSmTopicProducer;
    @Produce("direct:send.sm.to.kafka")
    protected ProducerTemplate sendSMToKafka;
    @Produce(KafkaRouteBuilder.SEND_SX_TO_KAFKA)
    protected ProducerTemplate sendSXToKafka;
    @Produce("direct:send.sx.to.external.consumer")
    protected ProducerTemplate sendSXToExternalConsumer;
    @Produce("direct:send.vm.to.kafka")
    protected ProducerTemplate sendVMToKafka;
    @Produce("direct:send.et.to.kafka")
    protected ProducerTemplate sendETToKafka;
    @Produce("direct:send.gm.to.kafka")
    protected ProducerTemplate sendGMToKafka;
    @Produce("direct:send.fm.to.kafka")
    protected ProducerTemplate sendFMToKafka;
    @Produce
    protected ProducerTemplate initialDeliveryRequestProducer;
    @Autowired
    IMap<String, OutboundSubscriptionSetup> subscriptions;
    Map<String, List<OutboundSubscriptionSetup>> outboundSubscriptionsByMonitoringRef = new HashMap<>();
    ExecutorService outboundSenderExecutorService;
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
    @Value("${anshar.outbound.error.monitoringref}")
    private String errorMonitoringRefMissing = "Error";
    @Value("${anshar.outbound.error.initialtermination}")
    private String initialTerminationTimePassed = "Error";
    @Value("${anshar.outbound.pubsub.topic.enabled}")
    private boolean pushToTopicEnabled;
    @Value("${external.sx.consumer.enabled}")
    private boolean pushToExternalSxConsumer;
    @Autowired
    private CamelRouteManager camelRouteManager;
    @Autowired
    private SiriHelper siriHelper;
    @Autowired
    private KafkaConfig kafkaConfig;
    @Value("${outbound.change.before.update.cache.hours:5}")
    private int outboundChangeBeforeUpdateCacheTTL;
    @Autowired
    private SubscriptionConfig incomingSubscriptionConfig;
    @Autowired
    private StopPlaceUpdaterService stopPlaceUpdaterService;
    @Value("${anshar.push.updated.thread.pool:10}")
    private int pushUpdatedThreadPool;
    @Value("${anshar.outbound.subscription.grace.period:30000}")
    private long outboundSubscriptionGracePeriod = 30000;
    @Value("${anshar.initial.delivery.estimated.timetables.queue.name}")
    private String initialDeliveryETQueueName;
    @Value("${anshar.initial.delivery.stop.monitoring.queue.name}")
    private String initialDeliverySMQueueName;
    @Value("${anshar.initial.delivery.general.message.queue.name}")
    private String initialDeliveryGMQueueName;
    @Value("${anshar.initial.delivery.facility.monitoring.queue.name}")
    private String initialDeliveryFMQueueName;
    @Value("${anshar.initial.delivery.situation.exchange.queue.name}")
    private String initialDeliverySXQueueName;
    @Value("${anshar.initial.delivery.vehicle.monitoring.queue.name}")
    private String initialDeliveryVMQueueName;
    @Autowired
    private InitialDeliveryGenerator initialDeliveryGenerator;

    private static boolean checkMissingMonitoringRef(SubscriptionRequest subscriptionRequest) {
        boolean missingMonitoringRef = false;
        if (subscriptionRequest != null && CollectionUtils.isNotEmpty(subscriptionRequest.getStopMonitoringSubscriptionRequests())) {
            missingMonitoringRef = subscriptionRequest.getStopMonitoringSubscriptionRequests()
                    .stream()
                    .anyMatch(subscriptionItem -> subscriptionItem.getStopMonitoringRequest() == null
                            || subscriptionItem.getStopMonitoringRequest().getMonitoringRef() == null);
        }
        return missingMonitoringRef;
    }

    public Collection getSubscriptions() {
        return Collections.unmodifiableCollection(subscriptions.values());
    }

    public List<OutboundSubscriptionSetup> getAllSubscriptions(SiriDataType type) {

        if (type == null) {
            throw new IllegalArgumentException("Type must be specified to search in subscriptions");
        }

        return subscriptions.values().stream()
                .filter(subscription -> type.equals(subscription.getSubscriptionType()))
                .collect(Collectors.toList());
    }

    public JSONArray getSubscriptionsAsJson() {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

        JSONArray stats = new JSONArray();

        for (String key : subscriptions.keySet()) {

            OutboundSubscriptionSetup subscription = subscriptions.get(key);

            JSONObject obj = mapSubscriptionToJsonObject(key, subscription, formatter);

            stats.add(obj);
        }

        return stats;
    }

    /**
     * Récupère la liste des 'requestorRef' uniques, potentiellement filtrée par SiriDataType.
     *
     * @param siriDataType Le type de données SIRI pour filtrer les abonnements.
     *                     Si la valeur est null, aucun filtre n'est appliqué.
     * @return Une liste de string contenant les chaînes de caractères des requestorRef uniques.
     */
    public List<String> getSubscriptionRequestorRefs(SiriDataType siriDataType) {
        Stream<OutboundSubscriptionSetup> subscriptionStream = subscriptions.values().stream();

        if (siriDataType != null) {
            subscriptionStream = subscriptionStream.filter(
                    subscription -> siriDataType.equals(subscription.getSubscriptionType())
            );
        }

        return subscriptionStream
                .map(OutboundSubscriptionSetup::getRequestorRef)
                .distinct()
                .collect(Collectors.toList());
    }

    private JSONObject mapSubscriptionToJsonObject(String key, OutboundSubscriptionSetup subscription, DateTimeFormatter formatter) {
        JSONObject obj = new JSONObject();
        obj.put("subscriptionRef", key);
        obj.put("subscriptionType", subscription.getSubscriptionType().name());
        obj.put("address", subscription.getAddress());
        obj.put("heartbeatInterval", (subscription.getHeartbeatInterval() / 1000) + " s");
        obj.put("datasetId", String.join(",", subscription.getDatasetList()));
        obj.put("requestReceived", formatter.format(subscription.getRequestTimestamp()));
        obj.put("initialTerminationTime", formatter.format(subscription.getInitialTerminationTime()));
        obj.put("clientTrackingName", subscription.getClientTrackingName() != null ? subscription.getClientTrackingName() : "");
        obj.put("filteredRefs", getFilteredRefs(subscription));
        obj.put("requestorRef", subscription.getRequestorRef());
        return obj;
    }

    public JSONArray getSubscriptionsCountAsJson() {
        JSONArray count = new JSONArray();
        Map<SiriDataType, Integer> countSubscriptionByType = new EnumMap<>(SiriDataType.class);
        for (Map.Entry<String, OutboundSubscriptionSetup> entry : subscriptions.entrySet()) {
            OutboundSubscriptionSetup subscription = entry.getValue();
            countSubscriptionByType.merge(subscription.getSubscriptionType(), 1, Integer::sum);
        }
        for (Map.Entry<SiriDataType, Integer> counter : countSubscriptionByType.entrySet()) {
            JSONObject obj = new JSONObject();
            obj.put("siriDataType", counter.getKey().name());
            obj.put("count", counter.getValue());
            count.add(obj);
        }

        return count;
    }

    public JSONObject getSubscriptionsWithPagination(
            SiriDataType type,
            int page,
            int pageSize,
            String filtersJson
    ) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        JSONArray filteredSubscriptions = new JSONArray();

        Map<String, String> filters = new HashMap<>();
        if (filtersJson != null && !filtersJson.isEmpty()) {
            try {
                JSONObject filtersObj = (JSONObject) new JSONParser().parse(filtersJson);
                for (Object key : filtersObj.keySet()) {
                    filters.put((String) key, (String) filtersObj.get(key));
                }
            } catch (org.json.simple.parser.ParseException e) {
                throw new RuntimeException(e);
            }
        }

        for (Map.Entry<String, OutboundSubscriptionSetup> entry : subscriptions.entrySet()) {
            OutboundSubscriptionSetup subscription = entry.getValue();

            if (type != subscription.getSubscriptionType()) {
                continue;
            }

            JSONObject subscriptionJson = mapSubscriptionToJsonObject(entry.getKey(), subscription, formatter);

            boolean matchesAllFilters = true;
            for (Map.Entry<String, String> filter : filters.entrySet()) {
                String fieldValue = getStringSafe(subscriptionJson, filter.getKey());
                if (!fieldValue.toLowerCase().contains(filter.getValue().toLowerCase())) {
                    matchesAllFilters = false;
                    break;
                }
            }

            if (!matchesAllFilters) {
                continue;
            }

            filteredSubscriptions.add(subscriptionJson);
        }

        JSONArray paginatedResult = new JSONArray();
        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, filteredSubscriptions.size());

        for (int i = startIndex; i < endIndex; i++) {
            paginatedResult.add(filteredSubscriptions.get(i));
        }

        JSONObject result = new JSONObject();
        result.put("data", paginatedResult);
        result.put("count", filteredSubscriptions.size());
        return result;
    }

    private String getStringSafe(JSONObject json, String key) {
        Object value = json.get(key);
        return value != null ? value.toString() : "";
    }

    private String getFilteredRefs(OutboundSubscriptionSetup outboundSubscription) {
        StringBuilder filteredRefs = new StringBuilder();
        Map<Class, Set<String>> filterMap = outboundSubscription.getFilterMap();

        if (filterMap != null && filterMap.containsKey(MonitoringRefStructure.class)) {
            String stopRefs = String.join(",", filterMap.get(MonitoringRefStructure.class));
            filteredRefs.append("stops:").append(stopRefs);
        }

        if (MapUtils.isNotEmpty(outboundSubscription.getFilterMapByDataset())) {
            if (!filteredRefs.isEmpty()) {
                filteredRefs.append("/");
            }
            filteredRefs.append("linesByDataset:");
            boolean isFirstEntry = true;
            for (Map.Entry<String, Map<Class, Set<String>>> filterMapEntry : outboundSubscription.getFilterMapByDataset().entrySet()) {
                if (!isFirstEntry) {
                    filteredRefs.append("/");
                }
                filteredRefs.append(filterMapEntry.getKey()).append(":").append(String.join(",", filterMapEntry.getValue().get(LineRef.class)));
                isFirstEntry = false;
            }
        }

        if (filterMap != null && filterMap.containsKey(LineRef.class)) {

            if (!filteredRefs.isEmpty()) {
                filteredRefs.append("/");
            }

            String lineRefs = String.join(",", filterMap.get(LineRef.class));
            filteredRefs.append("lines:").append(lineRefs);
        }

        if (filterMap != null && filterMap.containsKey(SiteRef.class)) {

            if (!filteredRefs.isEmpty()) {
                filteredRefs.append("/");
            }

            String siteRefs = String.join(",", filterMap.get(SiteRef.class));
            filteredRefs.append("sites:").append(siteRefs);
        }

        return filteredRefs.toString();
    }

    /**
     * Handle subscription request that can contain one or multiple subcriptions
     *
     * @param incomingSiri           raw Siri
     * @param incomingSiriParameters incoming parameters
     * @return
     */
    public Siri handleMultipleSubscriptionsRequest(Siri incomingSiri, IncomingSiriParameters incomingSiriParameters) {
        SubscriptionRequest subscriptionRequest = incomingSiri.getSubscriptionRequest();
        if (subscriptionRequest.getStopMonitoringSubscriptionRequests() != null && subscriptionRequest.getStopMonitoringSubscriptionRequests().size() > 1) {
            return handleMultipleStopMonitoringRequest(incomingSiri, incomingSiriParameters);
        } else if (subscriptionRequest.getVehicleMonitoringSubscriptionRequests() != null && subscriptionRequest.getVehicleMonitoringSubscriptionRequests().size() > 1) {
            return handleMultipleVehicleMonitoringRequest(incomingSiri, incomingSiriParameters);
        } else if (subscriptionRequest.getFacilityMonitoringSubscriptionRequests() != null && subscriptionRequest.getFacilityMonitoringSubscriptionRequests().size() > 1) {
            return handleMultipleFacilityMonitoringRequest(incomingSiri, incomingSiriParameters);
        } else {
            return handleSingleSubscriptionRequest(incomingSiri, incomingSiriParameters);
        }
    }

    private Siri handleMultipleVehicleMonitoringRequest(Siri incomingSiri, IncomingSiriParameters incomingSiriParameters) {

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

            Siri currentResult = handleSingleSubscriptionRequest(singleSiriRequest, incomingSiriParameters);
            resultList.add(currentResult);
        }

        return aggregateResults(resultList);
    }

    private Siri handleMultipleStopMonitoringRequest(Siri incomingSiri, IncomingSiriParameters incomingSiriParameters) {

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

            Siri currentResult = handleSingleSubscriptionRequest(singleSiriRequest, incomingSiriParameters);
            resultList.add(currentResult);
        }

        return aggregateResults(resultList);
    }

    private Siri handleMultipleFacilityMonitoringRequest(Siri incomingSiri, IncomingSiriParameters incomingSiriParameters) {

        SubscriptionRequest subscriptionRequest = incomingSiri.getSubscriptionRequest();
        List<Siri> resultList = new ArrayList<>();
        RequestorRef requestorRef = subscriptionRequest.getRequestorRef();
        String consumerAddress = subscriptionRequest.getConsumerAddress();
        SubscriptionContextStructure subscriptionContext = subscriptionRequest.getSubscriptionContext();
        MessageQualifierStructure messageIdentifier = subscriptionRequest.getMessageIdentifier();

        for (FacilityMonitoringSubscriptionStructure fmss : subscriptionRequest.getFacilityMonitoringSubscriptionRequests()) {
            Siri singleSiriRequest = new Siri();
            SubscriptionRequest singleRequest = new SubscriptionRequest();
            singleRequest.getFacilityMonitoringSubscriptionRequests().add(fmss);
            singleRequest.setRequestorRef(requestorRef);
            singleRequest.setConsumerAddress(consumerAddress);
            singleRequest.setSubscriptionContext(subscriptionContext);
            singleRequest.setMessageIdentifier(messageIdentifier);
            singleSiriRequest.setSubscriptionRequest(singleRequest);
            singleSiriRequest.setVersion(incomingSiri.getVersion());

            Siri currentResult = handleSingleSubscriptionRequest(singleSiriRequest, incomingSiriParameters);
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
     * @param incomingSiri           raw Siri
     * @param incomingSiriParameters received parameters
     * @return
     */
    public Siri handleSingleSubscriptionRequest(Siri incomingSiri, IncomingSiriParameters incomingSiriParameters) {
        OutboundIdMappingPolicy outboundIdMappingPolicy = incomingSiriParameters.getOutboundIdMappingPolicy();
        boolean soapTransformation = incomingSiriParameters.isSoapTransformation();
        boolean missingMonitoringRef = checkMissingMonitoringRef(incomingSiri.getSubscriptionRequest());
        if (missingMonitoringRef) {
            String subscriptionId = findSubscriptionIdentifier(incomingSiri.getSubscriptionRequest());
            return siriObjectFactory.createSubscriptionResponse(StringUtils.defaultIfBlank(subscriptionId, "Undefined subscription id"), false, errorMonitoringRefMissing, incomingSiri.getVersion());
        }
        OutboundSubscriptionSetup subscription = createSubscription(incomingSiri, incomingSiriParameters);
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
        } else if (isSmSubWithStartTime(incomingSiri)) {
            hasError = true;
            errorText = "Usage of startTime and previewInterval at the same time is not allowed in StopMonitoring";
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

            requestInitialDeliverySending(subscription, outboundIdMappingPolicy);

            return subscriptionResponse;
        }
    }

    /**
     * Checks if a StopMonitoring subscriptin request is using previewInterval and startTime tags
     * (it is forbidden)
     *
     * @param incomingSiri siri that contains subscription request
     * @return true : the subscription has a previewInterval and startTime tag
     * false : the subscription is not using previewInterval and startTime tags at the same time
     */
    private boolean isSmSubWithStartTime(Siri incomingSiri) {

        if (incomingSiri.getSubscriptionRequest() == null || incomingSiri.getSubscriptionRequest().getStopMonitoringSubscriptionRequests() == null
                || incomingSiri.getSubscriptionRequest().getStopMonitoringSubscriptionRequests().isEmpty()) {
            // this is no an SM subscription request. no need to apply check
            return false;
        }

        for (StopMonitoringSubscriptionStructure stopMonitoringSubscriptionRequest : incomingSiri.getSubscriptionRequest().getStopMonitoringSubscriptionRequests()) {
            if (stopMonitoringSubscriptionRequest.getStopMonitoringRequest().getPreviewInterval() != null && stopMonitoringSubscriptionRequest.getStopMonitoringRequest().getStartTime() != null) {
                return true;
            }
        }
        return false;
    }


    public void generateAndSendInitialDelivery(String subscriptionId, String mappingPolicy) {

        OutboundSubscriptionSetup subscription = subscriptions.get(subscriptionId);
        if (subscription == null) {
            return;
        }
        OutboundIdMappingPolicy outboundIdMappingPolicy = OutboundIdMappingPolicy.valueOf(mappingPolicy);

        //Send initial ServiceDelivery
        logger.debug("Find initial delivery for {}", subscription.getSubscriptionId());

        Map<String, Siri> deliveriesByDataset = initialDeliveryGenerator.findInitialDeliveriesByDataset(subscription);
        for (Map.Entry<String, Siri> datasetAndDelivery : deliveriesByDataset.entrySet()) {
            sendInitialDeliveryToClient(datasetAndDelivery.getKey(), datasetAndDelivery.getValue(), subscription);
        }
    }

    private void requestInitialDeliverySending(OutboundSubscriptionSetup subscription, OutboundIdMappingPolicy outboundIdMappingPolicy) {
        Map<String, Object> headers = new HashMap<>();
        if (outboundIdMappingPolicy == null) {
            outboundIdMappingPolicy = OutboundIdMappingPolicy.DEFAULT;
        }
        headers.put("subscriptionId", subscription.getSubscriptionId());
        headers.put("outboundIdMappingPolicy", outboundIdMappingPolicy.toString());


        switch (subscription.getSubscriptionType()) {
            case STOP_MONITORING -> initialDeliveryRequestProducer.sendBodyAndHeaders(initialDeliverySMQueueName, null, headers);
            case VEHICLE_MONITORING -> initialDeliveryRequestProducer.sendBodyAndHeaders(initialDeliveryVMQueueName, null, headers);
            case SITUATION_EXCHANGE -> initialDeliveryRequestProducer.sendBodyAndHeaders(initialDeliverySXQueueName, null, headers);
            case ESTIMATED_TIMETABLE -> initialDeliveryRequestProducer.sendBodyAndHeaders(initialDeliveryETQueueName, null, headers);
            case GENERAL_MESSAGE -> initialDeliveryRequestProducer.sendBodyAndHeaders(initialDeliveryGMQueueName, null, headers);
            case FACILITY_MONITORING -> initialDeliveryRequestProducer.sendBodyAndHeaders(initialDeliveryFMQueueName, null, headers);
        }
    }

    private void sendInitialDeliveryToClient(String datasetId, Siri delivery, OutboundSubscriptionSetup subscription) {
        if (delivery != null) {
            if (SiriDataType.GENERAL_MESSAGE.equals(subscription.getSubscriptionType())) {
                delivery = convertIdsGeneralMessage(delivery, datasetId, subscription.getOutboundIdMappingPolicy());
            }
            logger.info("Sending initial delivery to {}, dataset:{}", subscription.getSubscriptionId(), datasetId);
            camelRouteManager.pushSiriData(datasetId, delivery, subscription, false);
        } else {
            logger.info("No initial delivery found for {}, dataset:{}", subscription, datasetId);
        }
    }


    public OutboundSubscriptionSetup createSubscription(Siri incomingSiri, IncomingSiriParameters incomingSiriParameters) {
        String datasetId = incomingSiriParameters.getDatasetId();
        OutboundIdMappingPolicy outboundIdMappingPolicy = incomingSiriParameters.getOutboundIdMappingPolicy();
        String clientTrackingName = incomingSiriParameters.getClientTrackingName();
        boolean useOrignalId = incomingSiriParameters.isUseOriginalId();
        SubscriptionRequest subscriptionRequest = incomingSiri.getSubscriptionRequest();
        List<ValueAdapter> mappers = new ArrayList<>();
        String version = getVersion(incomingSiri);
        Duration previewInterval = null;
        if (SiriUtils.hasSMRequest(subscriptionRequest)
                && subscriptionRequest.getStopMonitoringSubscriptionRequests().get(0).getStopMonitoringRequest().getPreviewInterval() != null) {
            previewInterval = subscriptionRequest.getStopMonitoringSubscriptionRequests().get(0).getStopMonitoringRequest().getPreviewInterval();
        } else {
            mappers = MappingAdapterPresets.getOutboundAdapters(outboundIdMappingPolicy);
        }

        Map<String, List<ValueAdapter>> valueAdaptersByDataset = getValueAdaptersByDataset(subscriptionRequest, outboundIdMappingPolicy, datasetId);
        Map<String, Map<Class, Set<String>>> filterMapByDataset = siriHelper.getFiltersByDataset(subscriptionRequest, outboundIdMappingPolicy, datasetId);

        if (incomingSiriParameters.isGmSIVSicAQuay()) {
            // value adapters are cached
            // create a new list, otherwise it will keep adding GmSIVSicAQuayPostProcessor to cached value adapters
            mappers = new ArrayList<>(mappers);
            mappers.add(new GmSIVSicAQuayPostProcessor());
        }

        OutboundSubscriptionSetup newOutboundSubscription = new OutboundSubscriptionSetup(
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
                SiriUtils.getVersionEnum(version),
                valueAdaptersByDataset,
                filterMapByDataset,
                outboundChangeBeforeUpdateCacheTTL,
                incomingSiriParameters.getCompressionFormat()
        );

        newOutboundSubscription.setOutboundIdMappingPolicy(outboundIdMappingPolicy);
        newOutboundSubscription.setPreviewInterval(previewInterval);

        return newOutboundSubscription;
    }


    private Map<String, List<ValueAdapter>> getValueAdaptersByDataset(SubscriptionRequest subscriptionRequest, OutboundIdMappingPolicy outboundIdMappingPolicy, String datasetId) {
        if (SiriUtils.hasETRequest(subscriptionRequest)) {
            return getValueAdaptersFromETRequest(subscriptionRequest, outboundIdMappingPolicy, datasetId);
        } else if (SiriUtils.hasVMRequest(subscriptionRequest)) {
            return getValueAdaptersFromVMRequest(subscriptionRequest, outboundIdMappingPolicy, datasetId);
        } else if (SiriUtils.hasSMRequest(subscriptionRequest)) {
            return getValueAdaptersFromSMRequest(subscriptionRequest, outboundIdMappingPolicy, datasetId);
        }

        return new HashMap<>();
    }

    private Map<String, List<ValueAdapter>> getValueAdaptersFromSMRequest(SubscriptionRequest subscriptionRequest, OutboundIdMappingPolicy outboundIdMappingPolicy, String datasetId) {
        Map<String, List<ValueAdapter>> results = new HashMap<>();
        Set<String> datasetList = SiriUtils.generateDatasetListFromHeader(datasetId);

        if (datasetList.isEmpty()) {
            datasetList = siriHelper.getDatasetsFromSubscription(subscriptionRequest.getStopMonitoringSubscriptionRequests().getFirst(), outboundIdMappingPolicy);
        }

        for (String dataset : datasetList) {
            Map<ObjectType, Optional<IdProcessingParameters>> idProcessingParams = siriHelper.getIdProcessingParamsFromDataset(dataset);
            results.put(dataset, MappingAdapterPresets.getOutboundAdapters(SiriDataType.STOP_MONITORING, outboundIdMappingPolicy, idProcessingParams));
        }

        return results;
    }

    private Map<String, List<ValueAdapter>> getValueAdaptersFromVMRequest(SubscriptionRequest subscriptionRequest, OutboundIdMappingPolicy outboundIdMappingPolicy, String datasetId) {
        Map<String, List<ValueAdapter>> results = new HashMap<>();
        if (StringUtils.isEmpty(datasetId)) {
            Map<ObjectType, Optional<IdProcessingParameters>> idProcessingParams = siriHelper.getIdProcessingParamsFromSubscription(subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0), outboundIdMappingPolicy, null);
            List<ValueAdapter> mappers = MappingAdapterPresets.getOutboundAdapters(SiriDataType.VEHICLE_MONITORING, outboundIdMappingPolicy, idProcessingParams);
            results.put(DEFAULT_DATASET, mappers);
        } else {

            Set<String> datasets = SiriUtils.generateDatasetListFromHeader(datasetId);

            for (String dataset : datasets) {
                Map<ObjectType, Optional<IdProcessingParameters>> idProcessingParams = siriHelper.getIdProcessingParamsFromSubscription(subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0), outboundIdMappingPolicy, dataset);
                List<ValueAdapter> mappers = MappingAdapterPresets.getOutboundAdapters(SiriDataType.VEHICLE_MONITORING, outboundIdMappingPolicy, idProcessingParams);
                results.put(dataset, mappers);
            }
        }
        return results;
    }

    private Map<String, List<ValueAdapter>> getValueAdaptersFromETRequest(SubscriptionRequest subscriptionRequest, OutboundIdMappingPolicy outboundIdMappingPolicy, String datasetId) {
        Map<String, List<ValueAdapter>> valueAdaptersByDataset = new HashMap<>();

        Set<String> datasetList = SiriUtils.generateDatasetListFromHeader(datasetId);
        if (datasetList.isEmpty()) {
            datasetList = getDatasetListFromETRequest(subscriptionRequest);
        }

        for (String dataset : datasetList) {
            Map<ObjectType, Optional<IdProcessingParameters>> idProcessingParams = incomingSubscriptionConfig.buildIdProcessingParamsFromDataset(dataset);
            List<ValueAdapter> mappers = MappingAdapterPresets.getOutboundAdapters(SiriDataType.ESTIMATED_TIMETABLE, outboundIdMappingPolicy, idProcessingParams);
            valueAdaptersByDataset.put(dataset, mappers);
        }
        return valueAdaptersByDataset;

    }

    private Set<String> getDatasetListFromETRequest(SubscriptionRequest subscriptionRequest) {
        Set<String> datasetList = new HashSet<>();
        List<EstimatedTimetableSubscriptionStructure> estimatedTimetableSubscriptionRequests = subscriptionRequest.getEstimatedTimetableSubscriptionRequests();
        for (EstimatedTimetableSubscriptionStructure estimatedTimetableSubscriptionRequest : estimatedTimetableSubscriptionRequests) {


            if (estimatedTimetableSubscriptionRequest.getEstimatedTimetableRequest().getLines() == null) {
                continue;
            }

            for (LineDirectionStructure lineDirection : estimatedTimetableSubscriptionRequest.getEstimatedTimetableRequest().getLines().getLineDirections()) {
                String rawLineRef = lineDirection.getLineRef().getValue();
                HashSet<String> searchedIds = new HashSet<>(Collections.singleton(rawLineRef));
                String datasetId = incomingSubscriptionConfig.findDatasetFromSearch(searchedIds, ObjectType.LINE).orElse(null);


                if (StringUtils.isEmpty(datasetId) || datasetList.contains(datasetId)) {
                    continue;
                }

                datasetList.add(datasetId);
            }
        }
        return datasetList;

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
            return getSeconds(subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getChangeBeforeUpdates());
        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            return getSeconds(subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getChangeBeforeUpdates());
        } else if (SiriHelper.containsValues(subscriptionRequest.getStopMonitoringSubscriptionRequests())) {
            return getSeconds(subscriptionRequest.getStopMonitoringSubscriptionRequests().get(0).getChangeBeforeUpdates());
        }
        return 0;
    }

    private long getUpdateInterval(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests()) && subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getUpdateInterval() != null) {
            return subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getUpdateInterval().getTimeInMillis(new Date(0));
        }
        return 0;
    }

    private int getSeconds(Duration changeBeforeUpdates) {
        if (changeBeforeUpdates != null) {
            return changeBeforeUpdates.getSeconds();
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
                if (StringUtils.isNotEmpty(subscription.getSubscriptionId()) && subscription.getSubscriptionId().equals(currentOutboundSub.getSubscriptionId())) {
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
        removeSubscriptionFromReverseMap(subscriptionId);
        return subscriptions.remove(subscriptionId);
    }

    private void removeSubscriptionFromReverseMap(String subscriptionId) {

        for (Map.Entry<String, List<OutboundSubscriptionSetup>> stringListEntry : outboundSubscriptionsByMonitoringRef.entrySet()) {
            List<OutboundSubscriptionSetup> filteredSubscriptionList = stringListEntry.getValue().stream()
                    .filter(outboundSubscriptionSetup -> !outboundSubscriptionSetup.getSubscriptionId().equals(subscriptionId))
                    .collect(Collectors.toList());
            stringListEntry.setValue(filteredSubscriptionList);
        }
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

                camelRouteManager.pushSiriData(null, terminateSubscriptionResponse, subscriptionRequest, true);
            } else {
                logger.info("Subscription terminated, but no response was sent");
            }
        } else {
            logger.trace("Got TerminateSubscriptionRequest for non-existing subscription");
        }
    }

    public void terminateAllsubscriptionsForTypeAndRequestor(SiriDataType siriDataType, String requestorRef, boolean postResponse) {
        logger.info("Terminating all subscriptions for requestor:" + requestorRef + " and type:" + siriDataType.toString());
        for (OutboundSubscriptionSetup subscription : subscriptions.values()) {
            if (subscription.getRequestorRef().equals(requestorRef) && subscription.getSubscriptionType().equals(siriDataType)) {

                terminateSubscription(subscription.getSubscriptionId(), postResponse);
            }
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

    public void terminateAllSubscriptionsByType(SiriDataType siriDataType, boolean postResponse) {
        logger.info("Terminating all subscriptions for siri type {}", siriDataType);
        for (OutboundSubscriptionSetup subscription : subscriptions.values()) {
            if (siriDataType.equals(subscription.getSubscriptionType())) {
                terminateSubscription(subscription.getSubscriptionId(), postResponse);
            }
        }
    }


    public Siri handleCheckStatusRequest(CheckStatusRequestStructure checkStatusRequest) {
        return siriObjectFactory.createCheckStatusResponse(checkStatusRequest);
    }


    public void pushUpdatesAsync(SiriDataType datatype, List updates, String datasetId) {
        final String breadcrumbId = MDC.get("camel.breadcrumbId");

        if (outboundSenderExecutorService == null) {
            outboundSenderExecutorService = Executors.newFixedThreadPool(pushUpdatedThreadPool);
        }

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        switch (datatype) {
            case ESTIMATED_TIMETABLE:
                outboundSenderExecutorService.execute(() -> pushUpdatedEstimatedTimetables(updates, datasetId, breadcrumbId));
                break;
            case SITUATION_EXCHANGE:
                outboundSenderExecutorService.execute(() -> pushUpdatedSituations(updates, datasetId, breadcrumbId));
                break;
            case VEHICLE_MONITORING:
                outboundSenderExecutorService.execute(() -> pushUpdatedVehicleActivities(updates, datasetId, breadcrumbId));
                break;
            case STOP_MONITORING:
                outboundSenderExecutorService.execute(() -> pushUpdatedStopMonitoring(updates, datasetId, breadcrumbId));
                break;
            case GENERAL_MESSAGE:
                outboundSenderExecutorService.execute(() -> pushUpdatedGeneralMessages(updates, datasetId, breadcrumbId));
                break;
            case FACILITY_MONITORING:
                outboundSenderExecutorService.execute(() -> pushUpdatedFacilityMonitoring(updates, datasetId, breadcrumbId));
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
        Siri delivery = siriObjectFactory.createVMServiceDelivery(addedOrUpdated, null, null);

        if (pushToTopicEnabled) {
            siriVmTopicProducer.asyncRequestBodyAndHeader(siriVmTopicProducer.getDefaultEndpoint(), delivery, CODESPACE_ID_KAFKA_HEADER_NAME, datasetId);
        }

        if (kafkaConfig.isSendSiriToKafka()) {
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
                                        subscriptionRequest.getDatasetList().isEmpty() || (
                                                subscriptionRequest
                                                        .getDatasetList()
                                                        .contains(datasetId)
                                        )
                                )
                        )

                )
                .collect(Collectors.toList());

        boolean logFullContents = true;
        for (OutboundSubscriptionSetup recipient : recipients) {
            if (!delivery.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty()) {
                delivery.getServiceDelivery().getVehicleMonitoringDeliveries().forEach(vmd -> vmd.setSubscriptionRef(SiriObjectFactory.createSubscriptionIdentifier(recipient.getSubscriptionId())));
            }
            camelRouteManager.pushSiriData(datasetId, delivery, recipient, logFullContents);
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
        Siri delivery = siriObjectFactory.createSXServiceDelivery(addedOrUpdated, null, null);

        if (pushToTopicEnabled) {
            siriSxTopicProducer.asyncRequestBodyAndHeader(siriSxTopicProducer.getDefaultEndpoint(), delivery, CODESPACE_ID_KAFKA_HEADER_NAME, datasetId);
        }

        if (pushToExternalSxConsumer) {
            delivery = fillStopNames(delivery, datasetId);
            sendSXToExternalConsumer.asyncRequestBodyAndHeader(sendSXToExternalConsumer.getDefaultEndpoint(), delivery, CODESPACE_ID_KAFKA_HEADER_NAME, datasetId);
        }

        if (kafkaConfig.isSendSiriToKafka()) {
            sendSXToKafka.asyncRequestBodyAndHeaders(sendSXToKafka.getDefaultEndpoint(), delivery, Map.of());
        }

        final List<OutboundSubscriptionSetup> recipients = subscriptions
                .values()
                .stream()
                .filter(subscriptionRequest -> (
                                subscriptionRequest.getSubscriptionType().equals(SiriDataType.SITUATION_EXCHANGE)
                                        && (
                                        subscriptionRequest.getDatasetList().isEmpty() || (
                                                subscriptionRequest
                                                        .getDatasetList()
                                                        .contains(datasetId)
                                        )
                                )
                        )

                )
                .collect(Collectors.toList());

        boolean logFullContents = true;
        for (OutboundSubscriptionSetup recipient : recipients) {
            if (!delivery.getServiceDelivery().getSituationExchangeDeliveries().isEmpty()) {
                delivery.getServiceDelivery().getSituationExchangeDeliveries().forEach(sed -> sed.setSubscriptionRef(SiriObjectFactory.createSubscriptionIdentifier(recipient.getSubscriptionId())));
            }
            Siri modifiedIdDelivery = convertIdsSituationExchange(delivery, datasetId, recipient.getOutboundIdMappingPolicy());
            camelRouteManager.pushSiriData(datasetId, modifiedIdDelivery, recipient, logFullContents);
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
    private Siri convertIdsSituationExchange(Siri delivery, String datasetId, OutboundIdMappingPolicy policy) {
        return SiriValueTransformer.transform(
                delivery,
                MappingAdapterPresets.getOutboundAdapters(SiriDataType.SITUATION_EXCHANGE, policy, incomingSubscriptionConfig.buildIdProcessingParamsFromDataset(datasetId)),
                true,
                false
        );
    }

    private Siri convertIdsGeneralMessage(Siri delivery, String datasetId, OutboundIdMappingPolicy policy) {
        return GeneralMessageHelper.applyTransformationsInContent(
                delivery,
                MappingAdapterPresets.getOutboundAdapters(SiriDataType.GENERAL_MESSAGE, policy, incomingSubscriptionConfig.buildIdProcessingParamsFromDataset(datasetId)),
                incomingSubscriptionConfig.buildIdProcessingParamsFromDataset(datasetId),
                true
        );
    }

    private void pushUpdatedGeneralMessages(List<GeneralMessage> addedOrUpdated, String datasetId, String breadcrumbId) {
        MDC.put("camel.breadcrumbId", breadcrumbId);

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        Siri delivery = siriObjectFactory.createGMServiceDelivery(addedOrUpdated, null, null);

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
                                        subscriptionRequest.getDatasetList().isEmpty() || (
                                                subscriptionRequest
                                                        .getDatasetList()
                                                        .contains(datasetId)
                                        )
                                )
                        )

                )
                .collect(Collectors.toList());

        boolean logFullContents = true;
        for (OutboundSubscriptionSetup recipient : recipients) {
            if (!delivery.getServiceDelivery().getGeneralMessageDeliveries().isEmpty()) {
                delivery.getServiceDelivery().getGeneralMessageDeliveries().forEach(gmd -> gmd.setSubscriptionRef(SiriObjectFactory.createSubscriptionIdentifier(recipient.getSubscriptionId())));
            }
            Siri modifiedIdDelivery = convertIdsGeneralMessage(delivery, datasetId, recipient.getOutboundIdMappingPolicy());
            camelRouteManager.pushSiriData(datasetId, modifiedIdDelivery, recipient, logFullContents);
            logFullContents = false;
        }

        MDC.remove("camel.breadcrumbId");
    }


    private void pushUpdatedFacilityMonitoring(List updates, String datasetId, String breadcrumbId) {
        MDC.put("camel.breadcrumbId", breadcrumbId);

        if (updates == null || updates.isEmpty()) {
            return;
        }
        Siri delivery = siriObjectFactory.createFMServiceDelivery(updates, null, null);


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
                                        subscriptionRequest.getDatasetList().isEmpty() || (
                                                subscriptionRequest
                                                        .getDatasetList()
                                                        .contains(datasetId)
                                        )
                                )
                        )

                )
                .collect(Collectors.toList());

        boolean logFullContents = true;
        for (OutboundSubscriptionSetup recipient : recipients) {
            if (!delivery.getServiceDelivery().getFacilityMonitoringDeliveries().isEmpty()) {
                delivery.getServiceDelivery().getFacilityMonitoringDeliveries().forEach(fmd -> fmd.setSubscriptionRef(SiriObjectFactory.createSubscriptionIdentifier(recipient.getSubscriptionId())));
            }
            camelRouteManager.pushSiriData(datasetId, delivery, recipient, logFullContents);
            logFullContents = false;
        }

        MDC.remove("camel.breadcrumbId");
    }

    private void pushUpdatedEstimatedTimetables(List<EstimatedVehicleJourney> addedOrUpdated, String datasetId, String breadcrumbId) {

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }

        MDC.put("camel.breadcrumbId", breadcrumbId);

        Siri delivery = siriObjectFactory.createETServiceDelivery(addedOrUpdated, null, null);

        if (pushToTopicEnabled) {
            siriEtTopicProducer.asyncRequestBodyAndHeader(siriEtTopicProducer.getDefaultEndpoint(), delivery, CODESPACE_ID_KAFKA_HEADER_NAME, datasetId);
        }

        if (kafkaConfig.isSendSiriToKafka()) {
            Map<String, Object> headers = new HashMap<>();
            headers.put(DATASET_ID_HEADER_NAME, datasetId);
            sendETToKafka.asyncRequestBodyAndHeaders(sendETToKafka.getDefaultEndpoint(), delivery, headers);
        }

        final List<OutboundSubscriptionSetup> recipients = subscriptions
                .values()
                .stream()
                .filter(subscription -> (
                                subscription.getSubscriptionType().equals(SiriDataType.ESTIMATED_TIMETABLE)
                                        && (
                                        subscription.getFilterMapByDataset().isEmpty() ||
                                                subscription.getFilterMapByDataset().containsKey("ALL") ||
                                                subscription.getFilterMapByDataset().containsKey(datasetId)
                                )
                        )
                )
                .collect(Collectors.toList());

        logger.debug("Pushing {} ET updates to {} outbound subscriptions", addedOrUpdated.size(), recipients.size());

        boolean logFullContents = true;
        for (OutboundSubscriptionSetup recipient : recipients) {
            if (!recipient.getSubscriptionType().equals(SiriDataType.ESTIMATED_TIMETABLE)) {
                continue;
            }
            if (!delivery.getServiceDelivery().getEstimatedTimetableDeliveries().isEmpty()) {
                delivery.getServiceDelivery().getEstimatedTimetableDeliveries().forEach(etd -> etd.setSubscriptionRef(SiriObjectFactory.createSubscriptionIdentifier(recipient.getSubscriptionId())));
            }
            camelRouteManager.pushSiriData(datasetId, delivery, recipient, logFullContents);
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

        Siri delivery = siriObjectFactory.createSMServiceDelivery(addedOrUpdated, null, null);


        if (pushToTopicEnabled) {
            siriSmTopicProducer.asyncRequestBodyAndHeader(siriSmTopicProducer.getDefaultEndpoint(), delivery, CODESPACE_ID_KAFKA_HEADER_NAME, datasetId);
        }

//        if (sendActivemqKafka) {
//            Map<String, Object> headers = new HashMap<>();
//            headers.put(DATASET_ID_HEADER_NAME, datasetId);
//            sendSMToKafka.asyncRequestBodyAndHeaders(sendSMToKafka.getDefaultEndpoint(), delivery, headers);
//        }

        Set<String> monitoredRefs = SiriHelper.extractMonitoringRefs(addedOrUpdated);
        List<OutboundSubscriptionSetup> impactedOutboundSubscriptions = getSubscriptionsRelatedToMonitoringRefs(datasetId, monitoredRefs);

        impactedOutboundSubscriptions.forEach(subscription -> {
            if (!delivery.getServiceDelivery().getStopMonitoringDeliveries().isEmpty()) {
                delivery.getServiceDelivery().getStopMonitoringDeliveries().forEach(smd -> smd.setSubscriptionRef(SiriObjectFactory.createSubscriptionIdentifier(subscription.getSubscriptionId())));
            }
            camelRouteManager.pushSiriData(datasetId, delivery, subscription, true);
        });
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
            long gracePeriod = outboundSubscriptionGracePeriod;

            Instant firstFail = failTrackerMap.getOrDefault(subscriptionId, Instant.now());

            long terminationTime = firstFail.until(Instant.now(), MILLIS);
            if (terminationTime > gracePeriod) {
                logger.info("Cancelling outbound subscription {} that has failed for {}s.", subscriptionId, terminationTime / 1000);
                removeSubscription(subscriptionId);
            } else {
                logger.info("Outbound subscription {} has not responded for {}s, will be cancelled after {}s.", subscriptionId, terminationTime / 1000, gracePeriod / 1000);
                // Adding a TTL to fail tracker to handle empty period with no data after an error
                // (only subcriptions that repeatedly fail for 30 minutes will be removed)
                failTrackerMap.set(subscriptionId, firstFail, 10, TimeUnit.MINUTES);
            }
        }
    }

    public void clearAllOutboundSubscriptions() {
        logger.warn("||||    CLEARING ALL OUTBOUND SUBCRIPTIONS |||");
        subscriptions.clear();
        outboundSubscriptionsByMonitoringRef.clear();
    }

    public void clearFailTracker(String subscriptionId) {
        if (failTrackerMap.containsKey(subscriptionId)) {
            logger.info("Subscription {} is now responding - clearing failtracker", subscriptionId);
            failTrackerMap.delete(subscriptionId);
        }
    }

    public int getOutboundSubscriptionCount() {
        return subscriptions.size();
    }
}
