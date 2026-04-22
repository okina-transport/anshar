package no.rutebanken.anshar.subscription;

import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.config.DiscoverySubscription;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import no.rutebanken.anshar.util.IDUtils;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.DiscoverySubscriptionsRouteBuilder.SEND_DISCOVERY_REQUEST_ROUTE;
import static no.rutebanken.anshar.subscription.SubscriptionConstants.DISCOVERY_SUBSCRIPTION_SERVICE_TYPE;


@Service
public class DiscoverySubscriptionCreator {
    private static final Logger logger = LoggerFactory.getLogger(DiscoverySubscriptionCreator.class);
    public static final String SUBSCRIPTION_URL_HEADER = "subscriptionUrl";
    public static final String ENDPOINT_URL_HEADER = "endpointUrl";
    public static final String SOAP_ACTION_HEADER = "SOAPAction";
    private static final int NB_OF_REFS_BY_SUBSCRIPTION = 30;

    private final SubscriptionConfig subscriptionConfig;

    private final SubscriptionInitializer subscriptionInitializer;

    private final ExtendedHazelcastService hazelcast;

    @Produce(SEND_DISCOVERY_REQUEST_ROUTE)
    protected ProducerTemplate discoveryRequestProducer;


    public DiscoverySubscriptionCreator(SubscriptionConfig subscriptionConfig, SubscriptionInitializer subscriptionInitializer, ExtendedHazelcastService hazelcast) {
        this.subscriptionConfig = subscriptionConfig;
        this.subscriptionInitializer = subscriptionInitializer;
        this.hazelcast = hazelcast;
    }

    public void createDiscoverySubscriptions() {
        logger.info("Starting subscription creation from discovery");
        for (DiscoverySubscription discoverySubscription : subscriptionConfig.getDiscoverySubscriptions()) {
            if (discoverySubscription.getActive() && shouldBeStarted(discoverySubscription)) {
                markAsInitialized(discoverySubscription);
                createSubscriptions(discoverySubscription);
            }
        }
        logger.info("Subscription creations from discovery completed");
    }

    private void markAsInitialized(DiscoverySubscription discoverySubscription) {
        switch (discoverySubscription.getDiscoveryType()) {
            case STOP_MONITORING -> {
                hazelcast.getSMDiscoveryInitialized().add(discoverySubscription.getSubscriptionIdBase());
            }
            case VEHICLE_MONITORING -> {
                hazelcast.getVMDiscoveryInitialized().add(discoverySubscription.getSubscriptionIdBase());
            }
        }
    }


    private boolean shouldBeStarted(DiscoverySubscription discoverySubscription) {
        switch (discoverySubscription.getDiscoveryType()) {
            case STOP_MONITORING -> {
                return !hazelcast.getSMDiscoveryInitialized().contains(discoverySubscription.getSubscriptionIdBase());
            }
            case VEHICLE_MONITORING -> {
                return !hazelcast.getVMDiscoveryInitialized().contains(discoverySubscription.getSubscriptionIdBase());
            }
            default -> {
                throw new IllegalStateException("Unhandled discovery type: " + discoverySubscription.getDiscoveryType());
            }
        }
    }

