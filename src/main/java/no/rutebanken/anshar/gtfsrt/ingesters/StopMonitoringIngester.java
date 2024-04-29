package no.rutebanken.anshar.gtfsrt.ingesters;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.siri.handlers.inbound.StopMonitoringInbound;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.MonitoredStopVisit;
import uk.org.siri.siri20.MonitoredStopVisitCancellation;
import uk.org.siri.siri20.Siri;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.*;

@Service
public class StopMonitoringIngester extends RestRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(StopMonitoringIngester.class);

    @Autowired
    AnsharConfiguration configuration;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private StopMonitoringInbound stopMonitoringInbound;

    @Autowired
    private HealthManager healthManager;


    public void processIncomingSMFromGTFSRT(Exchange e) {
        InputStream xml = e.getIn().getBody(InputStream.class);
        try {
            Siri siri = SiriValueTransformer.parseXml(xml);
            String datasetId = e.getIn().getHeader(DATASET_ID_HEADER_NAME, String.class);
            String url = e.getIn().getHeader(URL_HEADER_NAME, String.class);

            if (siri.getServiceDelivery() == null || siri.getServiceDelivery().getStopMonitoringDeliveries() == null ||
                    siri.getServiceDelivery().getStopMonitoringDeliveries().get(0) == null) {
                logger.info("Empty StopMonitoring from GTFS-RT on dataset:" + datasetId);
                return;
            }

            healthManager.dataReceived();

            List<MonitoredStopVisit> stopVisits = siri.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits();

            Collection<MonitoredStopVisit> ingestedVisits = stopMonitoringInbound.ingestStopVisits(datasetId, stopVisits);

            for (MonitoredStopVisit visit : ingestedVisits) {
                subscriptionManager.touchSubscription(GTFSRT_SM_PREFIX + visit.getMonitoringRef().getValue(), false);
            }

            List<MonitoredStopVisitCancellation> stopVisitToCancel = siri.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisitCancellations();

            if (stopVisitToCancel != null && stopVisitToCancel.size() > 0) {
                stopMonitoringInbound.cancelStopVisits(datasetId, stopVisitToCancel);
            }

            logger.info("GTFS-RT - Ingested  stop Times {} on {} . datasetId:{}, URL:{}", ingestedVisits.size(), stopVisits.size(), datasetId, url);


        } catch (JAXBException | XMLStreamException jaxbException) {
            logger.error("Error while unmarshalling siri message from gtfsrt SM", e);
        }
    }

}
