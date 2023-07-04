package no.rutebanken.anshar.ishtar;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.api.SiriApi;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.*;
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

    private static final int INTERVAL_IN_MILLIS_ISHTAR = 1080000; // toutes les demi-heures

    @Value("${ishtar.server.port}")
    private String ISHTAR_PORT;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    protected IshtarCall(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {

        singletonFrom("quartz://anshar/getAllDataFromIshtar?trigger.repeatInterval=" + INTERVAL_IN_MILLIS_ISHTAR,"getAllGTFSRTFromIshtar")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD(ISHTAR_PORT + "/gtfs-rt-apis/all")
                .unmarshal().json(JsonLibrary.Jackson, List.class)
                .log("--> ISHTAR : get GTFS RT Api data")
                .process(exchange -> {
                    List<Object> allGtfs = body().getExpression().evaluate(exchange, List.class);
                    ArrayList<GtfsRTApi> gtfsResults = new ArrayList<>();
                    ObjectMapper objectMapper = new ObjectMapper();
                    // on vide les précédentes valeurs
                    subscriptionConfig.getGtfsRTApis().removeAll(subscriptionConfig.getGtfsRTApis());

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
                .toD(ISHTAR_PORT + "/siri-apis/all")
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
                .toD(ISHTAR_PORT + "/id-processing-parameters/all")
                .unmarshal().json(JsonLibrary.Jackson, List.class)
                .log("--> ISHTAR : get ID Processing Parameters data")
                .process(exchange -> {
                    List<Object> allProcessing = body().getExpression().evaluate(exchange, List.class);
                    ArrayList<IdProcessingParameters> processingResults = new ArrayList<>();
                    ObjectMapper objectMapper = new ObjectMapper();
                    // on vide les précédentes valeurs
                    subscriptionConfig.getIdProcessingParameters().removeAll(subscriptionConfig.getIdProcessingParameters());

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
                .toD(ISHTAR_PORT + "/subscriptions/all")
                .unmarshal().json(JsonLibrary.Jackson, List.class)
                .log("--> ISHTAR : get Subscriptions data")
                .process(exchange -> {
                    ObjectMapper objectMapper = new ObjectMapper();
                    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    List subscriptionResult = body().getExpression().evaluate(exchange, List.class);
                    ArrayList<SubscriptionSetup> results = new ArrayList<>();
                    // on vide les précédentes valeurs
                    subscriptionConfig.getSubscriptions().removeAll(subscriptionConfig.getSubscriptions());

                    if (subscriptionResult != null) {
                        for (Object obj : subscriptionResult) {
                            LinkedHashMap<?, ?> current_subscription = ((LinkedHashMap<?, ?>) ((LinkedHashMap<?, ?>) obj).get("subscription"));
                            if ((Boolean) current_subscription.get("active")) {
                                SubscriptionSetup newSubscription = objectMapper.convertValue(current_subscription, SubscriptionSetup.class);
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
                                List<String> idMappingPrefixes = new ArrayList<>(idMappingPrefixesList);
                                newSubscription.setIdMappingPrefixes(idMappingPrefixes);

                                results.add(newSubscription);
                            }
                        }
                    }
                    subscriptionConfig.getSubscriptions().addAll(results);
                })
                .bean(SubscriptionInitializer.class, "createSubscriptions")
                .end();
    }
}
