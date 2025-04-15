package no.rutebanken.anshar.routes;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProxyInstanceLeaderCheckRouteBuilder extends BaseRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ProxyInstanceLeaderCheckRouteBuilder.class);

    protected ProxyInstanceLeaderCheckRouteBuilder(AnsharConfiguration configuration, SubscriptionManager subscriptionManager) {
        super(configuration, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

        if (CollectionUtils.isEmpty(config.getAppModes())) {
            logger.info("Anshar - no cluster mode - skipping leader check");
            return;
        }

        if (!config.processAdmin()) {
            logger.info("Specialized instance - skipping proxy synchronization");
            return;
        }

        singletonFrom("quartz://anshar/check_leader_instance?trigger.repeatInterval=" + config.getLeaderCheckIntervalMs(),
                "check_leader_instance")
                .log("Checking leader instance")
                .bean(AnsharConfiguration.class, "isCurrentInstanceLeader")
                .end();

    }
}
