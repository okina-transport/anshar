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

import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.handlers.outbound.SituationExchangeOutbound;
import no.rutebanken.anshar.routes.siri.processor.GmSIVSicAQuayPostProcessor;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.util.SiriUtils;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.entur.siri.validator.SiriValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.*;

import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static no.rutebanken.anshar.routes.HttpParameter.INTERNAL_SIRI_DATA_TYPE;
import static no.rutebanken.anshar.routes.HttpParameter.SIRI_VERSION_HEADER_NAME;
import static no.rutebanken.anshar.routes.siri.Siri20RequestHandlerRoute.TRANSFORM_SOAP;
import static no.rutebanken.anshar.routes.siri.transformer.SiriOutputTransformerRoute.OUTPUT_ADAPTERS_HEADER_NAME;
import static no.rutebanken.anshar.routes.validation.validators.Constants.*;
import static org.springframework.http.HttpHeaders.CONTENT_ENCODING;

@Service
public class CamelRouteManager {
    private static final Logger logger = LoggerFactory.getLogger(CamelRouteManager.class);
    @Produce("direct:send.to.external.subscription")
    protected ProducerTemplate siriSubscriptionProcessor;
    @Autowired
    ServerSubscriptionManager subscriptionManager;
    @Autowired
    ScheduledOutboundSubscriptionConfig scheduledOutboundSubscriptionConfig;
    Map<String, ExecutorService> threadFactoryMap = new HashMap<>();
    @Autowired
    private SiriHelper siriHelper;
    @Value("${anshar.default.max.elements.per.delivery:1000}")
    private int maximumSizePerDelivery;
    @Autowired
    private VehicleActivities vehicleActivities;
    @Autowired
    private SituationExchangeOutbound situationExchangeOutbound;
    @Autowired
    private PrometheusMetricsService prometheusMetricsService;
    private ExecutorService executors;

    @Autowired
    InitialDeliveryGenerator initialDeliveryGenerator;

    /**
     * Splits SIRI-data if applicable, and pushes data to external subscription
     *
     * @param payload
     * @param subscriptionRequest
     */
    public void pushSiriData(String datasetId, Siri payload, OutboundSubscriptionSetup subscriptionRequest, boolean logBody, Long inboundTime) {

        String consumerAddress = subscriptionRequest.getAddress();
        if (consumerAddress == null) {
            logger.info("ConsumerAddress is null - ignoring data.");
            return;
        }
        final String breadcrumbId = MDC.get("camel.breadcrumbId");
        ExecutorService executorService = getOrCreateExecutorService(subscriptionRequest);

        executorService.submit(() -> {
            try {

                long startTime = System.currentTimeMillis();
                MDC.put("camel.breadcrumbId", breadcrumbId);
                if (!subscriptionManager.subscriptions.containsKey(subscriptionRequest.getSubscriptionId())) {
                    logger.debug("Subscription has been terminated - ignoring data.");
                    // Short circuit if subscription has been terminated while waiting
                    return;
                }
                Map<Class, Set<String>> filterMap;
                if (subscriptionRequest.getFilterMapByDataset() != null && !subscriptionRequest.getFilterMapByDataset().isEmpty()) {
                    filterMap = getfilterMap(subscriptionRequest, datasetId);
                } else {
                    filterMap = subscriptionRequest.getFilterMap();
                }

                Siri filteredPayload = SiriHelper.filterSiriPayload(payload, filterMap);

                int deliverySize = this.maximumSizePerDelivery;
                if (subscriptionRequest.getDatasetList() != null && !subscriptionRequest.getDatasetList().isEmpty()) {
                    deliverySize = Integer.MAX_VALUE;
                }

                List<Siri> splitSiri = siriHelper.splitDeliveries(filteredPayload, deliverySize, subscriptionRequest.getSubscriptionId());

                if (splitSiri.size() > 1) {
                    logger.info("Object split into {} deliveries for subscription {}.", splitSiri.size(), subscriptionRequest);
                }

                // On ne notifie pas les abonnés si (temps estimé (TR) - temps attendu (TH)) < changeBeforeUpdates de l'abonnement
                if (subscriptionRequest.getChangeBeforeUpdates() > 0) {
                    removeVehicleMonitoringIfChangeBeforeUpdates(splitSiri, subscriptionRequest);
                    removeStopMonitoringIfChangeBeforeUpdates(splitSiri, subscriptionRequest);
                }


                // On remplace les données partielles reçues par l'intégralité de la donnée si incrementalUpdate de l'abonnement est à false
                if (!subscriptionRequest.getIncrementalUpdates()) {
                    handleFullUpdateDelivery(subscriptionRequest, inboundTime);
                    return;
                }

                for (Siri siri : splitSiri) {
                    siri = SiriUtils.setSubscriberRef(siri, subscriptionRequest.getRequestorRef());
                    if (subscriptionRequest.getSubscriptionType().equals(SiriDataType.STOP_MONITORING)) {
                        if (subscriptionRequest.getPreviewInterval() != null) {
                            siri = SiriUtils.filterStopMonitoringOnPreviewInterval(siri, subscriptionRequest);
                        } else {
                            siri = SiriUtils.removeTheoreticalSM(siri);
                        }
                        if (!hasStopMonitoringData(siri)) {
                            continue;
                        }
                    }

                    // On crée des déclencheurs pour ne notifier les abonnés que tous les x moments définis par l'updateInterval
                    if (subscriptionRequest.getUpdateInterval() > 0) {
                        scheduledOutboundSubscriptionConfig.createScheduledOutboundSubscription(siri, subscriptionRequest);
                    } else {
                        postDataToSubscription(datasetId, siri, subscriptionRequest, logBody, inboundTime);
                    }
                }

                // logger.info(pushSiriTT.toString());
                prometheusMetricsService.recordPushTime(subscriptionRequest.getRequestorRef(), System.currentTimeMillis() - startTime);

            } catch (Exception e) {
                logger.info("Failed to push data for subscription {}: {}", subscriptionRequest, e);

                if (e.getCause() instanceof SocketException) {
                    logger.info("Recipient is unreachable - ignoring");
                } else {
                    String msg = e.getMessage();
                    if (e.getCause() != null) {
                        msg = e.getCause().getMessage();
                    }
                    logger.info("Exception caught when pushing SIRI-data: {}", msg);
                }
                subscriptionManager.pushFailedForSubscription(subscriptionRequest.getSubscriptionId());

                removeDeadSubscriptionExecutors(subscriptionManager);
            } finally {
                MDC.remove("camel.breadcrumbId");
            }
        });
    }


