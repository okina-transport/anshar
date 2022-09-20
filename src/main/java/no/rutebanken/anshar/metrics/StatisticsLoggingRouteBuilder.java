package no.rutebanken.anshar.metrics;


import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.springframework.stereotype.Component;

@Component
public class StatisticsLoggingRouteBuilder extends BaseRouteBuilder {


    private static final int INTERVAL_IN_MILLIS = 7200000;


    protected StatisticsLoggingRouteBuilder(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

        singletonFrom("quartz://anshar/statisticsLogger?trigger.repeatInterval=" + INTERVAL_IN_MILLIS,
                "statisticsLogger")
                .bean(StatisticsLogger.class, "writeLogs")
                .end();


    }


}
