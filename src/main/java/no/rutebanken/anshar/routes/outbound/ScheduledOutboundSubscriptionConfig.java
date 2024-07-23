package no.rutebanken.anshar.routes.outbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.Siri;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class ScheduledOutboundSubscriptionConfig {

    @Autowired
    private CamelRouteManager camelRouteManager;

    private final ThreadPoolTaskScheduler scheduler;
    private final Map<String, ScheduledTaskWrapper> scheduledTasks = new HashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(ScheduledOutboundSubscriptionConfig.class);

    public ScheduledOutboundSubscriptionConfig() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.initialize();
    }

    public void createScheduledOutboundSubscription(Siri payload, OutboundSubscriptionSetup outboundSubscriptionSetup) {
        String subscriptionId = outboundSubscriptionSetup.getSubscriptionId();
        ScheduledTaskWrapper taskWrapper = scheduledTasks.get(subscriptionId);

        if (taskWrapper != null) {
            updateTaskWithLastData(taskWrapper, payload);
            logger.info("Scheduled outbound subscription with ID " + subscriptionId + " already exists, just update data to send");
            return;
        }

        taskWrapper = new ScheduledTaskWrapper(outboundSubscriptionSetup, payload);
        scheduledTasks.put(subscriptionId, taskWrapper);

        PeriodicTrigger trigger = new PeriodicTrigger(outboundSubscriptionSetup.getUpdateInterval(), TimeUnit.MILLISECONDS);
        trigger.setFixedRate(true);

        ScheduledFuture<?> scheduledTask = scheduler.schedule(taskWrapper, trigger);
        taskWrapper.setScheduledFuture(scheduledTask);
    }

    private void updateTaskWithLastData(ScheduledTaskWrapper taskWrapper, Siri payload) {
        taskWrapper.setPayload(payload);
    }

    public class ScheduledTaskWrapper implements Runnable {
        private final OutboundSubscriptionSetup outboundSubscriptionSetup;
        private Siri payload;
        private ScheduledFuture<?> scheduledFuture;

        public ScheduledTaskWrapper(OutboundSubscriptionSetup outboundSubscriptionSetup, Siri payload) {
            this.outboundSubscriptionSetup = outboundSubscriptionSetup;
            this.payload = payload;
        }

        @Override
        public void run() {
            camelRouteManager.postDataToSubscription(payload, outboundSubscriptionSetup, false);
        }

        public void setPayload(Siri payload) {
            this.payload = payload;
        }

        public void setScheduledFuture(ScheduledFuture<?> scheduledFuture) {
            this.scheduledFuture = scheduledFuture;
        }
    }
}



