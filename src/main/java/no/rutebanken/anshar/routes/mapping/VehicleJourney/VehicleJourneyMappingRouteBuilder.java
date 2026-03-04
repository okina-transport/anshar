package no.rutebanken.anshar.routes.mapping.VehicleJourney;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.siri.theoretical.TheoreticalSiriSmConsumer;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class VehicleJourneyMappingRouteBuilder extends BaseRouteBuilder {

    protected VehicleJourneyMappingRouteBuilder(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {


        singletonFrom("quartz://anshar/vj_mapping_cache_reload?cron=" + config.getVjMappingCacheCron(), "vj_mapping_cache_reload")
                .bean(VehicleJourneyCache.class, "refill")
                .end();

        singletonFrom("quartz://anshar/vj_mapping_cache_reload_first_launch?trigger.repeatInterval=1&trigger.repeatCount=0",
                "vj_mapping_cache_reload_first_launch")
                .bean(VehicleJourneyCache.class, "refill")
                .end();

    }
}
