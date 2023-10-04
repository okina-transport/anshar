package no.rutebanken.anshar.ishtar;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                    // on vide les précédentes valeurs
                    subscriptionConfig.getGtfsRTApis().clear();


                    if (allGtfs != null) {
                        for (Object obj : allGtfs) {
                            Map<?, ?> jsonMap = (Map<?, ?>) obj;
                            if ((Boolean) jsonMap.get("active")) {
                                jsonMap.remove("id"); // Ignorer la propriété
                                jsonMap.remove("updateDatetime"); // Ignorer la propriété
                                GtfsRTApi newGtfs = objectMapper.convertValue(jsonMap, GtfsRTApi.class);
                                gtfsResults.add(newGtfs);
                            }
                        }
                    }
                    subscriptionConfig.getGtfsRTApis().addAll(gtfsResults);
                })

                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD(ishtarUrl + "/siri-apis/all")
                .unmarshal().json(JsonLibrary.Jackson, List.class)
                .log("--> ISHTAR : get Siri Api data")
                .process(exchange -> {
                    List<Object> allSiri = body().getExpression().evaluate(exchange, List.class);
                    ArrayList<SiriApi> siriResults = new ArrayList<>();
                    ObjectMapper objectMapper = new ObjectMapper();
                    // on vide les précédentes valeurs
                    subscriptionConfig.getSiriApis().removeAll(subscriptionConfig.getSiriApis());

                    if (allSiri != null) {
                        for (Object obj : allSiri) {
                            Map<?, ?> jsonMap = (Map<?, ?>) obj;
                            if ((Boolean) jsonMap.get("active")) {
                                jsonMap.remove("id"); // Ignorer la propriété
                                jsonMap.remove("updateDatetime"); // Ignorer la propriété
                                SiriApi newSiri = objectMapper.convertValue(jsonMap, SiriApi.class);
                                siriResults.add(newSiri);
                            }
                        }
                    }
                    subscriptionConfig.getSiriApis().addAll(siriResults);
                })

                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD(ishtarUrl + "/id-processing-parameters/all")
                .unmarshal().json(JsonLibrary.Jackson, List.class)
                .log("--> ISHTAR : get ID Processing Parameters data")
                .process(exchange -> {
                    List<Object> allProcessing = body().getExpression().evaluate(exchange, List.class);
                    ArrayList<IdProcessingParameters> processingResults = new ArrayList<>();
                    ObjectMapper objectMapper = new ObjectMapper();
                    // on vide les précédentes valeurs
                    subscriptionConfig.getIdProcessingParameters().clear();

                    if (allProcessing != null) {
                        for (Object obj : allProcessing) {
                            Map<?, ?> jsonMap = (Map<?, ?>) obj;
                            jsonMap.remove("id"); // Ignorer la propriété
                            jsonMap.remove("updateDatetime"); // Ignorer la propriété
                            IdProcessingParameters newProcessing = objectMapper.convertValue(jsonMap, IdProcessingParameters.class);
                            processingResults.add(newProcessing);
                        }
                    }
                    subscriptionConfig.getIdProcessingParameters().addAll(processingResults);
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
                    // on vide les précédentes valeurs
                    subscriptionConfig.getSubscriptions().clear();

                    if (subscriptionResult != null) {
                        for (Object obj : subscriptionResult) {
                            LinkedHashMap<?, ?> current_subscription = ((LinkedHashMap<?, ?>) ((LinkedHashMap<?, ?>) obj).get("subscription"));
                            if (current_subscription == null) {
                                continue;
                            }
                            SubscriptionSetup newSubscription = objectMapper.convertValue(current_subscription, SubscriptionSetup.class);
                            newSubscription.setChangeBeforeUpdatesSeconds((int) current_subscription.get("changeBeforeUpdateSeconds"));

                            newSubscription.setInternalId((int) ((LinkedHashMap<?, ?>) ((LinkedHashMap<?, ?>) obj).get("subscription")).get("id"));

                            List<Map<String, Object>> urlMapsList = (List<Map<String, Object>>) ((LinkedHashMap<?, ?>) obj).get("urlMaps");
                            Map<RequestType, String> urlMap = new HashMap<>();
                            for (Map<String, Object> urlMapData : urlMapsList) {
                                LinkedHashMap<?, ?> current_urlMap = ((LinkedHashMap<?, ?>) ((LinkedHashMap<?, ?>) urlMapData).get("urlMaps"));
                                RequestType requestType = RequestType.valueOf(current_urlMap.get("name").toString());
                                String url = current_urlMap.get("url").toString();
                                urlMap.put(requestType, url);
                            }
                            newSubscription.setUrlMap(urlMap);

                            List<Map<String, Object>> customHeadersList = (List<Map<String, Object>>) ((LinkedHashMap<?, ?>) obj).get("customHeaders");
                            Map<String, Object> customHeaders = new HashMap<>();
                            for (Map<String, Object> customHeaderData : customHeadersList) {
                                LinkedHashMap<?, ?> current_customHeaders = ((LinkedHashMap<?, ?>) ((LinkedHashMap<?, ?>) customHeaderData).get("customHeaders"));
                                String name = current_customHeaders.get("name").toString();
                                Object value = current_customHeaders.get("value").toString();
                                customHeaders.put(name, value);
                            }
                            newSubscription.setCustomHeaders(customHeaders);

                            List<String> idMappingPrefixesList = (List<String>) ((LinkedHashMap<?, ?>) obj).get("idMappingPrefixes");
                            newSubscription.setIdMappingPrefixes(idMappingPrefixesList);
                            results.add(newSubscription);
                        }
                    }
                    subscriptionConfig.getSubscriptions().addAll(results);
                })
                .bean(SubscriptionInitializer.class, "createSubscriptions")
                .end();
    }
}
