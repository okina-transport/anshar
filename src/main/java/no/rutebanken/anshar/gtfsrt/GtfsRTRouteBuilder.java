package no.rutebanken.anshar.gtfsrt;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GtfsRTRouteBuilder extends BaseRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(GtfsRTRouteBuilder.class);


    @Value("${anshar.gtfs.interval.millis:120000}")
    private int gtfsIntervalInMillis;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private AnsharConfiguration configuration;

    protected GtfsRTRouteBuilder(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

        if ((!configuration.processSX() && !configuration.processVM() && !configuration.processSM() && !configuration.processET()) || !configuration.isCurrentInstanceLeader()) {
            logger.info("Application non paramétrée en SM/SX/ET/VM ou instance non leader. Pas de récupération GTFS-RT");
            return;
        }


        singletonFrom("quartz://anshar/import_GTFSRT_DATA?trigger.repeatInterval=" + gtfsIntervalInMillis, "import_GTFSRT_DATA")
                .bean(GtfsRTDataRetriever.class, "getGTFSRTData")
                .end();

    }
}
