package no.rutebanken.anshar.gtfsrt.ingesters;

import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import no.rutebanken.anshar.util.IDUtils;


import java.util.HashMap;
import java.util.Map;


public class AbstractIngester extends RestRouteBuilder {
    private static final int DEFAULT_HEARTBEAT_SECONDS = 300;


    protected String prefix;
    protected SiriDataType dataType;
    protected RequestType requestType;


    protected SubscriptionSetup createStandardSubscription(String objectRef, String datasetId, String url) {
        SubscriptionSetup setup = new SubscriptionSetup();
        setup.setDatasetId(datasetId);
        setup.setHeartbeatIntervalSeconds(DEFAULT_HEARTBEAT_SECONDS);
        setup.setRequestorRef("OKINA-GTFS-RT");
        setup.setAddress(url);
        setup.setServiceType(SubscriptionSetup.ServiceType.REST);
        setup.setSubscriptionMode(SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE);
        setup.setDurationOfSubscriptionHours(24);
        setup.setVendor("OKINA");
        setup.setContentType("GTFS-RT");
        setup.setActive(true);

        String subscriptionId = prefix + objectRef;
        setup.setName(subscriptionId);
        setup.setSubscriptionType(dataType);
        setup.setSubscriptionId(subscriptionId);
        Map<RequestType, String> urlMap = new HashMap<>();
        urlMap.put(requestType, url);
        setup.setUrlMap(urlMap);
        setup.setInternalId(IDUtils.getUniqueInternalIdForGTFSRT());

        return setup;
    }


}
