package no.rutebanken.anshar.gtfsrt.ingesters;

import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.siri.handlers.inbound.SituationExchangeInbound;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.Siri;

import jakarta.xml.bind.JAXBException;

import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.*;

@Service
public class SituationExchangeIngester extends AbstractIngester {

    private static final Logger logger = LoggerFactory.getLogger(SituationExchangeIngester.class);

    private final SubscriptionManager subscriptionManager;
    private final SituationExchangeInbound situationExchangeInbound;
    private final HealthManager healthManager;

    public SituationExchangeIngester(SubscriptionManager subscriptionManager, SituationExchangeInbound situationExchangeInbound, HealthManager healthManager) {
        this.subscriptionManager = subscriptionManager;
        this.situationExchangeInbound = situationExchangeInbound;
        this.healthManager = healthManager;
        this.dataType = SiriDataType.SITUATION_EXCHANGE;
    }


    public void processIncomingSXFromGTFSRT(Exchange e) {
        InputStream xml = e.getIn().getBody(InputStream.class);
        Long inboundTime = e.getIn().getHeader(INBOUND_TIME_HEADER_NAME, Long.class);
        try {
            Siri siri = SiriValueTransformer.parseXml(xml);
            String datasetId = e.getIn().getHeader(DATASET_ID_HEADER_NAME, String.class);
            String url = e.getIn().getHeader(URL_HEADER_NAME, String.class);
            Boolean closeMissingAlerts = e.getIn().getHeader(CLOSE_MISSING_ALERTS_HEADER_NAME, Boolean.class);

            if (siri.getServiceDelivery() == null || siri.getServiceDelivery().getSituationExchangeDeliveries() == null || siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations() == null) {
                logger.info("Empty Situation exchange from GTFS-RT on dataset:" + datasetId);
                return;
            }

            healthManager.dataReceived();

            List<PtSituationElement> situations = siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements();
            if (BooleanUtils.isTrue(closeMissingAlerts)) {
                situationExchangeInbound.closeMissingAlerts(datasetId, situations, inboundTime);
            }
            List<String> subscriptionList = getSubscriptions(situations);
            checkAndCreateSubscriptions(subscriptionList, datasetId, url);

            Collection<PtSituationElement> ingestedSituations = situationExchangeInbound.ingestSituations(datasetId, situations, true, inboundTime);

            for (PtSituationElement situation : ingestedSituations) {
                subscriptionManager.touchSubscription(GTFSRT_SX_PREFIX + getSituationSubscriptionId(situation), false);
            }

            logger.info("GTFS-RT - Ingested alerts {} on {} . datasetId:{}, URL:{}", ingestedSituations.size(), situations.size(), datasetId, url);


        } catch (JAXBException | XMLStreamException jaxbException) {
            logger.error("Error while unmarshalling siri message from gtfsrt SX", e);
        }
    }

    private String getSituationSubscriptionId(PtSituationElement situation) {
        StringBuilder key = new StringBuilder();

        if (situation.getSituationNumber() != null) {
            key.append(situation.getSituationNumber().getValue());
            key.append(":");
        }

        if (situation.getParticipantRef() != null) {
            key.append(situation.getParticipantRef().getValue());
        }

        return key.length() > 0 ? key.toString() : "GeneralSubsCriptionId";
    }

    /**
     * Read all situation messages and build a list of subscriptions that must be checked(or created if not exists)
     *
     * @param situations The list of situations
     * @return The list of subscription ids build by reading the situations
     */
    private List<String> getSubscriptions(List<PtSituationElement> situations) {
        return situations.stream()
                .map(this::getSituationSubscriptionId)
                .toList();
    }

    /***
     * Read the list of subscription ids and for each, check if it exists. If not, a new subscription is created
     * @param subscriptionsList
     *  The list of subscription ids
     * @param url
     */
    private void checkAndCreateSubscriptions(List<String> subscriptionsList, String datasetId, String url) {

        for (String subsId : subscriptionsList) {
            if (subscriptionManager.isGTFSRTSubscriptionExisting(prefix + datasetId))
                //A subscription is already existing for this situation. No need to create one
                continue;
            createNewSubscription(subsId, datasetId, url);
        }
    }

    /**
     * Create a new subscription for the id given in parameter
     *
     * @param subscriptionId The id for which a subscription must be created
     * @param url
     */
    private void createNewSubscription(String subscriptionId, String datasetId, String url) {
        SubscriptionSetup setup = createStandardSubscription(subscriptionId, datasetId, url);
        setup.setSubscriptionId(prefix + datasetId);
        setup.setName(prefix + datasetId);
        setup.setSubscriptionType(SiriDataType.SITUATION_EXCHANGE);
        subscriptionManager.addSubscription(prefix + datasetId, setup);
        subscriptionManager.addGTFSRTSubscription(prefix + datasetId);
    }


}
