package no.rutebanken.anshar.routes.siri.handlers.inbound;

import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.util.StopMonitoringUtils;
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

@Service
public class StopMonitoringInbound {

    private static final Logger logger = LoggerFactory.getLogger(StopMonitoringInbound.class);

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

    public boolean ingestStopVisitFromApi(SiriDataType dataFormat, String dataSetId, Siri incoming, List<SubscriptionSetup> subscriptionSetupList) {
        // logger.debug("Got SM-delivery: Subscription [{}] {}", subscriptionSetupList);
        List<StopMonitoringDeliveryStructure> stopMonitoringDeliveries = incoming.getServiceDelivery().getStopMonitoringDeliveries();
        List<MonitoredStopVisit> addedOrUpdated = new ArrayList<>();
        if (stopMonitoringDeliveries != null) {
            stopMonitoringDeliveries.forEach(sm -> {
                        if (sm != null) {
                            if (sm.isStatus() != null && !sm.isStatus() || sm.getErrorCondition() != null) {
                                logger.info(utils.getErrorContents(sm.getErrorCondition()));
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

        serverSubscriptionManager.pushUpdatesAsync(dataFormat, addedOrUpdated, dataSetId);

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
        recordDeltaTimes(datasetId, incomingMonitoredStopVisits);
        Collection<MonitoredStopVisit> result = monitoredStopVisits.addAll(datasetId, incomingMonitoredStopVisits);
        if (result.size() > 0) {
            serverSubscriptionManager.pushUpdatesAsync(SiriDataType.STOP_MONITORING, incomingMonitoredStopVisits, datasetId);
        }
        return result;
    }

    public void cancelStopVisits(String datasetId, List<MonitoredStopVisitCancellation> incomingMonitoredStopVisitsCancellations) {
        monitoredStopVisits.cancelStopVsits(datasetId, incomingMonitoredStopVisitsCancellations);
        serverSubscriptionManager.pushUpdatesAsync(SiriDataType.STOP_MONITORING, incomingMonitoredStopVisitsCancellations, datasetId);
    }

    public boolean ingestStopVisit(SubscriptionSetup subscriptionSetup, Siri incoming) {
        List<StopMonitoringDeliveryStructure> stopMonitoringDeliveries = incoming.getServiceDelivery().getStopMonitoringDeliveries();
        //logger.debug("Got SM-delivery: Subscription [{}] ", subscriptionSetup);

        List<MonitoredStopVisit> addedOrUpdated = new ArrayList<>();
        if (stopMonitoringDeliveries != null) {
            stopMonitoringDeliveries.forEach(sm -> {
                        if (sm != null) {
                            if (sm.isStatus() != null && !sm.isStatus() || sm.getErrorCondition() != null) {
                                logger.info(utils.getErrorContents(sm.getErrorCondition()));
                            } else {
                                if (sm.getMonitoredStopVisits() != null && !sm.getMonitoredStopVisits().isEmpty()) {
                                    addedOrUpdated.addAll(ingestStopVisits(subscriptionSetup.getDatasetId(), sm.getMonitoredStopVisits()));
                                }
                                if (sm.getMonitoredStopVisitCancellations() != null && !sm.getMonitoredStopVisitCancellations().isEmpty()) {
                                    cancelStopVisits(subscriptionSetup.getDatasetId(), sm.getMonitoredStopVisitCancellations());
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
            String stopName = StopMonitoringUtils.getMonitoringRef(monitoredStopVisit).orElse(null);
            String lineName = StopMonitoringUtils.getLineName(monitoredStopVisit).orElse(null);
            String vehicleJourneyName = StopMonitoringUtils.getVehicleJourneyName(monitoredStopVisit).orElse(null);
            String aimedTime = StopMonitoringUtils.getAimedTimeAtStop(monitoredStopVisit).orElse(null);
            Objects.requireNonNull(stopName);
            Objects.requireNonNull(lineName);
            Objects.requireNonNull(vehicleJourneyName);
            Objects.requireNonNull(aimedTime);
            newItemIdentifier = stopName + lineName + vehicleJourneyName + aimedTime;
        } catch (Exception e) {
            logger.error("Unable to compute new itemIdentifier from {}", monitoredStopVisit.getItemIdentifier());
        }
        return StringUtils.defaultIfEmpty(newItemIdentifier, existingItemIdentifier);
    }
}
