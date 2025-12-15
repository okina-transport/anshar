package no.rutebanken.anshar.routes.siri.theoretical;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TheoreticalSiriSmRoute extends BaseRouteBuilder {

    protected TheoreticalSiriSmRoute(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {
        if (!config.isSiriGenerationFromTheoreticalDataEnabled()) {
            log.info("Application not configured to generate Siri SM from theoretical data");
            return;
        }

        if (!config.processSM()) {
            log.info("Application not configured to handle Siri SM data");
            return;
        }

        singletonFrom("quartz://anshar/ingest_th_sm_data?cron=" + config.getSiriGenerationFromTheoreticalDataCron(), "ingest_th_sm_data")
                .bean(TheoreticalSiriSmConsumer.class, "ingestSiriSmData")
                .end();

        singletonFrom("quartz://anshar/ingest_th_sm_data_first_launch?trigger.repeatInterval=1&trigger.repeatCount=0",
                "ingest_th_sm_data_first_launch")
                .bean(TheoreticalSiriSmConsumer.class, "ingestSiriSmData")
                .end();
    }
}
