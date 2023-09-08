package no.rutebanken.anshar.okinaDisruptions;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.util.StringUtils;
import no.rutebanken.anshar.okinaDisruptions.model.Disruption;
import no.rutebanken.anshar.okinaDisruptions.model.MillisOrLocalDateTimeDeserializer;
import no.rutebanken.anshar.routes.siri.handlers.inbound.SituationExchangeInbound;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.PtSituationElement;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Component
public class DisruptionRetriever {


    private static final int DEFAULT_HEARTBEAT_SECONDS = 300;
    private static final String PREFIX = "OKINA_SX_";

    private static final Logger logger = LoggerFactory.getLogger(DisruptionRetriever.class);

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private DisruptionService disruptionService;


    @Value("${mobi.iti.disruption.url:defaultURL}")
    String disruptionURLInSubscriptions;

    private String ansharUserId;

    @Autowired
    private SituationExchangeInbound situationExchangeInbound;

    public DisruptionRetriever() {
        ansharUserId = "anshar-" + System.currentTimeMillis();
    }

    public void retrieveDisruptions() {
        logger.info("Début récupération des perturbations");

        try {
            String disruptionsString = disruptionService.getAllDisrutionsFromOkinaDB(ansharUserId);

            ingestDisruption(disruptionsString);

        } catch (IOException e) {
            logger.error("Error while retrieving disruptions");
            logger.error(e.getMessage());

        }


    }

    void ingestDisruption(String disruptionsString) throws JsonProcessingException {

        if (StringUtils.isEmpty(disruptionsString)){
            logger.info("Pas de nouvelle perturbation");
            return;
        }

        List<Disruption> disruptions = convertJSONtoObjects(disruptionsString);


        List<Disruption> disruptionsToDelete = disruptions.stream()
                .filter(disruption -> disruption.getDeleteDateTime() != null)
                .collect(Collectors.toList());

        List<Disruption> disruptionsToIngest = disruptions.stream()
                .filter(disruption -> disruption.getDeleteDateTime() == null)
                .filter(disruption -> disruption.getDiffusion() != null && disruption.getDiffusion().equals("DIFFUSED"))
                .collect(Collectors.toList());

        Map<String, List<Disruption>> disruptionsByDataset = buildDisruptionMap(disruptionsToIngest);


        List<PtSituationElement> ingestedSituations = new ArrayList<>();

        int totalSituationCount = 0;
        int totalDeletedSituationCount = 0;
        for (Map.Entry<String, List<Disruption>> currentDisruptionEntry : disruptionsByDataset.entrySet()) {


            List<PtSituationElement> situations = currentDisruptionEntry.getValue().stream()
                    .map(SituationExchangeGenerator::createFromDisruption)
                    .collect(Collectors.toList());

            totalSituationCount = totalSituationCount + situations.size();

            List<PtSituationElement> situationsToDelete = disruptionsToDelete.stream()
                    .map(SituationExchangeGenerator::createFromDisruption)
                    .collect(Collectors.toList());
            totalDeletedSituationCount = totalDeletedSituationCount + situationsToDelete.size();

            situationsToDelete.forEach(situationToDelete -> situationExchangeInbound.removeSituation(currentDisruptionEntry.getKey(), situationToDelete));
            List<String> subscriptionList = getSubscriptions(situations) ;
            checkAndCreateSubscriptions(subscriptionList);
            ingestedSituations.addAll(situationExchangeInbound.ingestSituations(currentDisruptionEntry.getKey(), situations));

        }


        for (PtSituationElement situation : ingestedSituations) {
            subscriptionManager.touchSubscription(PREFIX + situation.getSituationNumber());
        }

        logger.info("Ingested alerts from Okina disruption service {} on {}. Deleted : {} ", ingestedSituations.size(), totalSituationCount, totalDeletedSituationCount);

    }

    private Map<String, List<Disruption>> buildDisruptionMap(List<Disruption> disruptionsToIngest) {
        Map<String, List<Disruption>> resultMap = new HashMap<>();

        for (Disruption disruption : disruptionsToIngest) {
            List<Disruption> currentDisruptionList;
            String organization = disruption.getOrganization();
            if (resultMap.containsKey(organization)){
                currentDisruptionList = resultMap.get(organization);
            }else{
                currentDisruptionList = new ArrayList<>();
                resultMap.put(organization, currentDisruptionList);
            }
            currentDisruptionList.add(disruption);
        }

        return resultMap;
    }

    /**
     * Read all situation messages and build a list of subscriptions that must be checked(or created if not exists)
     * @param situations
     *      The list of situations
     * @return
     *      The list of subscription ids build by reading the situations
     */
    private List<String> getSubscriptions(List<PtSituationElement> situations) {
        return situations.stream()
                .map(situation -> situation.getSituationNumber().getValue())
                .collect(Collectors.toList());
    }


    /***
     * Read the list of subscription ids and for each, check if it exists. If not, a new subscription is created
     * @param subscriptionsList
     *  The list of subscription ids
     */
    private void checkAndCreateSubscriptions(List<String> subscriptionsList) {

        for (String subsId : subscriptionsList) {
            if (subscriptionManager.isSubscriptionExisting(PREFIX + subsId))
                //A subscription is already existing for this situation. No need to create one
                continue;
            createNewSubscription(subsId);
        }
    }

    /**
     * Create a new subscription for the id given in parameter
     * @param subscriptionId
     *      The id for which a subscription must be created
     */
    private void createNewSubscription(String subscriptionId){
        SubscriptionSetup setup = createStandardSubscription(subscriptionId);
        subscriptionManager.addSubscription(subscriptionId,setup);
    }

    protected SubscriptionSetup createStandardSubscription(String objectRef){
        SubscriptionSetup setup = new SubscriptionSetup();
        setup.setDatasetId("OKINA-DISRUPTION-SERVICE");
        setup.setHeartbeatIntervalSeconds(DEFAULT_HEARTBEAT_SECONDS);
        setup.setRequestorRef("OKINA");
        setup.setAddress(disruptionURLInSubscriptions);
        setup.setServiceType(SubscriptionSetup.ServiceType.REST);
        setup.setSubscriptionMode(SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE);
        setup.setDurationOfSubscriptionHours(24);
        setup.setVendor("OKINA");
        setup.setContentType("DISRUPTIONS");
        setup.setActive(true);

        String subscriptionId = PREFIX + objectRef;
        setup.setName(subscriptionId);
        setup.setSubscriptionType(SiriDataType.SITUATION_EXCHANGE);
        setup.setSubscriptionId(subscriptionId);
        Map<RequestType, String> urlMap = new HashMap<>();
        urlMap.put(RequestType.GET_SITUATION_EXCHANGE, disruptionURLInSubscriptions);
        setup.setUrlMap(urlMap);


        return setup;
    }


    private List<Disruption> convertJSONtoObjects(String jsonDisruptions) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();

        JavaTimeModule javaTimeModule = new JavaTimeModule();
        // override default
        javaTimeModule.addDeserializer(LocalDateTime.class, new MillisOrLocalDateTimeDeserializer());

        objectMapper.registerModule(javaTimeModule);
        return objectMapper.readValue(jsonDisruptions, new TypeReference<>() {
        });

    }
}