    /**
     * The current payload must be replaced by the whole data contained in cache because user does nor request for "incrementalUpdate"
     *
     * @param subscriptionRequest parameters of the outbound subscriptions
     */
    private void handleFullUpdateDelivery(OutboundSubscriptionSetup subscriptionRequest, Long inboundTime) {
        Map<String, Siri> completeDelivery = initialDeliveryGenerator.findInitialDeliveriesByDataset(subscriptionRequest);

        for (Map.Entry<String, Siri> deliveryWithDataset : completeDelivery.entrySet()) {
            postDataToSubscription(deliveryWithDataset.getKey(), deliveryWithDataset.getValue(), subscriptionRequest, false, inboundTime);
        }
    }

    private Map<Class, Set<String>> getfilterMap(OutboundSubscriptionSetup subscriptionRequest, String datasetId) {
        Map<Class, Set<String>> results = new HashMap<>();

        if (subscriptionRequest.getFilterMapByDataset().containsKey(datasetId)) {
            results.putAll(subscriptionRequest.getFilterMapByDataset().get(datasetId));
        }

        if (subscriptionRequest.getFilterMapByDataset().containsKey(ServerSubscriptionManager.DEFAULT_DATASET)) {
            for (Map.Entry<Class, Set<String>> entry : subscriptionRequest.getFilterMapByDataset().get(ServerSubscriptionManager.DEFAULT_DATASET).entrySet()) {
                if (results.containsKey(entry.getKey())) {
                    results.get(entry.getKey()).addAll(entry.getValue());
                } else {
                    results.put(entry.getKey(), entry.getValue());
                }
            }
        }


        return results;

    }

