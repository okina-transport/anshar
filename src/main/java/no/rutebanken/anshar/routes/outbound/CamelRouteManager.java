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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.outbound.SituationExchangeOutbound;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

import static no.rutebanken.anshar.routes.HttpParameter.SIRI_VERSION_HEADER_NAME;
import static no.rutebanken.anshar.routes.siri.Siri20RequestHandlerRoute.TRANSFORM_SOAP;
import static no.rutebanken.anshar.routes.siri.transformer.SiriOutputTransformerRoute.OUTPUT_ADAPTERS_HEADER_NAME;
import static no.rutebanken.anshar.routes.validation.validators.Constants.HEARTBEAT_HEADER;

@Service
public class CamelRouteManager {
    private static final Logger logger = LoggerFactory.getLogger(CamelRouteManager.class);

    @Autowired
    private SiriHelper siriHelper;

    @Autowired
    ServerSubscriptionManager subscriptionManager;

    @Value("${anshar.default.max.elements.per.delivery:1000}")
    private int maximumSizePerDelivery;

    @Value("${anshar.default.max.threads.per.outbound.subscription:20}")
    private int maximumThreadsPerOutboundSubscription;

    @Produce(uri = "direct:send.to.external.subscription")
    protected ProducerTemplate siriSubscriptionProcessor;

    @Autowired
    ScheduledOutboundSubscriptionConfig scheduledOutboundSubscriptionConfig;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private SituationExchangeOutbound situationExchangeOutbound;

    @Autowired
    private PrometheusMetricsService prometheusMetricsService;

    private ThreadPoolExecutor executors;


    /**
     * Splits SIRI-data if applicable, and pushes data to external subscription
     *
     * @param payload
     * @param subscriptionRequest
     */
    void pushSiriData(Siri payload, OutboundSubscriptionSetup subscriptionRequest, boolean logBody) {

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
                    // Short circuit if subscription has been terminated while waiting
                    return;
                }

                Siri filteredPayload = SiriHelper.filterSiriPayload(payload, subscriptionRequest.getFilterMap());

                int deliverySize = this.maximumSizePerDelivery;
                if (subscriptionRequest.getDatasetId() != null) {
                    deliverySize = Integer.MAX_VALUE;
                }

                List<Siri> splitSiri = siriHelper.splitDeliveries(filteredPayload, deliverySize);

                if (splitSiri.size() > 1) {
                    logger.info("Object split into {} deliveries for subscription {}.", splitSiri.size(), subscriptionRequest);
                }

                // On ne notifie pas les abonnés si (temps estimé (TR) - temps attendu (TH)) < changeBeforeUpdates de l'abonnement
                if (subscriptionRequest.getChangeBeforeUpdates() > 0) {
                    removeVehicleMonitoringIfChangeBeforeUpdates(splitSiri, subscriptionRequest);
                }


                // On remplace les données partielles reçues par l'intégralité de la donnée si incrementalUpdate de l'abonnement est à false
                if (!subscriptionRequest.getIncrementalUpdates()) {

                    if (SiriDataType.VEHICLE_MONITORING.equals(subscriptionRequest.getSubscriptionType())) {
                        splitSiri = replaceByCompleteVMData(subscriptionRequest);
                    } else if (SiriDataType.SITUATION_EXCHANGE.equals(subscriptionRequest.getSubscriptionType())) {
                        splitSiri = replaceByCompleteSXData(subscriptionRequest);
                    }


                }

