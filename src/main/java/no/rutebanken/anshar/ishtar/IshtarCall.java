package no.rutebanken.anshar.ishtar;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.util.StringUtils;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.api.SiriApi;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionInitializer;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.Exchange;
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

    protected IshtarCall(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

        if (StringUtils.isEmpty(ishtarUrl)) {
            return;
        }

        singletonFrom("quartz://anshar/getAllDataFromIshtar?trigger.repeatInterval=" + INTERVAL_IN_MILLIS_ISHTAR, "getAllDataFromIshtar")
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
                    List subscriptionResult = body().getExpression().evaluate(exchange, List.class);
                    ArrayList<SubscriptionSetup> results = new ArrayList<>();

                    if (subscriptionResult != null) {
                        for (Object obj : subscriptionResult) {
                            LinkedHashMap<?, ?> current_subscription = ((LinkedHashMap<?, ?>) ((LinkedHashMap<?, ?>) obj).get("subscription"));
                            if (current_subscription == null) {
                                current_subscription = (LinkedHashMap<?, ?>) obj;
                            }

                            List<LinkedHashMap<String, String>> customHeaders = (List<LinkedHashMap<String, String>>) current_subscription.get("customHeaders");
                            current_subscription.remove("customHeaders");

                            SubscriptionSetup newSubscription = objectMapper.convertValue(current_subscription, SubscriptionSetup.class);
                            addCustomHeaders(newSubscription, customHeaders);
                            newSubscription.setChangeBeforeUpdatesSeconds((int) current_subscription.get("changeBeforeUpdatesSeconds"));

                            newSubscription.setInternalId((int) current_subscription.get("id"));

                            List<Map<String, Object>> urlMapsList = (List<Map<String, Object>>) ((LinkedHashMap<?, ?>) obj).get("urlMaps");
                            Map<RequestType, String> urlMap = new HashMap<>();
                            for (Map<String, Object> urlMapData : urlMapsList) {

                                RequestType requestType = RequestType.valueOf(urlMapData.get("name").toString());
                                String url = urlMapData.get("url").toString();
                                urlMap.put(requestType, url);
                            }
                            newSubscription.setUrlMap(urlMap);


                            List<String> idMappingPrefixesList = (List<String>) ((LinkedHashMap<?, ?>) obj).get("idMappingPrefixes");
                            newSubscription.setIdMappingPrefixes(idMappingPrefixesList);
                            results.add(newSubscription);
                        }
                    } else {
                        log.warn("IshtarCall : subscriptionResult null");
                    }
                    subscriptionConfig.mergeSubscriptions(results);
                })
                .bean(SubscriptionInitializer.class, "createSubscriptions")
                .end();
    }

    private void addCustomHeaders(SubscriptionSetup newSubscription, List<LinkedHashMap<String, String>> customHeadersFromJSON) {
        Map<String, Object> customHeaders = new HashMap<>();
        for (LinkedHashMap<String, String> customHeaderData : customHeadersFromJSON) {

            String name = customHeaderData.get("name").toString();
            Object value = customHeaderData.get("value").toString();
            customHeaders.put(name, value);
        }
        newSubscription.setCustomHeaders(customHeaders);
    }
}
