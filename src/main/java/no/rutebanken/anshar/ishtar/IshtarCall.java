package no.rutebanken.anshar.ishtar;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.util.StringUtils;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.api.SiriApi;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.DiscoverySubscription;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.*;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class IshtarCall extends BaseRouteBuilder {

    @Value("${ishtar.interval.millis:180000}")
    private int INTERVAL_IN_MILLIS_ISHTAR;

    @Value("${ishtar.server.url}")
    private String ishtarUrl;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private DiscoverySubscriptionCreator discoverySubscriptionCreator;

    protected IshtarCall(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

        if (StringUtils.isEmpty(ishtarUrl)) {
            return;
        }

        singletonFrom("quartz://anshar/autoGetAllDataFromIshtar?trigger.repeatInterval=" + INTERVAL_IN_MILLIS_ISHTAR,
                "autoGetAllDataFromIshtar")
                .bean(this, "callDataFromIshtar")
                .end();

        from("direct:startDataFetch")
                .routeId("startDataFetch")
                .removeHeaders("*")
                .log(LoggingLevel.INFO, "--> ISHTAR : start synchronize data")
                .to("direct:getAllDataFromIshtar")
                .end();

        from("direct:getAllDataFromIshtar")
                .routeId("getAllDataFromIshtar")
                .onException(Exception.class)
                .handled(true)
                .log(LoggingLevel.ERROR, "--> ISHTAR : error during data synchronization : ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                .setBody(simple("Error during data synchronization : ${exception.message}"))
                .end()
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .setHeader("Accept", constant("application/json, text/plain, */*"))
                .toD(ishtarUrl + "/gtfs-rt-apis/all")
                .unmarshal().json(JsonLibrary.Jackson, List.class)
                .log("--> ISHTAR : get GTFS RT Api data")
                .process(exchange -> {
                    List<Object> allGtfs = body().getExpression().evaluate(exchange, List.class);
                    ArrayList<GtfsRTApi> gtfsResults = new ArrayList<>();
                    ObjectMapper objectMapper = new ObjectMapper();


                    if (allGtfs != null) {
                        for (Object obj : allGtfs) {
                            Map<?, ?> jsonMap = (Map<?, ?>) obj;
                            jsonMap.remove("id"); // Ignorer la propriété
                            jsonMap.remove("updateDatetime"); // Ignorer la propriété
                            GtfsRTApi newGtfs = objectMapper.convertValue(jsonMap, GtfsRTApi.class);
                            gtfsResults.add(newGtfs);
                        }
                    }
                    log.info("Recovered GTFSRT-APIs:" + gtfsResults.size());
                    log.info("Existing GTFSRT-APIs:" + subscriptionConfig.getGtfsRTApis().size());
                    subscriptionConfig.mergeGTFSRTApis(gtfsResults);
                })

                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD(ishtarUrl + "/siri-apis/all")
                .unmarshal().json(JsonLibrary.Jackson, List.class)
                .log("--> ISHTAR : get Siri Api data")
                .process(exchange -> {
                    List<Object> allSiri = body().getExpression().evaluate(exchange, List.class);
                    ArrayList<SiriApi> siriResults = new ArrayList<>();
                    ObjectMapper objectMapper = new ObjectMapper();


                    if (allSiri != null) {
                        for (Object obj : allSiri) {
                            Map<?, ?> jsonMap = (Map<?, ?>) obj;

                            jsonMap.remove("id"); // Ignorer la propriété
                            jsonMap.remove("updateDatetime"); // Ignorer la propriété
                            SiriApi newSiri = objectMapper.convertValue(jsonMap, SiriApi.class);
                            siriResults.add(newSiri);

                        }
                    }
                    subscriptionConfig.mergeSiriApis(siriResults);
                })

                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD(ishtarUrl + "/id-processing-parameters/all")
                .unmarshal().json(JsonLibrary.Jackson, List.class)
                .log("--> ISHTAR : get ID Processing Parameters data")
                .process(exchange -> {
                    List<Object> allProcessing = body().getExpression().evaluate(exchange, List.class);
                    ArrayList<IdProcessingParameters> processingResults = new ArrayList<>();
                    ObjectMapper objectMapper = new ObjectMapper();


                    if (allProcessing != null) {
                        for (Object obj : allProcessing) {
                            Map<?, ?> jsonMap = (Map<?, ?>) obj;
                            jsonMap.remove("id"); // Ignorer la propriété
                            jsonMap.remove("updateDatetime"); // Ignorer la propriété
                            IdProcessingParameters newProcessing = objectMapper.convertValue(jsonMap, IdProcessingParameters.class);
                            processingResults.add(newProcessing);
                        }
                    }
                    subscriptionConfig.mergeIdProcessingParams(processingResults);
                })

                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD(ishtarUrl + "/subscriptions/all")
                .unmarshal().json(JsonLibrary.Jackson, List.class)
                .log("--> ISHTAR : get Subscriptions data")
                .process(exchange -> {
                    ObjectMapper objectMapper = new ObjectMapper();
                    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
                    List subscriptionResult = body().getExpression().evaluate(exchange, List.class);
                    List<SubscriptionSetup> results = new ArrayList<>();
                    List<DiscoverySubscription> discoveryResults = new ArrayList<>();

                    List<LinkedHashMap<?, ?>> standardSubscriptions = new ArrayList<>();
                    List<LinkedHashMap<?, ?>> discoverySubscriptionsSubscriptions = new ArrayList<>();


                    if (subscriptionResult != null) {
                        for (Object obj : subscriptionResult) {

                            LinkedHashMap<?, ?> current_subscription = ((LinkedHashMap<?, ?>) ((LinkedHashMap<?, ?>) obj).get("subscription"));
                            if (current_subscription == null) {
                                current_subscription = (LinkedHashMap<?, ?>) obj;
                            }

                            if (!(boolean) current_subscription.get("active")) {
                                continue;
                            }

                            if (current_subscription.containsKey("discoverySubscription") && (boolean) current_subscription.get("discoverySubscription")) {
                                discoverySubscriptionsSubscriptions.add(current_subscription);
                            } else {
                                standardSubscriptions.add(current_subscription);
                            }

                        }
                    } else {
                        log.warn("IshtarCall : subscriptionResult null");
                    }

                    if (!standardSubscriptions.isEmpty()) {
                        for (LinkedHashMap<?, ?> current_subscription : standardSubscriptions) {
                            List<LinkedHashMap<String, String>> customHeaders = (List<LinkedHashMap<String, String>>) current_subscription.get("customHeaders");
                            current_subscription.remove("customHeaders");
                            List<LinkedHashMap<String, String>> idMappingPrefixes = (List<LinkedHashMap<String, String>>) current_subscription.get("idMappingPrefixes");
                            current_subscription.remove("idMappingPrefixes");

                            SubscriptionSetup newSubscription = objectMapper.convertValue(current_subscription, SubscriptionSetup.class);
                            addCustomHeaders(newSubscription, customHeaders);
                            addIdMappingPrefixes(newSubscription, idMappingPrefixes);
                            newSubscription.setChangeBeforeUpdatesSeconds((int) current_subscription.get("changeBeforeUpdatesSeconds"));
                            newSubscription.setInternalId((int) current_subscription.get("id"));
                            createAndSetStopMonitoringRefValues(current_subscription, newSubscription);
                            createAndSetLineRefValues(current_subscription, newSubscription);
                            Map<RequestType, String> urlMap = getUrlMaps(current_subscription);
                            newSubscription.setUrlMap(urlMap);
                            if (isNotCompliant(newSubscription)) {
                                continue;
                            }
                            List<String> idMappingPrefixesList = (List<String>) current_subscription.get("idMappingPrefixes");
                            newSubscription.setIdMappingPrefixes(idMappingPrefixesList);
                            results.add(newSubscription);
                        }
                        log.info("Merging ishtar subscriptions with existing subscriptions:" + results.size());
                        subscriptionConfig.mergeSubscriptions(results);
                    } else {
                        log.info("No ishtar subscriptions found");
                    }

                    if (!discoverySubscriptionsSubscriptions.isEmpty()) {
                        for (LinkedHashMap<?, ?> current_subscription : discoverySubscriptionsSubscriptions) {
                            DiscoverySubscription newSubscription = new DiscoverySubscription();
                            newSubscription.setDatasetId((String) current_subscription.get("datasetId"));
                            newSubscription.setSubscriptionMode(SubscriptionSetup.SubscriptionMode.valueOf((String) current_subscription.get("subscriptionMode")));
                            newSubscription.setDiscoveryType(SiriDataType.valueOf((String) current_subscription.get("subscriptionType")));
                            newSubscription.setDurationOfSubscriptionHours((int) current_subscription.get("durationOfSubscriptionHours"));
                            newSubscription.setSubscriptionIdBase((String) current_subscription.get("subscriptionId"));
                            List<LinkedHashMap<String, String>> customHeaders = (List<LinkedHashMap<String, String>>) current_subscription.get("customHeaders");
                            addCustomHeaders(newSubscription, customHeaders);
                            Map<RequestType, String> urlMap = getUrlMaps(current_subscription);
                            Optional<String> urlOpt = urlMap.values().stream().findFirst();
                            urlOpt.ifPresent(newSubscription::setUrl);
                            newSubscription.setChangeBeforeUpdatesSeconds((int) current_subscription.get("changeBeforeUpdatesSeconds"));
                            newSubscription.setHeartbeatIntervalSeconds((int) current_subscription.get("heartbeatIntervalSeconds"));
                            newSubscription.setPreviewIntervalSeconds((int) current_subscription.get("previewIntervalSeconds"));
                            newSubscription.setRequestorRef((String) current_subscription.get("requestorRef"));
                            newSubscription.setVendorBaseName((String) current_subscription.get("vendor"));

                            discoveryResults.add(newSubscription);
                        }
                        log.info("Merging ishtar subscriptions with existing subscriptions:" + results.size());
                        subscriptionConfig.mergeDiscoverySubscriptions(discoveryResults);
                        discoverySubscriptionCreator.createDiscoverySubscriptions();
                    }


                })
                .bean(SubscriptionInitializer.class, "createSubscriptions")
                .end();
    }

    /**
     * Executes some tests to check if the subscription is correct
     *
     * @param newSubscription
     * @return true : subscription is correct
     * false : subscription is not correct
     */
    private boolean isNotCompliant(SubscriptionSetup newSubscription) {
        if (SubscriptionSetup.SubscriptionMode.SUBSCRIBE.equals(newSubscription.getSubscriptionMode())) {

            if (newSubscription.getUrlMap() == null || newSubscription.getUrlMap().isEmpty() || !newSubscription.getUrlMap().containsKey(RequestType.SUBSCRIBE)) {
                log.warn("Subscription not compliant. Missing subcribe URL : " + newSubscription.getSubscriptionId());
                return false;
            }

        }
        return true;
    }

    private Map<RequestType, String> getUrlMaps(LinkedHashMap<?, ?> params) {
        Map<RequestType, String> results = new HashMap<>();

        if (!params.containsKey("urlMaps")) {
            return new HashMap<>();
        }

        Object obj = params.get("urlMaps");
        if (obj instanceof List) {
            List<Map<String, Object>> urlMapsList = (List<Map<String, Object>>) obj;
            for (Map<String, Object> urlMapData : urlMapsList) {
                RequestType requestType = RequestType.valueOf(urlMapData.get("name").toString());
                String url = urlMapData.get("url").toString();
                results.put(requestType, url);
            }
        } else {
            Map<String, Object> urlMapData = (Map<String, Object>) obj;
            RequestType requestType = RequestType.valueOf(urlMapData.get("name").toString());
            String url = urlMapData.get("url").toString();
            results.put(requestType, url);
        }

        return results;
    }

    /**
     * Extracts the "name" field from each entry in the "subscriptionLines" list
     * within the provided {@code current_subscription} map and sets these names
     * as the line reference values in the provided {@code newSubscription}.
     *
     * @param current_subscription the map containing subscription details, expected to have a "subscriptionLines" key
     *                             which maps to a list of LinkedHashMaps representing subscription stops.
     * @param newSubscription      the {@link SubscriptionSetup} object to be updated with the extracted line reference values.
     */
    private void createAndSetLineRefValues(LinkedHashMap<?, ?> current_subscription, SubscriptionSetup newSubscription) {
        List<LinkedHashMap<String, String>> subscriptionLines = (List<LinkedHashMap<String, String>>) current_subscription.get("subscriptionLines");
        if (subscriptionLines != null && !subscriptionLines.isEmpty()) {
            List<String> lineRefValue = new ArrayList<>();
            for (LinkedHashMap<String, String> line : subscriptionLines) {
                String name = line.get("name");
                if (name != null) {
                    lineRefValue.add(name);
                }
            }
            newSubscription.setLineRefValues(lineRefValue);
        }
    }

    /**
     * Extracts the "name" field from each entry in the "subscriptionStops" list
     * within the provided {@code current_subscription} map and sets these names
     * as the stop monitoring reference values in the provided {@code newSubscription}.
     *
     * @param current_subscription the map containing subscription details, expected to have a "subscriptionStops" key
     *                             which maps to a list of LinkedHashMaps representing subscription stops.
     * @param newSubscription      the {@link SubscriptionSetup} object to be updated with the extracted stop monitoring reference values.
     */
    private void createAndSetStopMonitoringRefValues(LinkedHashMap<?, ?> current_subscription, SubscriptionSetup newSubscription) {
        List<LinkedHashMap<String, String>> subscriptionStops = (List<LinkedHashMap<String, String>>) current_subscription.get("subscriptionStops");
        if (subscriptionStops != null && !subscriptionStops.isEmpty()) {
            List<String> stopMonitoringRefValue = new ArrayList<>();
            for (LinkedHashMap<String, String> stop : subscriptionStops) {
                String stopRef = stop.get("stopRef");
                if (stopRef != null) {
                    stopMonitoringRefValue.add(stopRef);
                }
            }
            newSubscription.setStopMonitoringRefValue(stopMonitoringRefValue);
        }
    }

    public void callDataFromIshtar() {
        getContext().createProducerTemplate().sendBody("direct:getAllDataFromIshtar", null);
    }

    private void addIdMappingPrefixes(SubscriptionSetup newSubscription, List<LinkedHashMap<String, String>> idMappingPrefixesFromJson) {
        if (idMappingPrefixesFromJson == null) {
            return;
        }

        List<String> idMappingPrefixesList = new ArrayList<>();
        for (LinkedHashMap<String, String> idMappingPrefixes : idMappingPrefixesFromJson) {
            String value = idMappingPrefixes.get("value");
            idMappingPrefixesList.add(value);
        }
        newSubscription.setIdMappingPrefixes(idMappingPrefixesList);
    }

    private void addCustomHeaders(SubscriptionSetup newSubscription, List<LinkedHashMap<String, String>> customHeadersFromJSON) {
        if (customHeadersFromJSON == null) {
            return;
        }

        Map<String, Object> customHeaders = new HashMap<>();
        for (LinkedHashMap<String, String> customHeaderData : customHeadersFromJSON) {

            String name = customHeaderData.get("name").toString();
            Object value = customHeaderData.get("value").toString();
            customHeaders.put(name, value);
        }
        newSubscription.setCustomHeaders(customHeaders);
    }

    private void addCustomHeaders(DiscoverySubscription newSubscription, List<LinkedHashMap<String, String>> customHeadersFromJSON) {
        if (customHeadersFromJSON == null) {
            return;
        }

        Map<String, Object> customHeaders = new HashMap<>();
        for (LinkedHashMap<String, String> customHeaderData : customHeadersFromJSON) {

            String name = customHeaderData.get("name").toString();
            Object value = customHeaderData.get("value").toString();
            customHeaders.put(name, value);
        }
        newSubscription.setCustomHeaders(customHeaders);
    }
}
