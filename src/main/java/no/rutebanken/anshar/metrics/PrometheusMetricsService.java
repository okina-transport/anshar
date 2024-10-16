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

package no.rutebanken.anshar.metrics;

import com.google.common.collect.Sets;
import com.hazelcast.replicatedmap.ReplicatedMap;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.routes.outbound.CamelRouteManager;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.MappingNames;
import no.rutebanken.anshar.routes.validation.ValidationType;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.*;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PrometheusMetricsService extends PrometheusMeterRegistry {

    private static final Logger logger = LoggerFactory.getLogger(PrometheusMetricsService.class);

    private static final String DATATYPE_TAG_NAME = "dataType";

    private static final String REQUESTOR_REF_TAG_NAME = "requestorRef";
    private static final String AGENCY_TAG_NAME = "agency";
    private static final String MAPPING_ID_TAG = "mappingId";
    private static final String MAPPING_NAME_TAG = "mappingName";

    private static final String KAFKA_STATUS_TAG = "kafkaStatus";
    private static final String KAFKA_TOPIC_NAME = "kafkaTopic";

    private static final String CODESPACE_TAG_NAME = "codespace";
    private static final String VALIDATION_TYPE_TAG_NAME = "validationType";
    private static final String VALIDATION_RULE_TAG_NAME = "category";
    private static final String SCHEMA_VALID_TAG_NAME = "schema";
    private static final String PROFILE_VALID_TAG_NAME = "profile";

    @Autowired
    protected SubscriptionManager manager;

    @Autowired
    private CamelRouteManager camelRouteManager;

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    private static final String METRICS_PREFIX = "app.anshar.";
    private static final String DATA_COUNTER_NAME = METRICS_PREFIX + "data.counter";
    private static final String DATA_TOTAL_COUNTER_NAME = METRICS_PREFIX + "data.total";

    private static final String DATA_EXTERNAL_SOURCE_TOTAL_COUNTER_NAME = METRICS_PREFIX + "data.external.source.total";
    private static final String DATA_SUCCESS_COUNTER_NAME = METRICS_PREFIX + "data.success";
    private static final String DATA_EXPIRED_COUNTER_NAME = METRICS_PREFIX + "data.expired";
    private static final String DATA_IGNORED_COUNTER_NAME = METRICS_PREFIX + "data.ignored";
    private static final String DATA_OUTBOUND_COUNTER_NAME = METRICS_PREFIX + "data.outbound";

    private static final String DATA_MAPPING_COUNTER_NAME = METRICS_PREFIX + "data.mapping";

    private static final String KAFKA_COUNTER_NAME = METRICS_PREFIX + "data.kafka";

    private static final String DATA_VALIDATION_COUNTER = METRICS_PREFIX + "data.validation";
    private static final String DATA_VALIDATION_RESULT_COUNTER = METRICS_PREFIX + "data.validation.result";

    private static final String EMPTY_RECORDED_AT_TIME = METRICS_PREFIX + "data.empty.recoreded.at.time";
    private static final String NEW_RECORDED_AFTER_OLD = METRICS_PREFIX + "data.new.recorded.before.old.recorded";

    private static final String NEGATIVE_EXPIRATATION = METRICS_PREFIX + "data.negative.expiration";


    private static final String SUBS_PUSH_WAITING_THREADS = METRICS_PREFIX + "subscription.push.waiting.threads";
    private static final String SUBS_PUSH_ACTIVE_THREADS = METRICS_PREFIX + "subscription.push.active.threads";
    private static final String PUSH_UPDATES_WAITING_THREADS = METRICS_PREFIX + "push.updates.waiting.threads";


    private static final String OUTBOUND_PUSH_TIME = METRICS_PREFIX + "data.outbound.push.time";

    private static final String DELTA_RECORDED_AT_TIME = METRICS_PREFIX + "data.delta.recorded.at.time";


    public PrometheusMetricsService() {
        super(PrometheusConfig.DEFAULT);
    }

    final Map<String, Integer> nbOfOutboundPushByRequestor = new HashMap<>();
    final Map<String, Long> totalPushTimeByRequestor = new HashMap<>();
    final Map<String, Set<Long>> smDeltaTimesTmp = new ConcurrentHashMap<>();
    final Map<String, Double> smDeltaTimesResults = new HashMap<>();
    final Map<String, Double> outboundPushTimeResults = new HashMap<>();


    final Map<String, Set<Long>> smDeltaTimesTmpBeforePush = new ConcurrentHashMap<>();
    final Map<String, Double> smDeltaTimesResultsBeforePush = new HashMap<>();

    @PreDestroy
    public void shutdown() {
        this.close();
    }


    /**
     * Record delta times between recordedAtTime field and real time
     *
     * @param dataType   Siri type
     * @param datasetId  id of the dataset
     * @param deltaTimes set of delta times
     */
    public void recordDeltaTimes(SiriDataType dataType, String datasetId, Set<Long> deltaTimes) {
        if (SiriDataType.STOP_MONITORING.equals(dataType)) {

            if (smDeltaTimesTmp.containsKey(datasetId)) {
                smDeltaTimesTmp.get(datasetId).addAll(deltaTimes);
            } else {
                Set<Long> mySet = Sets.newConcurrentHashSet();
                mySet.addAll(deltaTimes);
                smDeltaTimesTmp.put(datasetId, mySet);
            }
        }
    }

    public void recordDeltaTimesBeforePush(SiriDataType dataType, String datasetId, Set<Long> deltaTimes) {
        if (SiriDataType.STOP_MONITORING.equals(dataType)) {

            if (smDeltaTimesTmpBeforePush.containsKey(datasetId)) {
                smDeltaTimesTmpBeforePush.get(datasetId).addAll(deltaTimes);
            } else {
                Set<Long> mySet = Sets.newConcurrentHashSet();
                mySet.addAll(deltaTimes);
                smDeltaTimesTmpBeforePush.put(datasetId, mySet);
            }
        }
    }

    public void recordPushTime(String requestorRef, long pushTime) {

        if (nbOfOutboundPushByRequestor.containsKey(requestorRef)) {
            Integer currentNbofPush = nbOfOutboundPushByRequestor.get(requestorRef);
            nbOfOutboundPushByRequestor.put(requestorRef, currentNbofPush + 1);
        } else {
            nbOfOutboundPushByRequestor.put(requestorRef, 1);
        }
        if (totalPushTimeByRequestor.containsKey(requestorRef)) {
            Long totalPushTime = totalPushTimeByRequestor.get(requestorRef);
            totalPushTimeByRequestor.put(requestorRef, totalPushTime + pushTime);
        } else {
            totalPushTimeByRequestor.put(requestorRef, pushTime);
        }
    }

    public void registerNegativeExpiration(SiriDataType dataType, String datasetId) {

        if (StringUtils.isEmpty(datasetId)) {
            datasetId = "emptyDatasetId";
        }
        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, dataType.name()));
        counterTags.add(new ImmutableTag(AGENCY_TAG_NAME, datasetId));
        counter(NEGATIVE_EXPIRATATION, counterTags).increment(1);
    }

    public void registerNewRecordedAfterOld(SiriDataType dataType) {
        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, dataType.name()));
        counter(NEW_RECORDED_AFTER_OLD, counterTags).increment(1);
    }

    public void registerEmptyRecordedAtTime(SiriDataType dataType) {
        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, dataType.name()));
        counter(EMPTY_RECORDED_AT_TIME, counterTags).increment(1);
    }

    public void registerIncomingData(SiriDataType dataType, String agencyId, long total, long updated, long expired, long ignored) {

        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, dataType.name()));
        counterTags.add(new ImmutableTag(AGENCY_TAG_NAME, agencyId));

        counter(DATA_TOTAL_COUNTER_NAME, counterTags).increment(total);
        counter(DATA_SUCCESS_COUNTER_NAME, counterTags).increment(updated);
        counter(DATA_EXPIRED_COUNTER_NAME, counterTags).increment(expired);
        counter(DATA_IGNORED_COUNTER_NAME, counterTags).increment(ignored);
    }

    public void registerIncomingDataFromExternalSource(SiriDataType dataType, String agencyId, long total) {
        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, dataType.name()));
        counterTags.add(new ImmutableTag(AGENCY_TAG_NAME, agencyId));

        counter(DATA_EXTERNAL_SOURCE_TOTAL_COUNTER_NAME, counterTags).increment(total);
    }


    public void registerDataMapping(SiriDataType dataType, String agencyId, MappingNames mappingName, int mappedCount) {

        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, dataType.name()));
        counterTags.add(new ImmutableTag(AGENCY_TAG_NAME, agencyId));
        counterTags.add(new ImmutableTag(MAPPING_NAME_TAG, mappingName.toString()));
        counterTags.add(new ImmutableTag(MAPPING_ID_TAG, mappingName.name()));

        counter(DATA_MAPPING_COUNTER_NAME, counterTags).increment(mappedCount);
    }

    public enum KafkaStatus {SENT, ACKED, FAILED}

    public void registerAckedKafkaRecord(String topic) {
        registerKafkaRecord(topic, KafkaStatus.ACKED);
    }

    public void registerKafkaRecord(String topic, KafkaStatus status) {
        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(KAFKA_TOPIC_NAME, topic));
        counterTags.add(new ImmutableTag(KAFKA_STATUS_TAG, status.name()));

        counter(KAFKA_COUNTER_NAME, counterTags).increment();
    }

    public void countOutgoingData(Siri siri, SubscriptionSetup.SubscriptionMode mode) {
        countOutgoingData(siri, null, mode);
    }

    public void countOutgoingData(Siri siri, String requestorRef, SubscriptionSetup.SubscriptionMode mode) {
        SiriDataType dataType = null;
        int count = 0;
        if (siri != null && siri.getServiceDelivery() != null) {
            if (siri.getServiceDelivery().getEstimatedTimetableDeliveries() != null &&
                    !siri.getServiceDelivery().getEstimatedTimetableDeliveries().isEmpty()) {
                EstimatedTimetableDeliveryStructure timetableDeliveryStructure = siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0);
                if (timetableDeliveryStructure != null && timetableDeliveryStructure.getEstimatedJourneyVersionFrames() != null &&
                        !timetableDeliveryStructure.getEstimatedJourneyVersionFrames().isEmpty()) {
                    EstimatedVersionFrameStructure estimatedVersionFrameStructure = timetableDeliveryStructure.getEstimatedJourneyVersionFrames().get(0);
                    if (estimatedVersionFrameStructure != null && estimatedVersionFrameStructure.getEstimatedVehicleJourneies() != null) {

                        dataType = SiriDataType.ESTIMATED_TIMETABLE;
                        count = estimatedVersionFrameStructure.getEstimatedVehicleJourneies().size();
                    }
                }
            } else if (siri.getServiceDelivery().getVehicleMonitoringDeliveries() != null &&
                    !siri.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty()) {
                VehicleMonitoringDeliveryStructure deliveryStructure = siri.getServiceDelivery().getVehicleMonitoringDeliveries().get(0);
                if (deliveryStructure != null) {
                    dataType = SiriDataType.VEHICLE_MONITORING;
                    count = deliveryStructure.getVehicleActivities().size();
                }
            } else if (siri.getServiceDelivery().getSituationExchangeDeliveries() != null &&
                    !siri.getServiceDelivery().getSituationExchangeDeliveries().isEmpty()) {
                SituationExchangeDeliveryStructure deliveryStructure = siri.getServiceDelivery().getSituationExchangeDeliveries().get(0);
                if (deliveryStructure != null && deliveryStructure.getSituations() != null) {
                    dataType = SiriDataType.SITUATION_EXCHANGE;
                    count = deliveryStructure.getSituations().getPtSituationElements().size();
                }
            } else if (siri.getServiceDelivery().getStopMonitoringDeliveries() != null &&
                    !siri.getServiceDelivery().getStopMonitoringDeliveries().isEmpty()) {
                StopMonitoringDeliveryStructure deliveryStructure = siri.getServiceDelivery().getStopMonitoringDeliveries().get(0);
                if (deliveryStructure != null) {
                    dataType = SiriDataType.STOP_MONITORING;
                    count = deliveryStructure.getMonitoredStopVisits().size();
                }
            }
            logger.debug("countOutgoingData - count:" + count + ", reqRef:" + requestorRef + ", dataType:" + dataType + ", mode:" + mode);
            countOutgoingData(requestorRef, dataType, mode, count);
        }

    }

    public void addValidationMetrics(
            SiriDataType dataType, String codespaceId, ValidationType validationType, String message, Integer count
    ) {
        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, dataType.name()));
        counterTags.add(new ImmutableTag(CODESPACE_TAG_NAME, codespaceId));
        counterTags.add(new ImmutableTag(VALIDATION_TYPE_TAG_NAME, validationType.name()));
        counterTags.add(new ImmutableTag(VALIDATION_RULE_TAG_NAME, message));

        counter(DATA_VALIDATION_COUNTER, counterTags).increment(count);
    }

    public void addValidationResult(
            SiriDataType dataType, String codespaceId, boolean schemaValid, boolean profileValid
    ) {
        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, dataType.name()));
        counterTags.add(new ImmutableTag(CODESPACE_TAG_NAME, codespaceId));
        counterTags.add(new ImmutableTag(SCHEMA_VALID_TAG_NAME, "" + schemaValid));
        counterTags.add(new ImmutableTag(PROFILE_VALID_TAG_NAME, "" + profileValid));

        counter(DATA_VALIDATION_RESULT_COUNTER, counterTags).increment();
    }

    private void countOutgoingData(String requestorRef, SiriDataType dataType, SubscriptionSetup.SubscriptionMode mode, long objectCount) {
        if (dataType != null && objectCount > 0) {
            List<Tag> counterTags = new ArrayList<>();
            counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, dataType.name()));
            counterTags.add(new ImmutableTag("mode", mode.name()));

            if (StringUtils.isEmpty(requestorRef)) {
                requestorRef = "emptyRequestorRef";
            }
            counterTags.add(new ImmutableTag(REQUESTOR_REF_TAG_NAME, requestorRef));


            counter(DATA_OUTBOUND_COUNTER_NAME, counterTags).increment(objectCount);
        }
    }

    final Map<String, Integer> gaugeValues = new HashMap<>();

    public void gaugeDataset(SiriDataType subscriptionType, String agencyId, Integer count) {

        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, subscriptionType.name()));
        counterTags.add(new ImmutableTag(AGENCY_TAG_NAME, agencyId));

        String key = "" + subscriptionType + agencyId;
        gaugeValues.put(key, count);

        gauge(DATA_COUNTER_NAME, counterTags, key, value -> gaugeValues.get(key));
    }

    @Override
    public String scrape() {
        update();
        return super.scrape();
    }


    private void updateDeltaTimes() {
        for (Map.Entry<String, Set<Long>> smDeltaTimeEntry : smDeltaTimesTmp.entrySet()) {
            String requestorRef = smDeltaTimeEntry.getKey();
            List<Tag> counterTags = new ArrayList<>();
            counterTags.add(new ImmutableTag(REQUESTOR_REF_TAG_NAME, requestorRef));


            Set<Long> deltaTimes = new HashSet<>(smDeltaTimeEntry.getValue());
            double sum = 0;
            for (Long deltaTime : deltaTimes) {
                sum = sum + deltaTime;
            }


            smDeltaTimesResults.put(requestorRef, sum / deltaTimes.size());
            gauge(DELTA_RECORDED_AT_TIME, counterTags, requestorRef, value -> smDeltaTimesResults.get(requestorRef));

        }
        smDeltaTimesTmp.clear();
    }

    public void update() {

        gauge(SUBS_PUSH_WAITING_THREADS, "pushWaitingThreads", value -> camelRouteManager.getPushSubscriptionWaitingQueueSize());
        gauge(SUBS_PUSH_ACTIVE_THREADS, "pushActiveThreads", value -> camelRouteManager.getPushSubscriptionActiveCount());
        gauge(PUSH_UPDATES_WAITING_THREADS, "pushUpdatesWaitingThreads", value -> serverSubscriptionManager.getPushUpdatesWaitingQueueSize());

        if (!smDeltaTimesTmp.isEmpty()) {
            updateDeltaTimes();
        }

        if (!smDeltaTimesTmpBeforePush.isEmpty()) {
            updateDeltaTimesBeforePush();
        }

        if (!nbOfOutboundPushByRequestor.isEmpty()) {

            for (Map.Entry<String, Integer> nbOfOutboundPushEntry : nbOfOutboundPushByRequestor.entrySet()) {
                String requestorRef = nbOfOutboundPushEntry.getKey();
                List<Tag> counterTags = new ArrayList<>();
                counterTags.add(new ImmutableTag(REQUESTOR_REF_TAG_NAME, requestorRef));

                Long totalTime = totalPushTimeByRequestor.get(requestorRef);
                Integer nbOfPush = nbOfOutboundPushEntry.getValue();
                outboundPushTimeResults.put(requestorRef, (double) totalTime / nbOfPush);
                gauge(OUTBOUND_PUSH_TIME, counterTags, requestorRef, value -> outboundPushTimeResults.get(requestorRef));
            }
            totalPushTimeByRequestor.clear();
            nbOfOutboundPushByRequestor.clear();
        }

        for (Meter meter : getMeters()) {
            if (DATA_COUNTER_NAME.equals(meter.getId().getName())) {
                this.remove(meter);
            }
        }

        EstimatedTimetables estimatedTimetables = ApplicationContextHolder.getContext().getBean(EstimatedTimetables.class);
        Map<String, Integer> datasetSize = estimatedTimetables.getLocalDatasetSize();
        for (Map.Entry<String, Integer> entry : datasetSize.entrySet()) {
            gaugeDataset(SiriDataType.ESTIMATED_TIMETABLE, entry.getKey(), entry.getValue());
        }

        Situations situations = ApplicationContextHolder.getContext().getBean(Situations.class);
        datasetSize = situations.getLocalDatasetSize();
        for (Map.Entry<String, Integer> entry : datasetSize.entrySet()) {
            gaugeDataset(SiriDataType.SITUATION_EXCHANGE, entry.getKey(), entry.getValue());
        }

        VehicleActivities vehicleActivities = ApplicationContextHolder.getContext().getBean(VehicleActivities.class);
        datasetSize = vehicleActivities.getLocalDatasetSize();
        for (Map.Entry<String, Integer> entry : datasetSize.entrySet()) {
            gaugeDataset(SiriDataType.VEHICLE_MONITORING, entry.getKey(), entry.getValue());
        }

        // TODO MHI : add SM to prometheus

        ReplicatedMap<String, SubscriptionSetup> subscriptions = manager.subscriptions;
        for (SubscriptionSetup subscription : subscriptions.values()) {

            SiriDataType subscriptionType = subscription.getSubscriptionType();

            String gauge_baseName = METRICS_PREFIX + "subscription";

            String gauge_failing = gauge_baseName + ".failing";
            String gauge_data_failing = gauge_baseName + ".data_failing";


            List<Tag> counterTags = new ArrayList<>();
            counterTags.add(new ImmutableTag(DATATYPE_TAG_NAME, subscriptionType.name()));
            counterTags.add(new ImmutableTag(AGENCY_TAG_NAME, subscription.getDatasetId()));
            counterTags.add(new ImmutableTag("vendor", subscription.getVendor()));


            //Flag as failing when ACTIVE, and NOT HEALTHY
            gauge(gauge_failing, getTagsWithTimeLimit(counterTags, "now"), subscription.getSubscriptionId(), value ->
                    (manager.isActiveSubscription(subscription.getSubscriptionId()) &&
                            !manager.isSubscriptionHealthy(subscription.getSubscriptionId())) ? 1 : 0);

            //Set flag as data failing when ACTIVE, and NOT receiving data

            gauge(gauge_data_failing, getTagsWithTimeLimit(counterTags, "5min"), subscription.getSubscriptionId(), value ->
                    isSubscriptionFailing(manager, subscription, 5 * 60));

            gauge(gauge_data_failing, getTagsWithTimeLimit(counterTags, "15min"), subscription.getSubscriptionId(), value ->
                    isSubscriptionFailing(manager, subscription, 15 * 60));

            gauge(gauge_data_failing, getTagsWithTimeLimit(counterTags, "30min"), subscription.getSubscriptionId(), value ->
                    isSubscriptionFailing(manager, subscription, 30 * 60));
        }
    }

    private void updateDeltaTimesBeforePush() {

        for (Map.Entry<String, Set<Long>> smDeltaTimeEntry : smDeltaTimesTmpBeforePush.entrySet()) {
            String requestorRef = smDeltaTimeEntry.getKey();
            List<Tag> counterTags = new ArrayList<>();
            counterTags.add(new ImmutableTag(REQUESTOR_REF_TAG_NAME, requestorRef));


            Set<Long> deltaTimes = new HashSet<>(smDeltaTimeEntry.getValue());
            double sum = 0;
            for (Long deltaTime : deltaTimes) {
                sum = sum + deltaTime;
            }


            smDeltaTimesResultsBeforePush.put(requestorRef, sum / deltaTimes.size());
            gauge(DELTA_RECORDED_AT_TIME + "_BEFORE_PUSH", counterTags, requestorRef, value -> smDeltaTimesResultsBeforePush.get(requestorRef));

        }
        smDeltaTimesTmp.clear();


    }

    private List<Tag> getTagsWithTimeLimit(List<Tag> counterTags, String timeLimit) {
        List<Tag> counterTagsClone = new ArrayList<>(counterTags);
        counterTagsClone.add(new ImmutableTag("timelimit", timeLimit));
        return counterTagsClone;
    }

    private double isSubscriptionFailing(SubscriptionManager manager, SubscriptionSetup subscription, int allowedSeconds) {
        if (manager.isActiveSubscription(subscription.getSubscriptionId()) &&
                !manager.isSubscriptionReceivingData(subscription.getSubscriptionId(), allowedSeconds)) {
            return 1;
        }
        return 0;
    }
}
