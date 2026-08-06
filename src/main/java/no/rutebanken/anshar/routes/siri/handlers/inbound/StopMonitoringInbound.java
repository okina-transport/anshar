package no.rutebanken.anshar.routes.siri.handlers.inbound;

import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.kafka.KafkaConfig;
import no.rutebanken.anshar.routes.kafka.KafkaRouteBuilder;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.util.StopMonitoringUtils;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.MonitoredStopVisit;
import uk.org.siri.siri21.MonitoredStopVisitCancellation;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopMonitoringDeliveryStructure;

import java.util.*;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;

@Service
public class StopMonitoringInbound {

    private static final Logger logger = LoggerFactory.getLogger(StopMonitoringInbound.class);
    @Produce(KafkaRouteBuilder.SEND_SM_IN_TO_KAFKA)
    protected ProducerTemplate sendSmInToKafka;
    @Autowired
    private MonitoredStopVisits monitoredStopVisits;
    @Autowired
    private Utils utils;
    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;
    @Autowired
    private SubscriptionManager subscriptionManager;
    @Autowired
    private PrometheusMetricsService metrics;
    @Autowired
    private KafkaConfig kafkaConfig;

    public boolean ingestStopVisitFromApi(SiriDataType dataFormat, String dataSetId, Siri incoming, List<SubscriptionSetup> subscriptionSetupList, Long inboundTime) {
        // logger.debug("Got SM-delivery: Subscription [{}] {}", subscriptionSetupList);
        List<StopMonitoringDeliveryStructure> stopMonitoringDeliveries = incoming.getServiceDelivery().getStopMonitoringDeliveries();
        List<MonitoredStopVisit> addedOrUpdated = new ArrayList<>();
        if (stopMonitoringDeliveries != null) {
            stopMonitoringDeliveries.forEach(sm -> {
                        if (sm != null) {
                            if (sm.isStatus() != null && !sm.isStatus() || utils.hasErrorData(sm.getErrorCondition())) {
                                String errorContents = utils.getErrorContents(sm.getErrorCondition());
                                if (StringUtils.isEmpty(errorContents) || errorContents.length() < 5) {
                                    try {
                                        logger.info("unable to find error content : " + CustomSiriXml.toXml(incoming));
                                    } catch (JAXBException e) {
                                        logger.error("Error while trying to parse xml", e);
                                    }
                                } else {
                                    logger.info(errorContents);
                                }
                            } else {
                                if (sm.getMonitoredStopVisits() != null) {
                                    updateStopMonitoringItemIdentifier(sm.getMonitoredStopVisits());
                                    addedOrUpdated.addAll(
                                            monitoredStopVisits.addAll(dataSetId, sm.getMonitoredStopVisits()));
                                }
                            }
                        }
                    }
            );
        }

        serverSubscriptionManager.pushUpdatesAsync(dataFormat, addedOrUpdated, dataSetId, inboundTime);

        for (SubscriptionSetup subscriptionSetup : subscriptionSetupList) {
            List<MonitoredStopVisit> addedOrUpdatedBySubscription = addedOrUpdated
                    .stream()
                    .filter(monitoredStopVisit -> subscriptionSetup.getStopMonitoringRefValues().contains(monitoredStopVisit.getMonitoringRef().getValue()))
                    .collect(Collectors.toList());
            subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdatedBySubscription.size());
//                        logger.info("Active SM-elements: {}, current delivery: {}, {}", monitoredStopVisits.getSize(), addedOrUpdatedBySubscription.size(), subscriptionSetup);
        }