                for (Siri siri : splitSiri) {
                    // On crée des déclencheurs pour ne notifier les abonnés que tous les x moments définis par l'updateInterval
                    if (subscriptionRequest.getUpdateInterval() > 0) {
                        scheduledOutboundSubscriptionConfig.createScheduledOutboundSubscription(siri, subscriptionRequest);
                    } else {
                        postDataToSubscription(siri, subscriptionRequest, logBody);
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

    private List<Siri> replaceByCompleteSXData(OutboundSubscriptionSetup subscriptionRequest) {
        OutboundIdMappingPolicy mappingPolicy;
        List<Siri> results = new ArrayList<>();

        if (subscriptionRequest != null && subscriptionRequest.isUseOriginalId()) {
            mappingPolicy = OutboundIdMappingPolicy.ORIGINAL_ID;
        } else {
            mappingPolicy = OutboundIdMappingPolicy.DEFAULT;
        }


        Siri completeSx = situationExchangeOutbound.createServiceDelivery(subscriptionRequest.getRequestorRef(), subscriptionRequest.getDatasetId(), subscriptionRequest.getClientTrackingName(), mappingPolicy, 10000);
        results.add(completeSx);
        return results;
    }

    private List<Siri> replaceByCompleteVMData(OutboundSubscriptionSetup subscriptionRequest) {
        return Collections.singletonList(vehicleActivities.createServiceDelivery(subscriptionRequest.getRequestorRef(), subscriptionRequest.getDatasetId(), subscriptionRequest.getClientTrackingName(),
                null, Integer.MAX_VALUE, subscriptionRequest.getFilterMap().get(LineRef.class), subscriptionRequest.getFilterMap().get(VehicleRef.class)));
    }


    private void removeVehicleMonitoringIfChangeBeforeUpdates(List<Siri> splitSiri, OutboundSubscriptionSetup subscriptionRequest) {
        splitSiri.stream()
                .flatMap(siri -> siri.getServiceDelivery().getVehicleMonitoringDeliveries().stream())
                .forEach(monitoringDeliveryStructure -> monitoringDeliveryStructure.getVehicleActivities()
                        .removeIf(vehicleActivityStructure ->
                                ifChangeBeforeUpdates(vehicleActivityStructure, subscriptionRequest)
                        ));
    }

    Map<String, ExecutorService> threadFactoryMap = new HashMap<>();

    private ExecutorService getOrCreateExecutorService(OutboundSubscriptionSetup subscriptionRequest) {

        if (executors == null) {
            ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat("outbound").build();
            executors = (ThreadPoolExecutor) Executors.newFixedThreadPool(maximumThreadsPerOutboundSubscription, factory);
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

    public int getPushSubscriptionWaitingQueueSize() {
        return executors == null ? 0 : executors.getQueue().size();
    }

    public void postDataToSubscription(Siri payload, OutboundSubscriptionSetup subscription, boolean showBody) {
        Map<String, Object> headers = new HashMap<>();
        if (serviceDeliveryContainsData(payload)) {
            String remoteEndPoint = subscription.getAddress();

            headers.put("breadcrumbId", MDC.get("camel.breadcrumbId"));
            headers.put("endpoint", remoteEndPoint);
            headers.put("SubscriptionId", subscription.getSubscriptionId());
            headers.put("showBody", showBody);
            headers.put("requestorRef", subscription.getRequestorRef());
            headers.put(SIRI_VERSION_HEADER_NAME, subscription.getSiriVersion());
            headers.put(OUTPUT_ADAPTERS_HEADER_NAME, subscription.getValueAdapters());
            if (subscription.isSOAPSubscription()) {
                headers.put(TRANSFORM_SOAP, TRANSFORM_SOAP);
            }

            if (payload.getHeartbeatNotification() != null) {
                headers.put(HEARTBEAT_HEADER, HEARTBEAT_HEADER);
            }

            siriSubscriptionProcessor.sendBodyAndHeaders(payload, headers);
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
                return (SiriHelper.containsValues(deliveryStructure.getMonitoredStopVisits()) &&
                        deliveryStructure.getMonitoredStopVisits().get(0).getMonitoredVehicleJourney() != null);
            }
            if (SiriHelper.containsValues(serviceDelivery.getGeneralMessageDeliveries())) {
                GeneralMessageDeliveryStructure deliveryStructure = serviceDelivery.getGeneralMessageDeliveries().get(0);
                return (SiriHelper.containsValues(deliveryStructure.getGeneralMessages()));
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
            long expectedDepartureTime = vehicleActivityStructure.getMonitoredVehicleJourney().getMonitoredCall().getExpectedDepartureTime().toInstant().toEpochMilli();
            long aimedDepartureTime = vehicleActivityStructure.getMonitoredVehicleJourney().getMonitoredCall().getAimedDepartureTime().toInstant().toEpochMilli();

            return (expectedDepartureTime - aimedDepartureTime) > outboundSubscriptionSetup.getChangeBeforeUpdates();
        }
        return false;
    }
}