    /**
     * Check if a siri object contains stopMonitoringVisits
     *
     * @param siri siri to check
     * @return true : the siri object contains stopMonitoringVisits
     * false : the siri object does not contain stopMonitoringVisits
     */
    private boolean hasStopMonitoringData(Siri siri) {
        if (siri == null || siri.getServiceDelivery() == null || siri.getServiceDelivery().getStopMonitoringDeliveries() == null || siri.getServiceDelivery().getStopMonitoringDeliveries().size() == 0) {
            return false;
        }

        return siri.getServiceDelivery().getStopMonitoringDeliveries().stream()
                .anyMatch(delivery ->
                        (delivery.getMonitoredStopVisits() != null && delivery.getMonitoredStopVisits().size() > 0) ||
                                (delivery.getMonitoredStopVisitCancellations() != null && delivery.getMonitoredStopVisitCancellations().size() > 0)
                );
    }

    private void removeStopMonitoringIfChangeBeforeUpdates(List<Siri> splitSiri, OutboundSubscriptionSetup outboundSubscription) {

        for (Siri siri : splitSiri) {
            if (siri.getServiceDelivery().getStopMonitoringDeliveries() == null) {
                continue;
            }

            List<StopMonitoringDeliveryStructure> smDeliveries = siri.getServiceDelivery().getStopMonitoringDeliveries();
            for (StopMonitoringDeliveryStructure smDelivery : smDeliveries) {
                List<MonitoredStopVisit> filteredStopVisits = new ArrayList<>();

                for (MonitoredStopVisit monitoredStopVisit : smDelivery.getMonitoredStopVisits()) {
                    if (shouldBeKept(monitoredStopVisit, outboundSubscription)) {
                        filteredStopVisits.add(monitoredStopVisit);
                    }
                }
                smDelivery.getMonitoredStopVisits().clear();
                smDelivery.getMonitoredStopVisits().addAll(filteredStopVisits);
            }
        }
    }

    /**
     * Determines if the stopVisit should be kept or not.
     * Rules :
     * - arrivalStatus or departureStatus in (cancelled, arrived, departed) => kept
     * - first notification for this passage => kept
     * - expectedTime - aimedTime  < changeBeforeUpdate => kept
     * - otherwise : removed
     *
     * @param stopVisit            the vehicleJourney to check
     * @param outboundSubscription the outbound subscription that contain changeBeforeUpdate param
     * @return true : the delay is greater than changeBeforeUpdate param
     * false : the delay is lower than changeBeforeUpdate param
     */
    private boolean shouldBeKept(MonitoredStopVisit stopVisit, OutboundSubscriptionSetup outboundSubscription) {
        MonitoredVehicleJourneyStructure vehicleJourney = stopVisit.getMonitoredVehicleJourney();


        if (vehicleJourney == null || vehicleJourney.getMonitoredCall() == null) {
            return false;
        }


        if (CallStatusEnumeration.CANCELLED.equals(vehicleJourney.getMonitoredCall().getArrivalStatus()) ||
                CallStatusEnumeration.ARRIVED.equals(vehicleJourney.getMonitoredCall().getArrivalStatus()) ||
                CallStatusEnumeration.DEPARTED.equals(vehicleJourney.getMonitoredCall().getArrivalStatus()) ||
                CallStatusEnumeration.CANCELLED.equals(vehicleJourney.getMonitoredCall().getDepartureStatus()) ||
                CallStatusEnumeration.ARRIVED.equals(vehicleJourney.getMonitoredCall().getDepartureStatus()) ||
                CallStatusEnumeration.DEPARTED.equals(vehicleJourney.getMonitoredCall().getDepartureStatus())) {
            return true;
        }

        if (vehicleJourney.getMonitoredCall().getExpectedDepartureTime() == null || vehicleJourney.getMonitoredCall().getAimedDepartureTime() == null) {
            return false;
        }


        String notificationId = stopVisit.getMonitoringRef().getValue() + stopVisit.getItemIdentifier();
        if (!outboundSubscription.hasNotificationBeenAlreadySent(notificationId)) {
            // this notification has never been sent to this customer. Initial delivery for this customer. Recording this notification and keeping the message that must be sent
            outboundSubscription.recordNotification(notificationId);
            return true;
        }

        // this notification has already been sent to this customer. applying the check on departureTime to check if the notification must be kept or removed
        long expectedDepartureTime = vehicleJourney.getMonitoredCall().getExpectedDepartureTime().toInstant().toEpochMilli() / 1000;
        long aimedDepartureTime = vehicleJourney.getMonitoredCall().getAimedDepartureTime().toInstant().toEpochMilli() / 1000;

        return (expectedDepartureTime - aimedDepartureTime) >= outboundSubscription.getChangeBeforeUpdates();

    }