    public void createSubscriptionsFromProviderResponse(Exchange e) throws XMLStreamException, JAXBException {
        InputStream body = e.getIn().getBody(InputStream.class);
        Siri incoming = SiriValueTransformer.parseXml(body);
        String originalUrl = (String) e.getIn().getHeader(ENDPOINT_URL_HEADER);
        String soapActionHeader = (String) e.getIn().getHeader(SOAP_ACTION_HEADER);
        SiriDataType discoveryType = SiriDataType.valueOf((String) e.getIn().getHeader(DISCOVERY_SUBSCRIPTION_SERVICE_TYPE));
        Optional<DiscoverySubscription> discoverySubsOpt = findDiscoveryParam(originalUrl, discoveryType);

        if (discoverySubsOpt.isEmpty()) {
            logger.error("Unable to find subscription for url : {}, soapActionHeader : {}", originalUrl, soapActionHeader);
            return;
        }

        DiscoverySubscription discoveryParams = discoverySubsOpt.get();
        List<String> referenceList = new ArrayList<>();

        if (SiriDataType.STOP_MONITORING.equals(discoveryType)) {
            referenceList = incoming.getStopPointsDelivery().getAnnotatedStopPointReves().stream()
                    .map(pointStructure -> pointStructure.getStopPointRef().getValue())
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());
        } else if (SiriDataType.VEHICLE_MONITORING.equals(discoveryType) || SiriDataType.ESTIMATED_TIMETABLE.equals(discoveryType)) {
            referenceList = incoming.getLinesDelivery().getAnnotatedLineReves().stream()
                    .map(annotatedLineRef -> annotatedLineRef.getLineRef().getValue())
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());
        }


        if (CollectionUtils.isNotEmpty(referenceList)) {
            List<SubscriptionSetup> subscriptionsToStart = createSubscriptionsSetups(referenceList, discoveryParams);
            subscriptionConfig.getSubscriptions().addAll(subscriptionsToStart);
        } else {
            logger.error("Discovery response received but no points found in response. id : {}", discoveryParams.getSubscriptionIdBase());
        }
        subscriptionInitializer.createSubscriptions();
    }

    public List<SubscriptionSetup> createSubscriptionsSetups(List<String> referenceList, DiscoverySubscription discoveryParams) {

        int currentNbOfMonitoredRef = 0;
        int subscriptionNb = 0;

        SubscriptionSetup currentSubscription = null;
        List<SubscriptionSetup> results = new ArrayList<>();


        for (String currentRef : referenceList) {

            if (currentNbOfMonitoredRef == 0) {
                currentSubscription = createSubscriptionSetup(currentRef, discoveryParams, subscriptionNb);
                subscriptionNb++;
                currentNbOfMonitoredRef++;
                continue;
            }

            if (SiriDataType.STOP_MONITORING.equals(discoveryParams.getDiscoveryType())) {
                currentSubscription.getStopMonitoringRefValues().add(currentRef);
            } else {
                currentSubscription.getLineRefValues().add(currentRef);
            }
            currentNbOfMonitoredRef++;

            if (currentNbOfMonitoredRef == NB_OF_REFS_BY_SUBSCRIPTION) {
                results.add(currentSubscription);
                currentNbOfMonitoredRef = 0;
            }

        }
        results.add(currentSubscription);
        return results;
    }

    private SubscriptionSetup createSubscriptionSetup(String value, DiscoverySubscription discoveryParams, int currentSubcrtiptionNb) {
        String type = getSubscriptionTypePrefix(discoveryParams.getDiscoveryType());
        SubscriptionSetup newSubscription = new SubscriptionSetup();
        newSubscription.setDatasetId(discoveryParams.getDatasetId());
        newSubscription.setSubscriptionType(discoveryParams.getDiscoveryType());
        newSubscription.setName(type + "-" + discoveryParams.getSubscriptionIdBase() + "-" + currentSubcrtiptionNb);
        newSubscription.setVendor(type + "-" + discoveryParams.getVendorBaseName() + "-" + currentSubcrtiptionNb);
        newSubscription.setServiceType(discoveryParams.getServiceType());
        newSubscription.setSubscriptionMode(discoveryParams.getSubscriptionMode());
        newSubscription.setHeartbeatIntervalSeconds(discoveryParams.getHeartbeatIntervalSeconds());
        newSubscription.setChangeBeforeUpdatesSeconds(discoveryParams.getChangeBeforeUpdatesSeconds());
        newSubscription.setUpdateIntervalSeconds(discoveryParams.getUpdateIntervalSeconds());
        newSubscription.setPreviewIntervalSeconds(discoveryParams.getPreviewIntervalSeconds());
        newSubscription.setOperatorNamespace("http://wsdl.siri.org.uk");
        newSubscription.setRequestorRef(discoveryParams.getRequestorRef());
        newSubscription.setInternalId(IDUtils.getUniqueInternalIdForDiscoverySubscription());

        Map<RequestType, String> urlMap = new EnumMap<>(RequestType.class);
        urlMap.put(RequestType.SUBSCRIBE, discoveryParams.getUrl());
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, discoveryParams.getUrl());
        RequestType reqType;
        String mappingAdapter;
        String stopMonitoringValue = null;
        String lineRefValue = null;

        if (SiriDataType.STOP_MONITORING.equals(discoveryParams.getDiscoveryType())) {
            reqType = RequestType.GET_STOP_MONITORING;
            mappingAdapter = "okina_sm";
            stopMonitoringValue = value;
        } else if (SiriDataType.VEHICLE_MONITORING.equals(discoveryParams.getDiscoveryType())) {
            reqType = RequestType.GET_VEHICLE_MONITORING;
            mappingAdapter = "okina_vm";
            lineRefValue = value;
        } else {
            reqType = RequestType.GET_ESTIMATED_TIMETABLE;
            mappingAdapter = "okina_et";
            lineRefValue = value;
        }

        urlMap.put(reqType, discoveryParams.getUrl());
        newSubscription.setUrlMap(urlMap);
        newSubscription.setCustomHeaders(discoveryParams.getCustomHeaders());
        newSubscription.setVersion("2.0");
        newSubscription.initConsumerAdressFromParent(discoveryParams.getVendorBaseName(), discoveryParams.getSubscriptionIdBase());
        newSubscription.setContentType("text/xml;charset=UTF-8");
        newSubscription.setSubscriptionId(type + "-" + discoveryParams.getSubscriptionIdBase() + "-" + currentSubcrtiptionNb);
        newSubscription.setRequestorRef(discoveryParams.getRequestorRef());
        newSubscription.setDurationOfSubscriptionHours(discoveryParams.getDurationOfSubscriptionHours());
        newSubscription.setMappingAdapterId(mappingAdapter);
        List<String> idMappingPrefixes = new ArrayList<>();
        idMappingPrefixes.add(discoveryParams.getDatasetId());
        newSubscription.setIdMappingPrefixes(idMappingPrefixes);
        newSubscription.setRestartTime(discoveryParams.getRestartTime());
        newSubscription.getStopMonitoringRefValues().add(stopMonitoringValue);
        newSubscription.getLineRefValues().add(lineRefValue);
        newSubscription.setActive(true);
        return newSubscription;
    }

    private Optional<DiscoverySubscription> findDiscoveryParam(String originalUrl, SiriDataType discoveryType) {

        if (subscriptionConfig.getDiscoverySubscriptions() == null || subscriptionConfig.getDiscoverySubscriptions().isEmpty()) {
            return Optional.empty();
        }

        for (DiscoverySubscription discoverySubscription : subscriptionConfig.getDiscoverySubscriptions()) {

            if (discoverySubscription.getUrl().equals(originalUrl) && discoveryType.equals(discoverySubscription.getDiscoveryType())) {
                return Optional.of(discoverySubscription);
            }
        }
        return Optional.empty();
    }

    private String getSubscriptionTypePrefix(SiriDataType siriDataType) {
        if (siriDataType.equals(SiriDataType.VEHICLE_MONITORING)) {
            return "VM";
        } else if (siriDataType.equals(SiriDataType.STOP_MONITORING)) {
            return "SM";
        } else {
            return "ET";
        }
    }

    private void createSubscriptions(DiscoverySubscription discoverySubscription) {

        if (discoverySubscription.getDiscoveryType() == null) {
            logger.error("Unable to create subscriptions because discoveryType is not specified for url: {}", discoverySubscription.getUrl());
            return;
        }

        discoveryRequestProducer.asyncRequestBody(discoveryRequestProducer.getDefaultEndpoint(), discoverySubscription);
    }


}
