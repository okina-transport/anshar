package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import no.rutebanken.anshar.util.SytralJSONMapper;
import org.joda.time.DateTime;


import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import uk.org.siri.siri20.MonitoredStopVisit;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

class JSONSytralTest extends SpringBootBaseTest {

    @Autowired
    private Situations situations;

    @Autowired
    private MonitoredStopVisits stopVisits;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private SiriApisRequestHandlerRoute siriApisRequestHandlerRoute;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private SiriHandler handler;


    private static final int DEFAULT_HEARTBEAT_SECONDS = 300;

    protected String url="http://defURL";
    protected String prefix = "SY";
    protected SiriDataType dataType = SiriDataType.STOP_MONITORING;
    protected RequestType requestType = RequestType.GET_STOP_MONITORING;

    protected Logger log = LoggerFactory.getLogger(getClass());

    @Test
    /**
     * Test SIRI Stop Monitoring
     */
    public void testJSONIntegration() {
        long startTime = DateTime.now().toInstant().getMillis();

        File file = new File("tcl_sytral.tclpassagearret.json");
        JSONParser parser = new JSONParser();
        try {
            JSONObject a = (JSONObject) parser.parse(new FileReader("src/test/resources/tcl_sytral.tclpassagearret.json"));
            JSONArray values = (JSONArray) a.get("values");

            List<MonitoredStopVisit> createdstopVisits = SytralJSONMapper.convertToSiriSM(values);

            List<String> visitSubscriptionList = getSubscriptionsFromVisits(createdstopVisits) ;

             //// STOP VISITS
            checkAndCreateSubscriptions(visitSubscriptionList, "SYTRAL_SM_", SiriDataType.STOP_MONITORING, RequestType.GET_STOP_MONITORING, "SYTRAL");

            Collection<MonitoredStopVisit> ingestedVisits = handler.ingestStopVisits("SYTRAL", createdstopVisits);

                for (MonitoredStopVisit visit : ingestedVisits) {
                    subscriptionManager.touchSubscription("SYTRAL_" + visit.getMonitoringRef().getValue(),false);
                }




            System.out.println("a");
            long endTime = DateTime.now().toInstant().getMillis();
            long processTime = (endTime - startTime) / 1000;
            log.info("JSON integration completed  in {} seconds ", processTime);




            Set<String> searchedStopIds = new HashSet<>();
            searchedStopIds.add("35998");

          //  Siri siriResult = stopVisits.createServiceDelivery("requestorId", null, "track", null, 10000000, -10000, searchedStopIds);



        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }

    }

    /**
     * Read all stopVisit messages and build a list of subscriptions that must be checked(or created if not exists)
     * @param stopVisits
     *      The list of stop visits
     * @return
     *      The list of subscription ids build by reading the visits
     */
    private List<String> getSubscriptionsFromVisits(List<MonitoredStopVisit> stopVisits) {

        return stopVisits.stream()
                .filter(visit -> visit.getMonitoringRef() != null &&  visit.getMonitoringRef().getValue() != null)
                .map(visit -> visit.getMonitoringRef().getValue())
                .collect(Collectors.toList());


    }

    /***
     * Read the list of subscription ids and for each, check if it exists. If not, a new subscription is created
     * @param subscriptionsList
     * @param customPrefix
     *  The list of subscription ids
     */
    private void checkAndCreateSubscriptions(List<String> subscriptionsList, String customPrefix, SiriDataType dataType, RequestType requestType, String datasetId) {

        for (String subscriptionId : subscriptionsList) {
            if (subscriptionManager.isGTFSRTSubscriptionExisting(customPrefix + subscriptionId))
                //A subscription is already existing for this vehicle journey. No need to create one
                continue;
            createNewSubscription(subscriptionId, customPrefix, dataType, requestType, datasetId);
            subscriptionManager.addGTFSRTSubscription(subscriptionId);
        }
    }

    /**
     * Create a new subscription for the ref given in parameter
     * @param ref
     *      The id for which a subscription must be created
     * @param customPrefix
     * @param dataType
     * @param requestType
     */
    private void createNewSubscription(String ref, String customPrefix, SiriDataType dataType, RequestType requestType, String datasetId){
        SubscriptionSetup setup = createStandardSubscription(ref, datasetId);
        String subscriptionId = customPrefix + ref;
        setup.setName(subscriptionId);
        setup.setSubscriptionType(dataType);
        setup.setSubscriptionId(subscriptionId);
        setup.getUrlMap().clear();
        setup.getUrlMap().put(requestType,url);
        setup.setStopMonitoringRefValue(ref);
        subscriptionManager.addSubscription(ref,setup);
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




}