package no.rutebanken.anshar.routes.siri;

import com.hazelcast.replicatedmap.ReplicatedMap;
import no.rutebanken.anshar.routes.health.LivenessReadinessRoute;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Configuration
public class SubscriptionStatusChecker extends RouteBuilder {

    @Autowired
    @Qualifier("getSubscriptionsMap")
    private ReplicatedMap<String, SubscriptionSetup> subscriptions;

    @Autowired
    private LivenessReadinessRoute livenessReadinessRoute;

    @Value("${interval.check.auto.subscription.restart}")
    private String interval;

    private final Map<String, ZonedDateTime> urlRestartDates = new ConcurrentHashMap<>();

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Override
    public void configure() throws Exception {
        from("quartz://anshar/statusChecker?trigger.repeatInterval=" + interval)
                .routeId("subscription-status-checker")
                .process(exchange -> {
                    urlRestartDates.clear();
                    checkSubscriptionStatuses();
                });

        from("direct:recordRequest")
                .routeId("subscription-record-request")
                .process(subscriptionManager::recordRequest);

        from("direct:recordResponse")
                .routeId("subscription-record-response")
                .process(subscriptionManager::recordResponse);


    }

    private void checkSubscriptionStatuses() {
        Map<String, SubscriptionSetup> filteredSubscriptions = subscriptions.entrySet().stream()
                .filter(entry -> !"GTFS-RT".equals(entry.getValue().getContentType()) && !SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE.equals(entry.getValue().getSubscriptionMode()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<Collection<String>, SubscriptionSetup> uniqueUrls = filteredSubscriptions.values().stream()
                .filter(sub -> sub.getUrlMap() != null && !sub.getUrlMap().isEmpty())
                .collect(Collectors.toMap(
                        sub -> new HashSet<>(sub.getUrlMap().values()),
                        sub -> sub,
                        (existing, replacement) -> existing
                ));

        uniqueUrls.forEach((urls, provider) -> {
            urls.forEach((url) -> {
                ZonedDateTime restartDate = performStatusCheck(url, List.of(provider));
                if (restartDate != null) {
                    urlRestartDates.put(url, restartDate);
                }
            });
        });

        List<SubscriptionSetup> subscriptionsToRestart = new ArrayList<>();
        filteredSubscriptions.entrySet().forEach(entry -> {
            String subscriptionId = entry.getKey();
            SubscriptionSetup subscription = entry.getValue();
            ZonedDateTime subscriptionStartDate = subscription.getStartedAt();

            if (subscriptionStartDate == null) {
                return;
            }

            Map<RequestType, String> urlMap = subscription.getUrlMap();
            if (urlMap != null && !urlMap.isEmpty()) {
                boolean needsRestart = urlMap.values().stream()
                        .filter(url -> url != null && urlRestartDates.containsKey(url))
                        .anyMatch(url -> subscriptionStartDate.isBefore(urlRestartDates.get(url)));

                if (needsRestart) {
                    subscriptionsToRestart.add(entry.getValue());
                    log.info("Subscription {} needs restart (started at {}). At least one URL was restarted after subscription start.",
                            subscriptionId, subscriptionStartDate);
                }
            }
        });
        subscriptionManager.launchTerminateRequest(subscriptionsToRestart);
        subscriptionManager.launchSubscriptionRequest(subscriptionsToRestart);
    }

    private ZonedDateTime performStatusCheck(String url, List<SubscriptionSetup> subscriptions) {
        try {

            return livenessReadinessRoute.getServerStartDate(subscriptions.getFirst());
        } catch (Exception e) {
            log.warn("Status check failed for {}: {}", url, e.getMessage());
            return null;
        }
    }
}
