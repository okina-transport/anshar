package no.rutebanken.anshar.routes;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.subscription.DiscoverySubscriptionCreator;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.http.HttpMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DiscoverySubscriptionsRouteBuilder extends BaseRouteBuilder {

    //Every 24 hours
    private static final int INTERVAL_IN_MILLIS = 86_400_000;

    private static final Logger logger = LoggerFactory.getLogger(DiscoverySubscriptionsRouteBuilder.class);

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private AnsharConfiguration configuration;

    NamespacePrefixMapper customNamespacePrefixMapper;

    protected DiscoverySubscriptionsRouteBuilder(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
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


        if (subscriptionConfig.getDiscoverySubscriptions().size() > 0) {

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
        } else {
            logger.info("Pas d'url stop discovery définie");
        }

        from("direct:send.discovery.request")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat(customNamespacePrefixMapper))
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
//                .process(e -> {
//                    logger.info(e.getIn().getBody(String.class));
//                })
                .to("xslt-saxon:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .setHeader("Content-type", constant("text/xml"))
//                .process(e -> {
//                    logger.info(e.getIn().getBody(String.class));
//                })
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .toD("${header.endpointUrl}")
                .choice().when(simple("${in.body} != null"))
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true&level=DEBUG")
                .to("xslt-saxon:xsl/siri_soap_raw.xsl?allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Extract SOAP version and convert to raw SIRI
                .bean(DiscoverySubscriptionCreator.class, "createSubscriptionsFromProviderResponse")

                .end()


        ;
    }
}
