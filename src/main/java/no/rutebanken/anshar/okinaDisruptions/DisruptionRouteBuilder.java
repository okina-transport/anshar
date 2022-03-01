package no.rutebanken.anshar.okinaDisruptions;

import io.micrometer.core.instrument.util.StringUtils;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DisruptionRouteBuilder extends BaseRouteBuilder {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final int INTERVAL_IN_MILLIS = 60000;

    @Value("${mobi.iti.disruption.api.url:}")
    private String okinaDisruptionAPIUrl;

    protected DisruptionRouteBuilder(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {


        if (StringUtils.isEmpty(okinaDisruptionAPIUrl)){
            logger.error("Aucune url d'API pour récupérer les disruptions n'a été définié");
            return ;
        }


        singletonFrom("quartz://anshar/retrieve_disruptions?fireNow=true&trigger.repeatInterval=" + INTERVAL_IN_MILLIS,
                "retrieveDisruptionsFromOkinaDB")
                .bean(DisruptionRetriever.class, "retrieveDisruptions")
                .end();


    }


}
