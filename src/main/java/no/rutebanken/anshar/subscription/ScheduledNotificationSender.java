package no.rutebanken.anshar.subscription;

import com.hazelcast.map.IMap;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.outbound.CamelRouteManager;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.outbound.StopMonitoringOutbound;
import no.rutebanken.anshar.util.SiriUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class ScheduledNotificationSender extends BaseRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledNotificationSender.class);

    @Value("${anshar.scheduledNotification.sender.interval:120 000}")
    private int scheduledNotificationInterval;


    @Value("${anshar.scheduledNotification.enabled:false}")
    private boolean scheduledNotificationEnabled;

    @Autowired
    ServerSubscriptionManager outboundSubscriptionManager;


    @Autowired
    private StopMonitoringOutbound stopMonitoringOutbound;

    @Autowired
    ExtendedHazelcastService hazelcastService;

    @Autowired
    private CamelRouteManager camelRouteManager;

    @Value("${outbound.scheduled.already.sent.cache.time.hours:5}")
    private int scheduledAlreadySentCacheTimeHours;

    protected ScheduledNotificationSender(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {
        if (!scheduledNotificationEnabled) {
            logger.info("Scheduled notifications are disabled");
            return;
        }

        singletonFrom("quartz://anshar/send_scheduled_notification?trigger.repeatInterval=" + scheduledNotificationInterval, "send_scheduled_notification")
                .bean(this, "sendScheduledNotification")
                .end();
    }


    public void sendScheduledNotification() throws InterruptedException {
        logger.info("Starting sending scheduled notifications");
        List<OutboundSubscriptionSetup> smSubscriptions = outboundSubscriptionManager.getAllSubscriptions(SiriDataType.STOP_MONITORING);

        List<OutboundSubscriptionSetup> outboundSubscriptionWithPreviewInterval = smSubscriptions.stream()
                .filter(smSub -> smSub.getPreviewInterval() != null)
                .collect(Collectors.toList());

        outboundSubscriptionWithPreviewInterval.forEach(this::sendScheduledNotificationForSubscription);
        logger.info("scheduled notifications sent");
    }


    /**
     * Send a scheduled notification to an outbound client
     * - read outbound subscription
     * - request cache to get data linked to outbound subscription
     * - filter data that has already been sent
     * - send data to client
     *
     * @param outboundSubscriptionSetup outbound subscription that contains parameters
     */
    private void sendScheduledNotificationForSubscription(OutboundSubscriptionSetup outboundSubscriptionSetup) {
        Set<String> stopsToSearch = outboundSubscriptionSetup.getFilterMap().get(MonitoringRefStructure.class);
        Map<String, Siri> deliveriesToSend = stopMonitoringOutbound.getScheduledDeliveryToSend(stopsToSearch);

        for (Map.Entry<String, Siri> entry : deliveriesToSend.entrySet()) {
            Siri delivery = entry.getValue();
            delivery = SiriUtils.filterStopMonitoringOnPreviewInterval(delivery, outboundSubscriptionSetup);
            delivery = filterAlreadySendMessages(delivery, outboundSubscriptionSetup);
            camelRouteManager.pushSiriData(entry.getKey(), delivery, outboundSubscriptionSetup, true, null);
        }
    }

    /**
     * Read a siri delivery and filter notifications that has already sent
     *
     * @param delivery                  the delivery in which notifications must be filtered
     * @param outboundSubscriptionSetup outbound subscription
     * @return the delivery, with filtered notifications
     */
    private Siri filterAlreadySendMessages(Siri delivery, OutboundSubscriptionSetup outboundSubscriptionSetup) {
        if (delivery.getServiceDelivery().getStopMonitoringDeliveries() == null) {
            return delivery;
        }

        IMap<String, String> alreadySent = hazelcastService.getScheduledAlreadySentSM();
        String subId = outboundSubscriptionSetup.getSubscriptionId();
        List<StopMonitoringDeliveryStructure> smDeliveries = delivery.getServiceDelivery().getStopMonitoringDeliveries();
        for (StopMonitoringDeliveryStructure smDelivery : smDeliveries) {
            Map<String, MonitoredStopVisit> byKey = new LinkedHashMap<>();
            for (MonitoredStopVisit msv : smDelivery.getMonitoredStopVisits()) {
                MonitoredVehicleJourneyStructure vj = msv.getMonitoredVehicleJourney();
                if (vj != null && vj.getMonitoredCall() != null) {
                    byKey.put(subId + "|" + buildNotificationId(msv), msv);
                }
            }
            Set<String> sent = alreadySent.getAll(byKey.keySet()).keySet();
            List<MonitoredStopVisit> kept = new ArrayList<>();
            byKey.forEach((k, msv) -> {
                if (!sent.contains(k)) {
                    kept.add(msv);
                    alreadySent.setAsync(k, "1", scheduledAlreadySentCacheTimeHours, TimeUnit.HOURS);
                }
            });
            smDelivery.getMonitoredStopVisits().clear();
            smDelivery.getMonitoredStopVisits().addAll(kept);
        }

        return delivery;
    }


    /**
     * Build a unique notification id based on itemIdentifier and expected times
     * (if expected times changes, notificationId must change)
     *
     * @param stopVisit notification for which an id must be
     * @return a notification Id
     */
    public String buildNotificationId(MonitoredStopVisit stopVisit) {
        MonitoredVehicleJourneyStructure vehicleJourney = stopVisit.getMonitoredVehicleJourney();
        return stopVisit.getItemIdentifier() + "-" + vehicleJourney.getMonitoredCall().getExpectedArrivalTime() + "-" + vehicleJourney.getMonitoredCall().getExpectedDepartureTime();
    }

}
