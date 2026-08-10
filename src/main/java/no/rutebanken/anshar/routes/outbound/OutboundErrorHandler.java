package no.rutebanken.anshar.routes.outbound;

import com.hazelcast.collection.ISet;
import com.hazelcast.map.IMap;
import com.hazelcast.scheduledexecutor.DuplicateTaskException;
import com.hazelcast.scheduledexecutor.IScheduledExecutorService;
import com.hazelcast.scheduledexecutor.NamedTask;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import org.apache.camel.Exchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


import static no.rutebanken.anshar.routes.validation.validators.Constants.ENDPOINT_HEADER_NAME;


/**
 * Class to handle errors on outbound pushs, url banishment
 */

@Component
@Slf4j
public class OutboundErrorHandler {


    private static final String BAN_CHECK_TASK_NAME = "outboundErrorHandler-banUrlsExceedingLimit";

    private final IMap<String, Integer> outboundErrorCount;
    int maximumOutboundErrorsAllowed;
    private final ServerSubscriptionManager subscriptionManager;
    private final IScheduledExecutorService sharedScheduler;
    private final ISet<String> bannedUrls;

    public OutboundErrorHandler(@Qualifier("getOutboundErrorCount") IMap<String, Integer> outboundErrorCount,
                                @Value("${maximum.outbound.errors.allowed.by.url:2}") int maximumOutboundErrorsAllowed,
                                @Autowired ServerSubscriptionManager subscriptionManager,
                                @Qualifier("getSharedScheduler") IScheduledExecutorService sharedScheduler,
                                @Qualifier("getBannedUrls") ISet<String> bannedUrls) {
        this.outboundErrorCount = outboundErrorCount;
        this.maximumOutboundErrorsAllowed = maximumOutboundErrorsAllowed;
        this.subscriptionManager = subscriptionManager;
        this.sharedScheduler = sharedScheduler;
        this.bannedUrls = bannedUrls;
    }

    /**
     * Schedules the ban-check task on the Hazelcast member owning the task's key, so that
     * exactly one node of the cluster runs it, regardless of how many nodes call this at startup.
     * Waits for the Spring context to be fully refreshed, since the task may run immediately
     * (initial delay 0) and would otherwise find {@link ApplicationContextHolder} not yet set.
     */
    @PostConstruct
    public void scheduleBanCheck() {
        try {
            sharedScheduler.scheduleOnKeyOwnerAtFixedRate(this::banUrlsExceedingLimit, BAN_CHECK_TASK_NAME, 0, 5, TimeUnit.MINUTES);
        } catch (DuplicateTaskException e) {
            log.info("Ban-check task [{}] is already scheduled on the cluster", BAN_CHECK_TASK_NAME);
        }
    }

    /**
     * Method to declare a new error on post and eventualy launch an url banishment
     *
     * @param exchange Exchange that contains client's url
     * @throws Exception
     */
    public void recordError(Exchange exchange) throws Exception {

        String baseUrl = getBaseURLfromExchange(exchange);
        int errorCount = outboundErrorCount.getOrDefault(baseUrl, 0);
        errorCount++;

        log.debug("Outbound error push [{}/{}] for url : {}", errorCount, maximumOutboundErrorsAllowed, baseUrl);
        outboundErrorCount.put(baseUrl, errorCount);
    }

    /**
     * Method to ban a url.( after banishment, no subscription will be possible to this url)
     *
     * @param baseUrl url to ban
     */
    private void banUrl(String baseUrl) {
        log.info("Banning url : {} because of repeated push errors", baseUrl);
        bannedUrls.add(baseUrl);
        List<String> outboundSubscriptionsToRemove = subscriptionManager.getSubscriptionsWithBaseUrl(baseUrl);
        log.info("Removing subscriptions with error URL: {}", String.join(",", outboundSubscriptionsToRemove));
        outboundSubscriptionsToRemove.forEach(subscriptionToRemove -> subscriptionManager.terminateSubscription(subscriptionToRemove, false));

    }

    /**
     * Reset error count for an url
     *
     * @param exchange exchange that contains client's url
     * @throws URISyntaxException
     */
    public void resetCount(Exchange exchange) throws URISyntaxException {
        String baseUrl = getBaseURLfromExchange(exchange);
        resetCount(baseUrl);
    }

    /**
     * Reset error count for an url
     *
     * @param baseUrl url for which count must be reset
     */
    public void resetCount(String baseUrl) {
        outboundErrorCount.remove(baseUrl);
        bannedUrls.remove(baseUrl);
    }

    /**
     * Get the complete error counts for each url
     *
     * @return the map with error counts by url
     */
    public Map<String, Integer> getOutboundErrorCount() {
        return outboundErrorCount;
    }

    /**
     * Recover the client's url from an exchange
     *
     * @param exchange exchange that contains an endpoint url
     * @return the client's url
     * @throws URISyntaxException
     */
    private String getBaseURLfromExchange(Exchange exchange) throws URISyntaxException {
        String endpoint = (String) exchange.getIn().getHeader(ENDPOINT_HEADER_NAME);
        return getBaseURL(endpoint);
    }

    /**
     * Normalizes a url to scheme://host+path, stripping port and query string, so that
     * error-counting, banishment and ban-checking all key on the same value.
     *
     * @param url url to normalize
     * @return the normalized base url
     */
    private String getBaseURL(String url) throws URISyntaxException {
        URI uri = new URI(url);
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPath() != null ? uri.getPath() : "");
    }

    /**
     * Tells if a url is allowed or banished (error count > max)
     *
     * @param url url to check
     * @return true : url is allowed and client can subscribe with this url
     * false : url is banished. Subscription request will be refused
     */
    public boolean isUrlAllowed(String url) {
        try {
            String baseUrl = getBaseURL(url);
            return outboundErrorCount.getOrDefault(baseUrl, 0) < maximumOutboundErrorsAllowed;
        } catch (URISyntaxException e) {
            log.warn("Unable to parse url [{}] while checking if it is banished", url, e);
            return true;
        }
    }


    /**
     * Clear all error counts
     */
    @Scheduled(fixedRateString = "${outbound.errors.clearing.frequency:86400000}")
    public void clearOutboundErrors() {
        log.info("Clearing outbound errors");
        outboundErrorCount.clear();
        bannedUrls.clear();
    }

    /**
     * Bans every url whose error count has reached the allowed maximum and that isn't already banned.
     * scheduled on a single node of the cluster.
     */
    public void banUrlsExceedingLimit() {
        outboundErrorCount.forEach((url, errorCount) -> {
            if (errorCount >= maximumOutboundErrorsAllowed && !bannedUrls.contains(url)) {
                banUrl(url);
            }
        });
    }


}
