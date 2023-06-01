package no.rutebanken.anshar.gtfsrt.readers;


import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.ProducerTemplate;
import uk.org.siri.siri20.Siri;

import java.util.HashMap;
import java.util.Map;

import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;
import static no.rutebanken.anshar.routes.validation.validators.Constants.URL_HEADER_NAME;


public abstract class AbstractSwallower {

    private static final int DEFAULT_HEARTBEAT_SECONDS = 300;

    protected String url;
    protected String prefix;
    protected SiriDataType dataType;
    protected RequestType requestType;

    public void setUrl(String url) {
        this.url = url;
    }

    protected SubscriptionSetup createStandardSubscription(String objectRef, String datasetId){
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
        urlMap.put(requestType,url);
        setup.setUrlMap(urlMap);


        return setup;
    }

    protected void sendToRealTimeServer(ProducerTemplate producerTemplate, Siri siriToSend, String datasetId) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(DATASET_ID_HEADER_NAME, datasetId);
        headers.put(URL_HEADER_NAME,url);
        producerTemplate.asyncRequestBodyAndHeaders(producerTemplate.getDefaultEndpoint(), siriToSend, headers);
    }
}
