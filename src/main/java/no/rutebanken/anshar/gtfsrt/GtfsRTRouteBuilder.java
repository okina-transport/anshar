package no.rutebanken.anshar.gtfsrt;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GtfsRTRouteBuilder extends BaseRouteBuilder{

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final int INTERVAL_IN_MILLIS = 60000;

    @Value("${anshar.gtfs.rt.url.list}")
    private String gtfsRTUrls;

    protected GtfsRTRouteBuilder(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

        if (StringUtils.isNotEmpty(gtfsRTUrls)){
            singletonFrom("quartz://anshar/import_GTFSRT_DATA?fireNow=true&trigger.repeatInterval=" + INTERVAL_IN_MILLIS,
                    "import_GTFSRT_DATA")
                    .bean(GtfsRTDataRetriever.class,"getGTFSRTData")
                    .end();
        }else{
            logger.info("Pas d'url GTFS-RT définie");
        }

    }
}
