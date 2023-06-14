package no.rutebanken.anshar.gtfsrt.readers;

import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.gtfsrt.mappers.AlertMapper;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.validation.validators.Constants.GTFSRT_SX_PREFIX;

/**
 * Class to handle and ingest alert data
 * alert (GTFS-RT) = situation exchange (SIRI)
 */

@Component
public class AlertReader extends AbstractSwallower {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private SiriHandler handler;

    @Produce(uri = "direct:send.sx.to.realtime.server")
    protected ProducerTemplate gtfsrtSxProducer;



    public AlertReader() {
        prefix = GTFSRT_SX_PREFIX;
        dataType = SiriDataType.SITUATION_EXCHANGE;
        requestType = RequestType.GET_SITUATION_EXCHANGE;
    }


    /**
     * Main function to ingest data : take a complete GTFS-RT object (FeedMessage), read and map data about Alerts and ingest it
     *
     * @param completeGTFSRTMessage
     *      The complete message (GTFS-RT format)
     */
    public void ingestAlertData(String datasetId, GtfsRealtime.FeedMessage completeGTFSRTMessage ){
        List<PtSituationElement> situations = buildSituationList(completeGTFSRTMessage);
        updateParticipantRef(datasetId, situations);
        List<String> subscriptionList = getSubscriptions(situations) ;
        checkAndCreateSubscriptions(subscriptionList, datasetId);
        buildSiriAndSend(situations, datasetId);
    }

    private void buildSiriAndSend(List<PtSituationElement> situations, String datasetId) {

        if (situations.isEmpty()){
            logger.info("no situations to ingest");
            return;
        }

        Siri siri = new Siri();
        ServiceDelivery serviceDel = new ServiceDelivery();
        SituationExchangeDeliveryStructure delStruct = new SituationExchangeDeliveryStructure();
        SituationExchangeDeliveryStructure.Situations sitStruct = new SituationExchangeDeliveryStructure.Situations();
        sitStruct.getPtSituationElements().addAll(situations);
        delStruct.setSituations(sitStruct);
        serviceDel.getSituationExchangeDeliveries().add(delStruct);
        siri.setServiceDelivery(serviceDel);
        sendToRealTimeServer(gtfsrtSxProducer,siri, datasetId);
    }

    private void updateParticipantRef(String datasetId,  List<PtSituationElement> situations){
        for (PtSituationElement situation : situations) {
            if (situation.getParticipantRef() == null){
                RequestorRef requestorRef = new RequestorRef();
                requestorRef.setValue("MOBIITI");
                situation.setParticipantRef(requestorRef);

                SituationSourceStructure sourceStruct = new SituationSourceStructure();
                NaturalLanguageStringStructure langStruct = new NaturalLanguageStringStructure();
                langStruct.setLang("FR");
                langStruct.setValue(datasetId);
                sourceStruct.setName(langStruct);
                situation.setSource(sourceStruct);
            }
        }
    }

    /**
     * Read the complete GTS-RT message and build a list of situations to integrate
     * @param feedMessage
     *         The complete message (GTFS-RT format)
     * @return
     *         A list of situations, build by mapping alerts from GTFS-RT message
     */
    private  List<PtSituationElement> buildSituationList(GtfsRealtime.FeedMessage feedMessage) {
        List<PtSituationElement> situtations = new ArrayList<>();


        for (GtfsRealtime.FeedEntity feedEntity : feedMessage.getEntityList()) {
            if (isEmptyAlert(feedEntity.getAlert()))
                continue;


            PtSituationElement situation = AlertMapper.mapSituationFromAlert(feedEntity.getAlert());

            SituationNumber situationNumber = new SituationNumber();
            situationNumber.setValue(feedEntity.getId());
            situation.setSituationNumber(situationNumber);
            situtations.add(situation);
        }
        return situtations;

    }

    private boolean isEmptyAlert(GtfsRealtime.Alert alert){

     return alert == null || alert.getInformedEntityCount() == 0 ||
             alert.getHeaderText() == null ||  alert.getHeaderText().getTranslationList().size() == 0 ||
             alert.getDescriptionText() == null || alert.getDescriptionText().getTranslationList().size() == 0;

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
                .map(this::getSituationSubscriptionId)
                .collect(Collectors.toList());
    }

    private String getSituationSubscriptionId (PtSituationElement situation){
        StringBuilder key = new StringBuilder();

        if (situation.getSituationNumber() != null){
            key.append(situation.getSituationNumber().getValue());
            key.append(":");
        }

        if (situation.getParticipantRef() != null){
            key.append(situation.getParticipantRef().getValue());
        }

        return key.length() > 0 ? key.toString() : "GeneralSubsCriptionId";
    }


    /***
     * Read the list of subscription ids and for each, check if it exists. If not, a new subscription is created
     * @param subscriptionsList
     *  The list of subscription ids
     */
    private void checkAndCreateSubscriptions(List<String> subscriptionsList, String datasetId) {

        for (String subsId : subscriptionsList) {
            if (subscriptionManager.isGTFSRTSubscriptionExisting(prefix + subsId))
                //A subscription is already existing for this situation. No need to create one
                continue;
            createNewSubscription(subsId, datasetId);
        }
    }

    /**
     * Create a new subscription for the id given in parameter
     * @param subscriptionId
     *      The id for which a subscription must be created
     */
    private void createNewSubscription(String subscriptionId, String datasetId){
        SubscriptionSetup setup = createStandardSubscription(subscriptionId, datasetId);
        subscriptionManager.addSubscription(subscriptionId,setup);
        subscriptionManager.addGTFSRTSubscription(subscriptionId);
    }

}
