package no.rutebanken.anshar.routes;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.DiscoverySubscription;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.subscription.DiscoverySubscriptionCreator;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.http.HttpMethods;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.*;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static no.rutebanken.anshar.subscription.DiscoverySubscriptionCreator.*;
import static no.rutebanken.anshar.subscription.SubscriptionConstants.DISCOVERY_SUBSCRIPTION_SOAP_TRANSFORMATION;

@Component
public class DiscoverySubscriptionsRouteBuilder extends BaseRouteBuilder {

    //Every 24 hours
    private static final int INTERVAL_IN_MILLIS = 86_400_000;

    private static final Logger logger = LoggerFactory.getLogger(DiscoverySubscriptionsRouteBuilder.class);
    public static final String SEND_DISCOVERY_REQUEST_PREPROCESS_ROUTE = "direct:send.discovery.request.preprocess";
    public static final String SEND_DISCOVERY_REQUEST_ROUTE = "direct:send.discovery.request";

    private final AnsharConfiguration configuration;

    NamespacePrefixMapper customNamespacePrefixMapper;

    protected DiscoverySubscriptionsRouteBuilder(AnsharConfiguration config, SubscriptionManager subscriptionManager, AnsharConfiguration configuration) {
        super(config, subscriptionManager);
        this.configuration = configuration;
        this.customNamespacePrefixMapper = new NamespacePrefixMapper() {
            @Override
            public String getPreferredPrefix(String arg0, String arg1, boolean arg2) {
                return "siri";
            }
        };
    }

    @Override
    public void configure() throws Exception {

        if ((!configuration.processSM() && !configuration.processVM()) || !configuration.isCurrentInstanceLeader()) {
            logger.info("Application non paramétrée en SM/VM ou instance non leader. Pas de création d'abonnement à partir des url discovery");
            return;
        }


        //1er lancement au démarrage de l'appli
        singletonFrom("quartz://anshar/create_discovery_subscriptions_first_launch?trigger.repeatInterval=1&trigger.repeatCount=0",
                "create_discovery_subscriptions_first_launch")
                .log("Subscriptions by discovery launched")
                .bean(DiscoverySubscriptionCreator.class, "createDiscoverySubscriptions")
                .end();

        //Lancement suivants toutes les 24 heures
        singletonFrom("quartz://anshar/create_discovery_subscriptions?trigger.repeatInterval=" + INTERVAL_IN_MILLIS,
                "create_discovery_subscriptions")
                .log("Subscriptions by discovery launched")
                .bean(DiscoverySubscriptionCreator.class, "createDiscoverySubscriptions")
                .end();

        from(SEND_DISCOVERY_REQUEST_PREPROCESS_ROUTE)
                .process(e -> {
                    e.getIn().getHeaders().putAll(createDiscoveryHeaders(e.getIn().getBody(DiscoverySubscription.class)));
                    e.getIn().setBody(createDiscoveryRequest(e.getIn().getBody(DiscoverySubscription.class)));
                })
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat(customNamespacePrefixMapper))
                .choice().when(header(DISCOVERY_SUBSCRIPTION_SOAP_TRANSFORMATION).isEqualTo(true))
                    .to("xslt-saxon:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .end()
                .setHeader("Content-type", constant("text/xml"))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .end();

        from(SEND_DISCOVERY_REQUEST_ROUTE)
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .to(SEND_DISCOVERY_REQUEST_PREPROCESS_ROUTE)
                .toD("${header.endpointUrl}")
                .choice().when(simple("${in.body} != null"))
                    .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true&level=DEBUG")
                    .choice()
                        .when(header(DISCOVERY_SUBSCRIPTION_SOAP_TRANSFORMATION).isEqualTo(true))
                        .to("xslt-saxon:xsl/siri_soap_raw.xsl?allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Extract SOAP version and convert to raw SIRI
                    .end()
                .bean(DiscoverySubscriptionCreator.class, "createSubscriptionsFromProviderResponse")
                .end();
    }


    public static Map<String, Object> createDiscoveryHeaders(DiscoverySubscription discoverySubscription) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(SOAP_ACTION_HEADER, convertDataTypeToSoapAction(discoverySubscription.getDiscoveryType()));
        headers.put(ENDPOINT_URL_HEADER, discoverySubscription.getUrl());
        headers.put(SUBSCRIPTION_URL_HEADER, discoverySubscription.getUrl());
        headers.put("Content-type", "text/xml");
        if (MapUtils.isNotEmpty(discoverySubscription.getCustomHeaders())) {
            headers.putAll(discoverySubscription.getCustomHeaders());
        }
        return headers;
    }

    public static Siri createDiscoveryRequest(DiscoverySubscription discoverySubscription) {
        Siri siriRequest = new Siri();
        MessageQualifierStructure messageId = new MessageQualifierStructure();
        String msgId = UUID.randomUUID().toString();
        messageId.setValue(msgId);
        RequestorRef requestorRef = new RequestorRef();
        requestorRef.setValue(discoverySubscription.getRequestorRef());

        logger.info("Creating discovery request for url :{}, type:{}, messageId:{}", discoverySubscription.getUrl(), discoverySubscription.getDiscoveryType(), msgId);

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

    private static String convertDataTypeToSoapAction(SiriDataType dataType) {
        switch (dataType) {
            case STOP_MONITORING:
                return "StopPointsDiscovery";
            case VEHICLE_MONITORING:
                return "LinesDiscovery";
            default:
                return "can't convert to soap action datatype:" + dataType;
        }
    }

}
