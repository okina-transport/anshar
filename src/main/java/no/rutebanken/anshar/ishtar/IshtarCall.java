package no.rutebanken.anshar.ishtar;

import io.micrometer.core.instrument.util.StringUtils;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.DiscoverySubscriptionCreator;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionInitializer;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IshtarCall extends BaseRouteBuilder {

    @Autowired
    private IshtarSynchronizeProcessor ishtarSynchronizeProcessor;
    @Value("${ishtar.interval.millis:180000}")
    private int INTERVAL_IN_MILLIS_ISHTAR;
    @Value("${ishtar.server.url}")
    private String ishtarUrl;
    @Autowired
    private SubscriptionConfig subscriptionConfig;
    @Autowired
    private DiscoverySubscriptionCreator discoverySubscriptionCreator;

    protected IshtarCall(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

        if (StringUtils.isEmpty(ishtarUrl)) {
            return;
        }

        singletonFrom("quartz://anshar/autoGetAllDataFromIshtar?trigger.repeatInterval=" + INTERVAL_IN_MILLIS_ISHTAR,
                "autoGetAllDataFromIshtar")
                .bean(this, "callDataFromIshtar")
                .end();

        from("direct:startDataFetch")
                .routeId("startDataFetch")
                .removeHeaders("*")
                .log(LoggingLevel.INFO, "--> ISHTAR : start synchronize data")
                .to("direct:getAllDataFromIshtar")
                .end();

        from("direct:getAllDataFromIshtar")
                .routeId("getAllDataFromIshtar")
                .onException(Exception.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                .setBody(simple("Error during data synchronization : ${exception.message}"))
                .end()
                .process(ishtarSynchronizeProcessor)
                .bean(SubscriptionInitializer.class, "createSubscriptions")
                .end();
    }

    public void callDataFromIshtar() {
        getContext().createProducerTemplate().sendBody("direct:getAllDataFromIshtar", null);
    }

}
