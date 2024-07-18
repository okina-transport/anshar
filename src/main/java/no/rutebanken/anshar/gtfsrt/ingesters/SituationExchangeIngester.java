package no.rutebanken.anshar.gtfsrt.ingesters;

import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.handlers.inbound.SituationExchangeInbound;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.Siri;

import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.*;

@Service
public class SituationExchangeIngester extends RestRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SituationExchangeIngester.class);

    @Autowired
    AnsharConfiguration configuration;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private SituationExchangeInbound situationExchangeInbound;

    @Autowired
    private HealthManager healthManager;


    public void processIncomingSXFromGTFSRT(Exchange e) {
        InputStream xml = e.getIn().getBody(InputStream.class);
        try {
            Siri siri = SiriValueTransformer.parseXml(xml);
            String datasetId = e.getIn().getHeader(DATASET_ID_HEADER_NAME, String.class);
            String url = e.getIn().getHeader(URL_HEADER_NAME, String.class);

            if (siri.getServiceDelivery() == null || siri.getServiceDelivery().getSituationExchangeDeliveries() == null || siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations() == null) {
                logger.info("Empty Situation exchange from GTFS-RT on dataset:" + datasetId);
                return;
            }

            healthManager.dataReceived();

            List<PtSituationElement> situations = siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements();


            Collection<PtSituationElement> ingestedSituations = situationExchangeInbound.ingestSituations(datasetId, situations, true);

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


}
