package no.rutebanken.anshar.gtfsrt;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GtfsRTRouteBuilder extends BaseRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(GtfsRTRouteBuilder.class);

    private static final int INTERVAL_IN_MILLIS = 60000;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private AnsharConfiguration configuration;

    protected GtfsRTRouteBuilder(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

        if ((!configuration.processSX() && !configuration.processVM() && !configuration.processSM() && !configuration.processET()) || !configuration.isCurrentInstanceLeader()){
            logger.info("Application non paramétrée en SM/SX/ET/VM ou instance non leader. Pas de récupération GTFS-RT");
            return;
        }

        if (subscriptionConfig.getGtfsRTApis().size() > 0) {
            singletonFrom("quartz://anshar/import_GTFSRT_DATA?trigger.repeatInterval=" + INTERVAL_IN_MILLIS, "import_GTFSRT_DATA")
                    .bean(GtfsRTDataRetriever.class, "getGTFSRTData")
                    .end();
        } else {
            logger.info("Pas d'url GTFS-RT définie");
        }
    }
}
