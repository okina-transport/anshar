package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.DiscoverySubscription;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import no.rutebanken.anshar.util.IDUtils;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class DiscoverySubscriptionCreator {
    private static final Logger logger = LoggerFactory.getLogger(DiscoverySubscriptionCreator.class);


    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private SubscriptionInitializer subscriptionInitializer;

    @Produce(uri = "direct:send.discovery.request")
    protected ProducerTemplate discoveryRequestProducer;

    private static final String ENDPOINT_URL_HEADER = "endpointUrl";
    private static final String SOAP_ACTION_HEADER = "SOAPAction";


    public void createDiscoverySubscriptions() throws IOException {
        logger.info("Starting subscription creation from discovery");
        for (DiscoverySubscription stopDiscoverySubscription : subscriptionConfig.getDiscoverySubscriptions()) {
            createSubscriptions(stopDiscoverySubscription);
        }
        logger.info("Subscription creations from discovery completed");
    }

    public void createSubscriptionsFromProviderResponse(Exchange e) throws IOException, XMLStreamException, JAXBException {
        InputStream body = e.getIn().getBody(InputStream.class);
        Siri incoming = SiriValueTransformer.parseXml(body);
        String originalUrl = (String) e.getIn().getHeader(ENDPOINT_URL_HEADER);
        String soapActionHeader = (String) e.getIn().getHeader(SOAP_ACTION_HEADER);
        SiriDataType discoveryType = convertSoapActionToDataType(soapActionHeader);
        Optional<DiscoverySubscription> discoverySubsOpt = findDiscoveryParam(originalUrl, discoveryType);

        if (discoverySubsOpt.isEmpty()) {
            logger.error("Unable to find subscription for url : {}, soapActionHeader:{}", originalUrl, soapActionHeader);
            return;
        }

        DiscoverySubscription discoveryParams = discoverySubsOpt.get();
        List<String> referenceList = new ArrayList<>();

        if (SiriDataType.STOP_MONITORING.equals(discoveryType)) {
            referenceList = incoming.getStopPointsDelivery().getAnnotatedStopPointReves().stream()
                    .map(pointStructure -> pointStructure.getStopPointRef().getValue())
                    .collect(Collectors.toList());
        } else if (SiriDataType.VEHICLE_MONITORING.equals(discoveryType)) {
            referenceList = incoming.getLinesDelivery().getAnnotatedLineReves().stream()
                    .map(annotatedLineRef -> annotatedLineRef.getLineRef().getValue())
                    .collect(Collectors.toList());
        }


        List<SubscriptionSetup> subscriptionsToStart = createSubscriptionsSetups(referenceList, discoveryParams);

        subscriptionConfig.getSubscriptions().addAll(subscriptionsToStart);
        subscriptionInitializer.createSubscriptions();
    }

    private List<SubscriptionSetup> createSubscriptionsSetups(List<String> referenceList, DiscoverySubscription discoveryParams) {
        return referenceList.stream()
                .map(reference -> createSubscriptionSetup(reference, discoveryParams))
                .collect(Collectors.toList());
    }

    private SubscriptionSetup createSubscriptionSetup(String value, DiscoverySubscription discoveryParams) {

        SubscriptionSetup newSubscription = new SubscriptionSetup();
        newSubscription.setDatasetId(discoveryParams.getDatasetId());
        newSubscription.setSubscriptionType(discoveryParams.getDiscoveryType());
        newSubscription.setName(value + " subscription");
        newSubscription.setVendor(buildVendor(discoveryParams, value));
        newSubscription.setServiceType(SubscriptionSetup.ServiceType.SOAP);
        newSubscription.setSubscriptionMode(discoveryParams.getSubscriptionMode());
        newSubscription.setHeartbeatIntervalSeconds(discoveryParams.getHeartbeatIntervalSeconds());
        newSubscription.setChangeBeforeUpdatesSeconds(discoveryParams.getChangeBeforeUpdatesSeconds());
        newSubscription.setUpdateIntervalSeconds(discoveryParams.getUpdateIntervalSeconds());
        newSubscription.setPreviewIntervalSeconds(discoveryParams.getPreviewIntervalSeconds());
        newSubscription.setOperatorNamespace("http://wsdl.siri.org.uk");
        newSubscription.setInternalId(IDUtils.getUniqueInternalIdForDiscoverySubscription());

        Map<RequestType, String> urlMap = new HashMap<>();
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
        } else {
            reqType = RequestType.GET_VEHICLE_MONITORING;
            mappingAdapter = "okina_vm";
            lineRefValue = value;
        }

        urlMap.put(reqType, discoveryParams.getUrl());
        newSubscription.setUrlMap(urlMap);
        newSubscription.setCustomHeaders(discoveryParams.getCustomHeaders());
        newSubscription.setVersion("2.0");
        newSubscription.setContentType("text/xml;charset=UTF-8");
        newSubscription.setSubscriptionId(buildSubscriptionId(discoveryParams, value));
        newSubscription.setRequestorRef(discoveryParams.getRequestorRef());
        newSubscription.setDurationOfSubscriptionHours(discoveryParams.getDurationOfSubscriptionHours());
        newSubscription.setMappingAdapterId(mappingAdapter);
        List<String> idMappingPrefixes = new ArrayList<>();
        idMappingPrefixes.add(discoveryParams.getDatasetId());
        newSubscription.setIdMappingPrefixes(idMappingPrefixes);
        newSubscription.setRestartTime("03:20");
        newSubscription.getStopMonitoringRefValues().add(stopMonitoringValue);
        newSubscription.getLineRefValues().add(lineRefValue);
        newSubscription.setActive(true);
        return newSubscription;
    }

    private String buildVendor(DiscoverySubscription discoveryParams, String value) {
        String type = SiriDataType.STOP_MONITORING.equals(discoveryParams.getDiscoveryType()) ? "SM" : "VM";
        return discoveryParams.getVendorBaseName() + extractValueFromRef(value) + "-" + type + "-SUB";
    }

    private String buildSubscriptionId(DiscoverySubscription discoveryParams, String value) {
        String type = SiriDataType.STOP_MONITORING.equals(discoveryParams.getDiscoveryType()) ? "SM" : "VM";
        return discoveryParams.getSubscriptionIdBase() + extractValueFromRef(value) + "-" + type + "-SUB";
    }

    private String extractValueFromRef(String value) {
        if (!value.contains(":") || value.split(":").length != 5) {
            return value;
        }
        return value.split(":")[3];
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

    private String convertDataTypeToSoapAction(SiriDataType dataType) {
        switch (dataType) {
            case STOP_MONITORING:
                return "StopPointsDiscovery";
            case VEHICLE_MONITORING:
                return "LinesDiscovery";
            default:
                return "can't convert to soap action datatype:" + dataType.toString();
        }
    }

    private SiriDataType convertSoapActionToDataType(String soapAction) {
        switch (soapAction) {
            case "LinesDiscovery":
                return SiriDataType.VEHICLE_MONITORING;
            default:
                return SiriDataType.STOP_MONITORING;
        }
    }


    private void createSubscriptions(DiscoverySubscription discoverySubscription) {

        if (discoverySubscription.getDiscoveryType() == null) {
            logger.error("Unable to create subscriptions because discoveryType is not specified for url:" + discoverySubscription.getUrl());
            return;
        }

        Map<String, Object> headers = new HashMap<>();
        headers.put(SOAP_ACTION_HEADER, convertDataTypeToSoapAction(discoverySubscription.getDiscoveryType()));
        headers.put(ENDPOINT_URL_HEADER, discoverySubscription.getUrl());
        headers.put("Content-type", "text/xml");

        Siri siriToSend = createDiscoveryRequest(discoverySubscription);

        discoveryRequestProducer.asyncRequestBodyAndHeaders(discoveryRequestProducer.getDefaultEndpoint(), siriToSend, headers);
    }

    private Siri createDiscoveryRequest(DiscoverySubscription discoverySubscription) {
        Siri siriRequest = new Siri();
        MessageQualifierStructure messageId = new MessageQualifierStructure();
        String msgId = UUID.randomUUID().toString();
        messageId.setValue(msgId);
        RequestorRef requestorRef = new RequestorRef();
        requestorRef.setValue(discoverySubscription.getRequestorRef());

        logger.info("Creating discovery request for url :{}, type:{}, messageId:{}", discoverySubscription.getUrl(), discoverySubscription.getDiscoveryType().toString(), msgId);

        if (SiriDataType.STOP_MONITORING.equals(discoverySubscription.getDiscoveryType())) {
            StopPointsRequest stopPointsRequest = new StopPointsRequest();
            stopPointsRequest.setRequestTimestamp(ZonedDateTime.now());
            stopPointsRequest.setMessageIdentifier(messageId);
            stopPointsRequest.setRequestorRef(requestorRef);
            siriRequest.setStopPointsRequest(stopPointsRequest);

        }

        if (SiriDataType.VEHICLE_MONITORING.equals(discoverySubscription.getDiscoveryType())) {
            LinesDiscoveryRequestStructure lineRequest = new LinesDiscoveryRequestStructure();
            lineRequest.setMessageIdentifier(messageId);
            lineRequest.setRequestTimestamp(ZonedDateTime.now());
            lineRequest.setRequestorRef(requestorRef);
            siriRequest.setLinesRequest(lineRequest);
        }
        return siriRequest;
    }


}
