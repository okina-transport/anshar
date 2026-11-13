package no.rutebanken.anshar.okinaDisruptions;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DisruptionRouteBuilder extends BaseRouteBuilder {

    private static final int INTERVAL_IN_MILLIS = 60000;

    @Value("${mobi.iti.disruption.api.url:}")
    private String okinaDisruptionAPIUrl;

    protected DisruptionRouteBuilder(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {


        if (StringUtils.isEmpty(okinaDisruptionAPIUrl)){
            log.error("Aucune url d'API pour récupérer les disruptions n'a été définié");
            return ;
        }

        if (!config.processSX()){
            log.error("Application non paramétrée en mode SX. pas de récupération de perturbations");
            return ;
        }


        singletonFrom("quartz://anshar/retrieve_disruptions?trigger.repeatInterval=" + INTERVAL_IN_MILLIS,
                "retrieveDisruptionsFromOkinaDB")
                .bean(DisruptionRetriever.class, "retrieveDisruptions")
                .end();


    }


}