    private void removeVehicleMonitoringIfChangeBeforeUpdates(List<Siri> splitSiri, OutboundSubscriptionSetup subscriptionRequest) {
        splitSiri.stream()
                .filter(siri -> siri.getServiceDelivery().getVehicleMonitoringDeliveries() != null)
                .flatMap(siri -> siri.getServiceDelivery().getVehicleMonitoringDeliveries().stream())
                .forEach(monitoringDeliveryStructure -> monitoringDeliveryStructure.getVehicleActivities()
                        .removeIf(vehicleActivityStructure ->
                                ifChangeBeforeUpdates(vehicleActivityStructure, subscriptionRequest)
                        ));
    }

    private ExecutorService getOrCreateExecutorService(OutboundSubscriptionSetup subscriptionRequest) {

        if (executors == null) {
            executors = Executors.newVirtualThreadPerTaskExecutor();
        }

        return executors;
    }


    /**
     * Clean up dead ExecutorServices
     *
     * @param subscriptionManager
     */
    private void removeDeadSubscriptionExecutors(ServerSubscriptionManager subscriptionManager) {
        List<String> idsToRemove = new ArrayList<>();
        for (String id : threadFactoryMap.keySet()) {
            if (!subscriptionManager.subscriptions.containsKey(id)) {
                final ExecutorService service = threadFactoryMap.get(id);
                idsToRemove.add(id);
                // Force shutdown since outbound subscription has been stopped
                service.shutdownNow();
            }
        }
        if (!idsToRemove.isEmpty()) {
            for (String id : idsToRemove) {
                logger.info("Remove executor for subscription {}", id);
                threadFactoryMap.remove(id);
            }
        }
    }


    public void postDataToSubscription(String datasetId, Siri payload, OutboundSubscriptionSetup subscription, boolean showBody, Long inboundTime) {
        Map<String, Object> headers = new HashMap<>();
        if (subscription.isSicAQuaySubscription()) {
            GmSIVSicAQuayPostProcessor.filteringSiriGMToKeepSicAQuayAlertMessages(payload);
        }

        if (serviceDeliveryContainsData(payload)) {
            String remoteEndPoint = subscription.getAddress();

            headers.put("breadcrumbId", MDC.get("camel.breadcrumbId"));
            headers.put("endpoint", remoteEndPoint);
            headers.put("SubscriptionId", subscription.getSubscriptionId());
            headers.put("showBody", showBody);
            headers.put("datasetId", datasetId);
            headers.put("requestorRef", subscription.getRequestorRef());
            headers.put(INTERNAL_SIRI_DATA_TYPE, subscription.getSubscriptionType().name());
            headers.put(SIRI_VERSION_HEADER_NAME, subscription.getSiriVersion());
            headers.put(CONTENT_ENCODING, subscription.getCompressionFormat().getCode());
            List<ValueAdapter> adapters = getAdapters(datasetId, subscription);

            headers.put(OUTPUT_ADAPTERS_HEADER_NAME, adapters);
            if (subscription.isSOAPSubscription()) {
                headers.put(TRANSFORM_SOAP, TRANSFORM_SOAP);
            }

            if (payload.getHeartbeatNotification() != null) {
                headers.put(HEARTBEAT_HEADER, HEARTBEAT_HEADER);
            }

            if (payload.getServiceDelivery() != null &&
                    CollectionUtils.isNotEmpty(payload.getServiceDelivery().getGeneralMessageDeliveries()) &&
                    subscription.getSiriVersion() == SiriValidator.Version.VERSION_2_0_IDFM_2_4
            ) {
                headers.put(IS_IDFM_GM, Boolean.TRUE);
            }
            headers.put(INBOUND_TIME_HEADER_NAME, inboundTime);

            siriSubscriptionProcessor.sendBodyAndHeaders(payload, headers);
        }
    }

    private List<ValueAdapter> getAdapters(String datasetId, OutboundSubscriptionSetup subscription) {

        if (StringUtils.isEmpty(datasetId)) {
            //Initial delivery possibly multi-dataset. Has already been converted. No need to apply additionnal conversion
            return Collections.emptyList();
        }
        if (StringUtils.isNotEmpty(datasetId) && subscription.getValueAdaptersByDataset().containsKey(datasetId)) {
            return subscription.getValueAdaptersByDataset().get(datasetId);
        } else {
            return subscription.getValueAdapters();
        }

    }

