package no.rutebanken.anshar.gtfsrt.ingesters;

import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.siri.handlers.inbound.EstimatedTimetableInbound;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.Siri;

import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.*;

@Service
public class EstimatedTimetableIngester extends RestRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(EstimatedTimetableIngester.class);

    @Autowired
    AnsharConfiguration configuration;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private EstimatedTimetableInbound estimatedTimetableInbound;

    @Autowired
    private HealthManager healthManager;

    public void processIncomingETFromGTFSRT(Exchange e) {
        InputStream xml = e.getIn().getBody(InputStream.class);
        try {
            Siri siri = SiriValueTransformer.parseXml(xml);
            String datasetId = e.getIn().getHeader(DATASET_ID_HEADER_NAME, String.class);
            String url = e.getIn().getHeader(URL_HEADER_NAME, String.class);

            if (siri.getServiceDelivery() == null || siri.getServiceDelivery().getEstimatedTimetableDeliveries() == null
                    || siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames() == null) {
                logger.info("Empty EstimatedTimetables from GTFS-RT on dataset:" + datasetId);
                return;
            }

            healthManager.dataReceived();

            List<EstimatedVehicleJourney> estimatedVehicleJourneys = siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies();
            Collection<EstimatedVehicleJourney> ingestedEstimatedTimetables = estimatedTimetableInbound.ingestEstimatedTimeTables(datasetId, estimatedVehicleJourneys);

            for (EstimatedVehicleJourney estimatedVehicleJourney : ingestedEstimatedTimetables) {
                subscriptionManager.touchSubscription(GTFSRT_ET_PREFIX + estimatedVehicleJourney.getDatedVehicleJourneyRef().getValue(), false);
            }

            logger.info("GTFS-RT - Ingested estimated time tables {} on {}. datasetId:{}, URL:{}", ingestedEstimatedTimetables.size(), estimatedVehicleJourneys.size(), datasetId, url);

        } catch (JAXBException | XMLStreamException jaxbException) {
            logger.error("Error while unmarshalling siri message from gtfsrt ET", e);
        }
    }

}
