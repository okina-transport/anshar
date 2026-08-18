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

package no.rutebanken.anshar.subscription;

import com.google.common.base.Preconditions;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.DiscoverySubscription;
import no.rutebanken.anshar.data.DiscoveryCache;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.health.IncomingDataHealthService;
import no.rutebanken.anshar.routes.siri.*;
import no.rutebanken.anshar.routes.siri.adapters.Mapping;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.processor.*;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.subscription.SubscriptionSetup.ServiceType.SOAP;

@Component
public class SubscriptionInitializer implements CamelContextAware {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionInitializer.class);
    private final SubscriptionManager subscriptionManager;
    private final SubscriptionConfig subscriptionConfig;
    private final SiriHandler handler;
    private final AnsharConfiguration configuration;
    private final DiscoveryCache discoveryCache;
    private final PrometheusMetricsService metrics;
    private final IncomingDataHealthService incomingDataHealthService;
    private final TaskScheduler taskScheduler;
    private CamelContext camelContext;
    private ScheduledFuture<?> waitingInit;

    public SubscriptionInitializer(SubscriptionManager subscriptionManager, SubscriptionConfig subscriptionConfig,
                                   SiriHandler handler, AnsharConfiguration configuration, DiscoveryCache discoveryCache,
                                   PrometheusMetricsService metrics, IncomingDataHealthService incomingDataHealthService,
                                   TaskScheduler taskScheduler) {
        this.subscriptionManager = subscriptionManager;
        this.subscriptionConfig = subscriptionConfig;
        this.handler = handler;
        this.configuration = configuration;
        this.discoveryCache = discoveryCache;
        this.metrics = metrics;
        this.incomingDataHealthService = incomingDataHealthService;
        this.taskScheduler = taskScheduler;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        logger.info("ShutdownStrategy: {}", camelContext.getShutdownStrategy());
    }

    @PreDestroy
    void shutdown() {
        logger.info("Triggering Camel shutdown");
        this.camelContext.shutdown();
        logger.info("Triggered Camel shutdown");
    }

    @PostConstruct
    void createSubscriptions() {


        if (isWaitingInit()) {
            logger.info("Another initialization is scheduled. Cancelling this one");
            return;
        }

        if (!configuration.isVJCacheLoaded()) {
            logger.info("VJ cache not loaded. Trying again in 30s");
            waitingInit = taskScheduler.schedule(this::createSubscriptions, Instant.now().plusSeconds(30));
            return;
        }

        camelContext.setUseMDCLogging(true);
        camelContext.setUseBreadcrumb(true);

        if (!configuration.getAppModes().isEmpty()) {
            logger.info("App started with mode(s): {}", configuration.getAppModes());
        }

        if (!configuration.isCurrentInstanceLeader()) {
            logger.info("===> Current instance not leader. Not launching subscriptions");
            return;
        }

        subscriptionManager.updateChildSubscriptionsFromParent(subscriptionConfig.getDiscoverySubscriptions());

        final Map<String, Object> mappingBeans = ApplicationContextHolder.getContext().getBeansWithAnnotation(Mapping.class);
        final Map<String, Class> mappingAdaptersById = new HashMap<>();
        for (final Object myFoo : mappingBeans.values()) {
            final Class<?> mappingAdapterClass = myFoo.getClass();
            final Mapping annotation = mappingAdapterClass.getAnnotation(Mapping.class);
            mappingAdaptersById.put(annotation.id(), mappingAdapterClass);
        }

        subscriptionManager.addMappingAdapters(mappingAdaptersById);

        logger.info("Initializing subscriptions for environment: {}", configuration.getEnvironment());

        if (subscriptionConfig != null) {
            List<SubscriptionSetup> subscriptionSetups = subscriptionConfig.getSubscriptions();
            logger.info("Initializing {} subscriptions", subscriptionSetups.size());


            List<SubscriptionSetup> actualSubscriptionSetups = new ArrayList<>();

            List<SubscriptionSetup> disabledSubscriptions = subscriptionSetups.stream()
                    .filter(subscriptionSetup -> !subscriptionSetup.isActive())
                    .collect(Collectors.toList());

            disabledSubscriptions.addAll(getDisabledDiscoveryChilds());

            List<SubscriptionSetup> activeSubscriptions = subscriptionSetups.stream()
                    .filter(sub -> sub.isActive() && !disabledSubscriptions.contains(sub))
                    .collect(Collectors.toList());

            subscriptionManager.resetPreviouslyStoppedSubscriptions(activeSubscriptions);

            if (!disabledSubscriptions.isEmpty()) {
                disableSubscriptions(disabledSubscriptions);
            }

            logger.info("Initializing {} subscriptions", activeSubscriptions.size());
            // Validation and consistency-verification
            for (SubscriptionSetup subscriptionSetup : activeSubscriptions) {
                addInfoToSubscription(mappingAdaptersById, subscriptionSetup);
                SubscriptionSetup existingSubscription = subscriptionManager.getSubscriptionBySubscriptionId(subscriptionSetup.getSubscriptionId());
                if (existingSubscription != null) {
                    if (!existingSubscription.equals(subscriptionSetup)) {
                        logger.info("Subscription with internalId={} is updated - reinitializing. {}", subscriptionSetup.getInternalId(), subscriptionSetup);
                        disableSubscriptions(List.of(existingSubscription));
                    } else {
                        subscriptionManager.updateSubscription(subscriptionSetup);
                    }


                }
                if (existingSubscription != null && !existingSubscription.equals(subscriptionSetup)) {
                    logger.info("Subscription with internalId={} is updated - reinitializing. {}", subscriptionSetup.getInternalId(), subscriptionSetup);
                    disableSubscriptions(List.of(existingSubscription));
                }
                actualSubscriptionSetups.add(subscriptionSetup);
            }

            for (SubscriptionSetup subscriptionSetup : actualSubscriptionSetups) {
                if (!subscriptionManager.isSubscriptionRegistered(subscriptionSetup.getSubscriptionId())) {
                    subscriptionManager.addSubscription(subscriptionSetup.getSubscriptionId(), subscriptionSetup);
                    initRouteBuilders(subscriptionSetup);
                }
            }

        } else {
            logger.error("Subscriptions not configured correctly - no subscriptions will be started");
        }
    }

    private void addInfoToSubscription(Map<String, Class> mappingAdaptersById, SubscriptionSetup subscriptionSetup) {

        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.STOP_MONITORING)) {
            discoveryCache.addStops(subscriptionSetup.getDatasetId(), subscriptionSetup.getStopMonitoringRefValues());
        }

        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.VEHICLE_MONITORING)) {
            discoveryCache.addLines(subscriptionSetup.getDatasetId(), subscriptionSetup.getLineRefValues());
        }

        subscriptionSetup.setAddress(configuration.getInboundUrl());

        if (!isValid(subscriptionSetup)) {
            throw new ServiceConfigurationError("Configuration is not valid for subscription " + subscriptionSetup);
        }


        List<ValueAdapter> valueAdapters = new ArrayList<>();

        if (mappingAdaptersById.containsKey(subscriptionSetup.getMappingAdapterId())) {
            Class adapterClass = mappingAdaptersById.get(subscriptionSetup.getMappingAdapterId());
            try {
                valueAdapters.addAll((List<ValueAdapter>) adapterClass.getMethod("getValueAdapters", SubscriptionSetup.class).invoke(adapterClass.newInstance(), subscriptionSetup));

            } catch (Exception e) {
                throw new ServiceConfigurationError("Invalid mappingAdapterId for subscription " + subscriptionSetup, e);
            }
        }
        //Is added to ALL subscriptions AFTER subscription-specific adapters
        valueAdapters.add(new CodespaceProcessor(subscriptionSetup.getDatasetId()));
        valueAdapters.add(new EnsureIncreasingTimesForCancelledStopsProcessor(subscriptionSetup.getDatasetId()));
        valueAdapters.add(new ExtraJourneyDestinationDisplayPostProcessor(subscriptionSetup.getDatasetId()));
        valueAdapters.add(new EnsureNonNullVehicleModePostProcessor(subscriptionSetup.getDatasetId()));

        if (SiriDataType.STOP_MONITORING.equals(subscriptionSetup.getSubscriptionType()) && subscriptionSetup.isOverrideDestinationName()) {
            valueAdapters.add(new UpdateDestinationNameProcessor(subscriptionSetup.getDatasetId()));
        }

        subscriptionSetup.setMappingAdapters(valueAdapters);

        if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.FETCHED_DELIVERY ||
                subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.POLLING_FETCHED_DELIVERY) {

            //Fetched delivery needs both subscribe-route and ServiceRequest-route
            String url = subscriptionSetup.getUrlMap().get(RequestType.SUBSCRIBE);

            subscriptionSetup.getUrlMap().putIfAbsent(RequestType.GET_ESTIMATED_TIMETABLE, url);
            subscriptionSetup.getUrlMap().putIfAbsent(RequestType.GET_VEHICLE_MONITORING, url);
            subscriptionSetup.getUrlMap().putIfAbsent(RequestType.GET_SITUATION_EXCHANGE, url);
        }
    }

    private boolean isWaitingInit() {
        if (waitingInit == null) {
            return false;
        }

        boolean isCancelled = waitingInit.isCancelled();
        boolean isDone = waitingInit.isDone();
        long delayMillis = waitingInit.getDelay(TimeUnit.MILLISECONDS);
        return !isDone && !isCancelled && delayMillis > 0;
    }

    private List<SubscriptionSetup> getDisabledDiscoveryChilds() {
        List<SubscriptionSetup> disabledDiscoveryChilds = new ArrayList<>();
        Set<DiscoverySubscription> disabledParents = subscriptionConfig.getDiscoverySubscriptions().stream()
                .filter(disc -> !disc.getActive())
                .collect(Collectors.toSet());

        for (DiscoverySubscription disabledParent : disabledParents) {
            Set<SubscriptionSetup> childrenToDisable = subscriptionManager.getAllSubscriptions(disabledParent.getDiscoveryType()).stream()
                    .filter(sub -> disabledParent.getSubscriptionIdBase().equals(sub.getParentSubscriptionId()))
                    .collect(Collectors.toSet());

            disabledDiscoveryChilds.addAll(childrenToDisable);
        }


        return disabledDiscoveryChilds;
    }


    private void initRouteBuilders(SubscriptionSetup subscriptionSetup) {
        try {
            List<RouteBuilder> routeBuilder = getRouteBuilders(subscriptionSetup);
            //Adding all routes to current context
            for (RouteBuilder builder : routeBuilder) {
                camelContext.addRoutes(builder);
            }

        } catch (Exception e) {
            logger.warn("Could not add subscription", e);
        }
    }

    /**
     * Disable a list of subscriptions given as parameter
     *
     * @param disabledSubscriptions
     */
    public void disableSubscriptions(List<SubscriptionSetup> disabledSubscriptions) {
        for (SubscriptionSetup disabledSubscription : disabledSubscriptions) {
            String subscriptionIdToDisable = disabledSubscription.getSubscriptionId();
            if (subscriptionManager.isSubscriptionRegistered(subscriptionIdToDisable) && subscriptionManager.get(subscriptionIdToDisable).isActive()) {
                subscriptionManager.sendTerminateRequest(disabledSubscription);
                disabledSubscription.setActive(false);
                disabledSubscription.setStatus(SubscriptionStatus.STOPPED);
                subscriptionManager.updateSubscription(disabledSubscription);

            }
        }
    }

    List<RouteBuilder> getRouteBuilders(SubscriptionSetup subscriptionSetup) {
        List<RouteBuilder> routeBuilders = new ArrayList<>();

        boolean isSubscription = subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE;
        boolean isLite = subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.LITE || subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.LITE_XML;
        boolean isFetchedDelivery = subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.FETCHED_DELIVERY |
                subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.POLLING_FETCHED_DELIVERY;
        boolean isSoap = subscriptionSetup.getServiceType() == SOAP;

        if (subscriptionSetup.getVersion().equals("1.4")) {
            if (isSoap) {
                if (isSubscription || isFetchedDelivery) {
                    routeBuilders.add(new Siri20ToSiriWS14Subscription(configuration, handler, subscriptionSetup, subscriptionManager));
                } else {
                    routeBuilders.add(new Siri20ToSiriWS14RequestResponse(configuration, subscriptionSetup, subscriptionManager));
                }
                if (isFetchedDelivery) {
                    routeBuilders.add(new Siri20ToSiriWS14RequestResponse(configuration, subscriptionSetup, subscriptionManager));
                }
            } else {
                routeBuilders.add(new Siri20ToSiriRS14Subscription(configuration, handler, subscriptionSetup, subscriptionManager));
            }
        } else {
            if (isSoap) {
                if (isSubscription || isFetchedDelivery) {
                    routeBuilders.add(new Siri20ToSiriWS20Subscription(configuration, handler, subscriptionSetup, subscriptionManager));

                    if (isFetchedDelivery || subscriptionSetup.isDataSupplyRequestForInitialDelivery()) {
                        routeBuilders.add(new Siri20ToSiriWS20RequestResponse(configuration, subscriptionSetup, subscriptionManager, incomingDataHealthService, metrics));
                    }
                } else {
                    routeBuilders.add(new Siri20ToSiriWS20RequestResponse(configuration, subscriptionSetup, subscriptionManager, incomingDataHealthService, metrics));
                }
            } else {
                if (isSubscription || isFetchedDelivery) {
                    routeBuilders.add(new Siri20ToSiriRS20Subscription(configuration, handler, subscriptionSetup, subscriptionManager));

                    if (isFetchedDelivery || subscriptionSetup.isDataSupplyRequestForInitialDelivery()) {
                        routeBuilders.add(new Siri20ToSiriRS20RequestResponse(configuration, subscriptionSetup, subscriptionManager));
                    }
                } else if (isLite) {
                    routeBuilders.add(new SiriLiteToSiriRS20RequestResponse(configuration, subscriptionSetup, subscriptionManager, incomingDataHealthService, metrics));
                } else {
                    routeBuilders.add(new Siri20ToSiriRS20RequestResponse(configuration, subscriptionSetup, subscriptionManager));
                }
            }
        }
        return routeBuilders;
    }

    private boolean isValid(SubscriptionSetup s) {
        Preconditions.checkNotNull(s.getVendor(), "Vendor is not set");
        Preconditions.checkNotNull(s.getDatasetId(), "DatasetId is not set");
        Preconditions.checkNotNull(s.getServiceType(), "ServiceType is not set");
        Preconditions.checkNotNull(s.getSubscriptionType(), "SubscriptionType is not set");
        Preconditions.checkNotNull(s.getVersion(), "Version is not set");
        Preconditions.checkNotNull(s.getSubscriptionId(), "SubscriptionId is not set");
        Preconditions.checkNotNull(s.getRequestorRef(), "RequestorRef is not set");
        Preconditions.checkNotNull(s.getSubscriptionMode(), "SubscriptionMode is not set");
        Preconditions.checkNotNull(s.getContentType(), "ContentType is not set");

        Preconditions.checkNotNull(s.getDurationOfSubscription(), "Duration is not set");
        Preconditions.checkState(s.getDurationOfSubscription().toMillis() > 0, "Duration must be > 0");

        Preconditions.checkNotNull(s.getHeartbeatInterval(), "HeartbeatInterval is not set");
        Preconditions.checkState(s.getHeartbeatInterval().toMillis() > 0, "HeartbeatInterval must be > 0");

        Preconditions.checkNotNull(s.getUrlMap(), "UrlMap is not set");
        Map<RequestType, String> urlMap = s.getUrlMap();
        if (s.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE || s.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.LITE
                || s.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.LITE_XML) {

            if (SiriDataType.SITUATION_EXCHANGE.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(urlMap.get(RequestType.GET_SITUATION_EXCHANGE), "GET_SITUATION_EXCHANGE-url is missing. " + s);
            } else if (SiriDataType.VEHICLE_MONITORING.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(urlMap.get(RequestType.GET_VEHICLE_MONITORING), "GET_VEHICLE_MONITORING-url is missing. " + s);
            } else if (SiriDataType.ESTIMATED_TIMETABLE.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(urlMap.get(RequestType.GET_ESTIMATED_TIMETABLE), "GET_ESTIMATED_TIMETABLE-url is missing. " + s);
            } else if (SiriDataType.STOP_MONITORING.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(urlMap.get(RequestType.GET_STOP_MONITORING), "GET_STOP_MONITORING-url is missing. " + s);
                Preconditions.checkNotNull(s.getStopMonitoringRefValues(), "stopMonitoringRefValue is missing. " + s);
            } else if (SiriDataType.GENERAL_MESSAGE.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(urlMap.get(RequestType.GET_GENERAL_MESSAGE), "GET_GENERAL_MESSAGE-url is missing. " + s);
            } else if (SiriDataType.FACILITY_MONITORING.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(urlMap.get(RequestType.GET_FACILITY_MONITORING), "GET_FACILITY_MONITORING-url is missing. " + s);
            } else {
                Preconditions.checkArgument(false, "URLs not configured correctly");
            }
        } else if (s.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {

            //Type-specific requirements
            if (SiriDataType.ESTIMATED_TIMETABLE.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(s.getPreviewInterval(), "PreviewInterval is not set");
            } else if (SiriDataType.SITUATION_EXCHANGE.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(s.getPreviewInterval(), "PreviewInterval is not set");
            } else if (SiriDataType.STOP_MONITORING.equals(s.getSubscriptionType())) {
//                Preconditions.checkNotNull(s.getStopMonitoringRefValue());
            }

            Preconditions.checkNotNull(urlMap.get(RequestType.SUBSCRIBE), "SUBSCRIBE-url is missing. " + s);
            Preconditions.checkNotNull(urlMap.get(RequestType.DELETE_SUBSCRIPTION), "DELETE_SUBSCRIPTION-url is missing. " + s);
        } else if (s.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.FETCHED_DELIVERY |
                s.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.POLLING_FETCHED_DELIVERY) {
            Preconditions.checkNotNull(urlMap.get(RequestType.SUBSCRIBE), "SUBSCRIBE-url is missing. " + s);
            Preconditions.checkNotNull(urlMap.get(RequestType.DELETE_SUBSCRIPTION), "DELETE_SUBSCRIPTION-url is missing. " + s);
        } else {
            Preconditions.checkArgument(false, "Subscription mode not configured");
        }

        if (!SiriDataType.VEHICLE_MONITORING.equals(s.getSubscriptionType())) {
            Preconditions.checkArgument(!s.forwardPositionData(), "Position only is only valid for VM-subscription.");
        }

        return true;
    }
}