    /**
     * Returns false if payload contains an empty ServiceDelivery (i.e. no actual SIRI-data), otherwise it returns false
     *
     * @param payload
     * @return
     */
    private boolean serviceDeliveryContainsData(Siri payload) {
        if (payload.getServiceDelivery() != null) {
            ServiceDelivery serviceDelivery = payload.getServiceDelivery();

            if (SiriHelper.containsValues(serviceDelivery.getSituationExchangeDeliveries())) {
                SituationExchangeDeliveryStructure deliveryStructure = serviceDelivery.getSituationExchangeDeliveries().get(0);
                return deliveryStructure.getSituations() != null &&
                        SiriHelper.containsValues(deliveryStructure.getSituations().getPtSituationElements());
            }

            if (SiriHelper.containsValues(serviceDelivery.getVehicleMonitoringDeliveries())) {
                VehicleMonitoringDeliveryStructure deliveryStructure = serviceDelivery.getVehicleMonitoringDeliveries().get(0);
                return deliveryStructure.getVehicleActivities() != null &&
                        SiriHelper.containsValues(deliveryStructure.getVehicleActivities());
            }

            if (SiriHelper.containsValues(serviceDelivery.getEstimatedTimetableDeliveries())) {
                EstimatedTimetableDeliveryStructure deliveryStructure = serviceDelivery.getEstimatedTimetableDeliveries().get(0);
                return (deliveryStructure.getEstimatedJourneyVersionFrames() != null &&
                        SiriHelper.containsValues(deliveryStructure.getEstimatedJourneyVersionFrames()) &&
                        SiriHelper.containsValues(deliveryStructure.getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies()));
            }
            if (SiriHelper.containsValues(serviceDelivery.getStopMonitoringDeliveries())) {
                StopMonitoringDeliveryStructure deliveryStructure = serviceDelivery.getStopMonitoringDeliveries().get(0);

                return (SiriHelper.containsValues(deliveryStructure.getMonitoredStopVisitCancellations()) &&
                        deliveryStructure.getMonitoredStopVisitCancellations().get(0) != null) ||
                        (SiriHelper.containsValues(deliveryStructure.getMonitoredStopVisits()) &&
                                deliveryStructure.getMonitoredStopVisits().get(0).getMonitoredVehicleJourney() != null);
            }
            if (SiriHelper.containsValues(serviceDelivery.getGeneralMessageDeliveries())) {
                GeneralMessageDeliveryStructure deliveryStructure = serviceDelivery.getGeneralMessageDeliveries().get(0);
                return (SiriHelper.containsValues(deliveryStructure.getGeneralMessages())) ||
                        (SiriHelper.containsValues(deliveryStructure.getGeneralMessageCancellations()));
            }
            if (SiriHelper.containsValues(serviceDelivery.getFacilityMonitoringDeliveries())) {
                FacilityMonitoringDeliveryStructure deliveryStructure = serviceDelivery.getFacilityMonitoringDeliveries().get(0);
                return (SiriHelper.containsValues(deliveryStructure.getFacilityConditions()));
            }
        }
        return true;
    }

    private boolean ifChangeBeforeUpdates(VehicleActivityStructure vehicleActivityStructure, OutboundSubscriptionSetup outboundSubscriptionSetup) {
        if (vehicleActivityStructure.getMonitoredVehicleJourney().getMonitoredCall() != null
                && vehicleActivityStructure.getMonitoredVehicleJourney().getMonitoredCall().getExpectedDepartureTime() != null
                && vehicleActivityStructure.getMonitoredVehicleJourney().getMonitoredCall().getAimedDepartureTime() != null) {
            long expectedDepartureTime = vehicleActivityStructure.getMonitoredVehicleJourney().getMonitoredCall().getExpectedDepartureTime().toInstant().toEpochMilli() / 1000;
            long aimedDepartureTime = vehicleActivityStructure.getMonitoredVehicleJourney().getMonitoredCall().getAimedDepartureTime().toInstant().toEpochMilli() / 1000;

            return (expectedDepartureTime - aimedDepartureTime) > outboundSubscriptionSetup.getChangeBeforeUpdates();
        }
        return false;
    }
}
