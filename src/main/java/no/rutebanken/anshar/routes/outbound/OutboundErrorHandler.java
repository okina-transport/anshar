package no.rutebanken.anshar.routes.outbound;

import com.hazelcast.map.IMap;
import lombok.extern.slf4j.Slf4j;
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


import static no.rutebanken.anshar.routes.validation.validators.Constants.ENDPOINT_HEADER_NAME;


/**
 * Class to handle errors on outbound pushs, url banishment
 */

@Component
@Slf4j
public class OutboundErrorHandler {


    private final IMap<String, Integer> outboundErrorCount;
    int maximumOutboundErrorsAllowed;
    private final ServerSubscriptionManager subscriptionManager;

    public OutboundErrorHandler(@Qualifier("getOutboundErrorCount") IMap<String, Integer> outboundErrorCount,
                                @Value("${maximum.outbound.errors.allowed.by.url:2}") int maximumOutboundErrorsAllowed,
                                @Autowired ServerSubscriptionManager subscriptionManager) {
        this.outboundErrorCount = outboundErrorCount;
        this.maximumOutboundErrorsAllowed = maximumOutboundErrorsAllowed;
        this.subscriptionManager = subscriptionManager;
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

        log.info("Outbound error push [{}/{}] for url : {}", errorCount, maximumOutboundErrorsAllowed, baseUrl);
        outboundErrorCount.put(baseUrl, errorCount);

        if (errorCount >= maximumOutboundErrorsAllowed) {
            banUrl(baseUrl);
        }
    }

    /**
     * Method to ban a url.( after banishment, no subscription will be possible to this url)
     *
     * @param baseUrl url to ban
     */
    private void banUrl(String baseUrl) {
        log.info("Banning url : {} because of repeated push errors", baseUrl);
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
        URI uri = new URI(endpoint);
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
        return outboundErrorCount.getOrDefault(url, 0) < maximumOutboundErrorsAllowed;
    }


    /**
     * Clear all error counts
     */
    @Scheduled(fixedRateString = "${outbound.errors.clearing.frequency:86400000}")
    public void clearOutboundErrors() {
        log.info("Clearing outbound errors");
        outboundErrorCount.clear();
    }
}
