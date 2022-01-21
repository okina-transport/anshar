package no.rutebanken.anshar.gtfsrt.swallowers;


import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;

import java.util.HashMap;
import java.util.Map;


public abstract class AbstractSwallower {

    private static final int DEFAULT_HEARTBEAT_SECONDS = 300;

    protected String url;
    protected String prefix;
    protected SiriDataType dataType;
    protected RequestType requestType;

    public void setUrl(String url) {
        this.url = url;
    }

    protected SubscriptionSetup createStandardSubscription(String objectRef){
        SubscriptionSetup setup = new SubscriptionSetup();
        setup.setDatasetId("GTFS-RT");
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
        urlMap.put(requestType,url);
        setup.setUrlMap(urlMap);


        return setup;
    }
}
