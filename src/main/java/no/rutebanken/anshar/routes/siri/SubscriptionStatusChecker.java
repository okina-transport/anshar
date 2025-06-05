package no.rutebanken.anshar.routes.siri;

import com.hazelcast.replicatedmap.ReplicatedMap;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Configuration
public class SubscriptionStatusChecker extends RouteBuilder {

    @Autowired
    @Qualifier("getSubscriptionsMap")
    private ReplicatedMap<String, SubscriptionSetup> subscriptions;

    @Value("${interval.check.auto.subscription.restart}")
    private String interval;

    private final Map<String, ZonedDateTime> urlRestartDates = new ConcurrentHashMap<>();

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Override
    public void configure() throws Exception {
        from("quartz://anshar/statusChecker?trigger.repeatInterval="+ interval)
                .routeId("subscription-status-checker")
                .process(exchange -> {
                    urlRestartDates.clear();
                    checkSubscriptionStatuses();
                });
    }

    private void checkSubscriptionStatuses() {
        Set<String> uniqueUrls = new HashSet<>();
        subscriptions.values().forEach(subscription -> {
            Map<RequestType, String> urlMap = subscription.getUrlMap();
            if (urlMap != null && !urlMap.isEmpty()) {
                uniqueUrls.addAll(urlMap.values());
            }
        });

        uniqueUrls.forEach(url -> {
            ZonedDateTime restartDate = performStatusCheck(url);
            if (restartDate != null) {
                urlRestartDates.put(url, restartDate);
            }
        });

        subscriptions.entrySet().forEach(entry -> {
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
                    subscriptionManager.forceRestart(subscription.getSubscriptionId());
                    log.info("Subscription {} needs restart (started at {}). At least one URL was restarted after subscription start.",
                            subscriptionId, subscriptionStartDate);
                }
            }
        });
    }

    private ZonedDateTime performStatusCheck(String url) {
        try {
            HttpResponse<Void> response = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder()
                                    .uri(URI.create(url))
                                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                                    .timeout(Duration.ofSeconds(5))
                                    .build(),
                            HttpResponse.BodyHandlers.discarding());

            if (!(response.statusCode() >= 200 && response.statusCode() < 500)) {
                return response.headers()
                        .firstValue("X-Service-Restart")
                        .map(ZonedDateTime::parse)
                        .orElse(ZonedDateTime.now());
            }
            return null;
        } catch (Exception e) {
            log.warn("Status check failed for {}: {}", url, e.getMessage());
            return null;
        }
    }
}
