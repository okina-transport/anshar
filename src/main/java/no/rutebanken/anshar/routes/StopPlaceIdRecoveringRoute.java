package no.rutebanken.anshar.routes;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.StopPlaceIdRetriever;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Class used to connect to TIAMAT and recover id from theorical offer
 *
 */
@Component
public class StopPlaceIdRecoveringRoute extends BaseRouteBuilder{

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final int INTERVAL_IN_MILLIS = 300000;

    @Value("${stop-places.api.url}")
    private String stopPlaceApiURL;


    protected StopPlaceIdRecoveringRoute(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

//        if (stopPlaceApiURL.isEmpty()) {
//            logger.info("Pas d'url API StopPlace définie");
//        } else {
//            singletonFrom("quartz://anshar/stopPlaceIdRecovering?trigger.repeatInterval=" + INTERVAL_IN_MILLIS,
//                    "stopPlaceIdRecovering")
//                    .bean(StopPlaceIdRetriever.class, "getStopPlaceIds")
//                    .end();
//
//        }

    }
}