        return !addedOrUpdated.isEmpty();
    }


    public Collection<MonitoredStopVisit> ingestStopVisits(String datasetId, List<MonitoredStopVisit> incomingMonitoredStopVisits) {
        return ingestStopVisits(datasetId, incomingMonitoredStopVisits, null);
    }

    public Collection<MonitoredStopVisit> ingestStopVisits(String datasetId, List<MonitoredStopVisit> incomingMonitoredStopVisits, Long inboundTime) {
        recordDeltaTimes(datasetId, incomingMonitoredStopVisits);
        Collection<MonitoredStopVisit> result = monitoredStopVisits.addAll(datasetId, incomingMonitoredStopVisits);
        if (CollectionUtils.isNotEmpty(result)) {
            serverSubscriptionManager.pushUpdatesAsync(SiriDataType.STOP_MONITORING, new ArrayList<>(result), datasetId, inboundTime);
        }
        return result;
    }

    public void cancelStopVisits(String datasetId, List<MonitoredStopVisitCancellation> incomingMonitoredStopVisitsCancellations) {
        cancelStopVisits(datasetId, incomingMonitoredStopVisitsCancellations, null);
    }

    public void cancelStopVisits(String datasetId, List<MonitoredStopVisitCancellation> incomingMonitoredStopVisitsCancellations, Long inboundTime) {
        monitoredStopVisits.cancelStopVsits(datasetId, incomingMonitoredStopVisitsCancellations);
        serverSubscriptionManager.pushUpdatesAsync(SiriDataType.STOP_MONITORING, incomingMonitoredStopVisitsCancellations, datasetId, inboundTime);
    }

    public boolean ingestStopVisit(SubscriptionSetup subscriptionSetup, Siri incoming, Long inboundTime) {
        List<StopMonitoringDeliveryStructure> stopMonitoringDeliveries = incoming.getServiceDelivery().getStopMonitoringDeliveries();
        //logger.debug("Got SM-delivery: Subscription [{}] ", subscriptionSetup);

        if (kafkaConfig.isKafkaEnabled() && kafkaConfig.isSendSiriSmInToKafka()) {
            sendSmInToKafka.asyncRequestBodyAndHeader(sendSmInToKafka.getDefaultEndpoint(), incoming,
                    DATASET_ID_HEADER_NAME, subscriptionSetup.getDatasetId());
        }

        List<MonitoredStopVisit> addedOrUpdated = new ArrayList<>();
        if (stopMonitoringDeliveries != null) {
            stopMonitoringDeliveries.forEach(sm -> {
                        if (sm != null) {
                            if (sm.isStatus() != null && !sm.isStatus() || utils.hasErrorData(sm.getErrorCondition())) {
                                String errorContents = utils.getErrorContents(sm.getErrorCondition());
                                if (StringUtils.isEmpty(errorContents) || errorContents.length() < 5) {
                                    try {
                                        logger.info("unable to find error content : " + CustomSiriXml.toXml(incoming));
                                    } catch (JAXBException e) {
                                        logger.error("Error while trying to parse xml", e);
                                    }
                                } else {
                                    logger.info(errorContents);
                                }
                            } else {
                                if (sm.getMonitoredStopVisits() != null && !sm.getMonitoredStopVisits().isEmpty()) {
                                    addedOrUpdated.addAll(ingestStopVisits(subscriptionSetup.getDatasetId(), sm.getMonitoredStopVisits(), inboundTime));
                                }
                                if (sm.getMonitoredStopVisitCancellations() != null && !sm.getMonitoredStopVisitCancellations().isEmpty()) {
                                    cancelStopVisits(subscriptionSetup.getDatasetId(), sm.getMonitoredStopVisitCancellations(), inboundTime);
                                }
                            }
                        }
                    }
            );
        }

        subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());

        // logger.debug("Active SM-elements: {}, current delivery: {}, {}", monitoredStopVisits.getSize(), addedOrUpdated.size(), subscriptionSetup);

        return !addedOrUpdated.isEmpty();
    }

    private void recordDeltaTimes(String datasetId, List<MonitoredStopVisit> smList) {
        Set<Long> deltaTimes = new HashSet<>();

        for (MonitoredStopVisit monitoredStopVisit : smList) {

            if (monitoredStopVisit.getRecordedAtTime() == null) {
                continue;
            }
            deltaTimes.add(System.currentTimeMillis() - monitoredStopVisit.getRecordedAtTime().toInstant().toEpochMilli());
        }
        metrics.recordDeltaTimes(SiriDataType.STOP_MONITORING, datasetId, deltaTimes);
    }

    protected void updateStopMonitoringItemIdentifier(List<MonitoredStopVisit> monitoredStopVisits) {
        if (CollectionUtils.isNotEmpty(monitoredStopVisits)) {
            for (MonitoredStopVisit monitoredStopVisit : monitoredStopVisits) {
                if (monitoredStopVisit != null) {
                    String newItemIdentifier = computeStopMonitoringItemIdentifier(monitoredStopVisit);
                    monitoredStopVisit.setItemIdentifier(newItemIdentifier);
                }
            }
        }
    }

    private String computeStopMonitoringItemIdentifier(MonitoredStopVisit monitoredStopVisit) {
        String existingItemIdentifier = monitoredStopVisit.getItemIdentifier();
        String newItemIdentifier = "";
        try {
            String stopRef = StopMonitoringUtils.getMonitoringRef(monitoredStopVisit).orElse(null);
            String lineRef = StopMonitoringUtils.getLineRef(monitoredStopVisit).orElse(null);
            String vehicleJourneyRef = StopMonitoringUtils.getVehicleJourneyRef(monitoredStopVisit).orElse(null);
            String aimedTime = StopMonitoringUtils.getAimedTimeAtStop(monitoredStopVisit).orElse(null);
            Objects.requireNonNull(stopRef);
            Objects.requireNonNull(lineRef);
            Objects.requireNonNull(vehicleJourneyRef);
            Objects.requireNonNull(aimedTime);
            newItemIdentifier = stopRef + lineRef + vehicleJourneyRef + aimedTime;
        } catch (Exception e) {
            logger.error("Unable to compute new itemIdentifier from {}", monitoredStopVisit.getItemIdentifier());
        }
        return StringUtils.defaultIfEmpty(newItemIdentifier, existingItemIdentifier);
    }
}
