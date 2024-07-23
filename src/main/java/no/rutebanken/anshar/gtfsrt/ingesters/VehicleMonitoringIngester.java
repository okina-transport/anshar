package no.rutebanken.anshar.gtfsrt.ingesters;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.siri.handlers.inbound.VehicleMonitoringInbound;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.VehicleActivityStructure;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.*;

@Service
public class VehicleMonitoringIngester extends RestRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(VehicleMonitoringIngester.class);

    @Autowired
    AnsharConfiguration configuration;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private VehicleMonitoringInbound vehicleMonitoringInbound;

    @Autowired
    private HealthManager healthManager;

    public void processIncomingVMFromGTFSRT(Exchange e) {
        InputStream xml = e.getIn().getBody(InputStream.class);
        try {
            Siri siri = SiriValueTransformer.parseXml(xml);
            String datasetId = e.getIn().getHeader(DATASET_ID_HEADER_NAME, String.class);
            String url = e.getIn().getHeader(URL_HEADER_NAME, String.class);


            if (siri.getServiceDelivery() == null || siri.getServiceDelivery().getVehicleMonitoringDeliveries() == null) {
                logger.info("Empty VehicleMonitoring from GTFS-RT on dataset:" + datasetId);
                return;
            }

            healthManager.dataReceived();

            List<VehicleActivityStructure> vehicleActivities = siri.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities();
            Collection<VehicleActivityStructure> ingestedVehicleJourneys = vehicleMonitoringInbound.ingestVehicleActivities(datasetId, vehicleActivities);
            for (VehicleActivityStructure vehicleActivity : ingestedVehicleJourneys) {
                subscriptionManager.touchSubscription(GTFSRT_VM_PREFIX + vehicleActivity.getMonitoredVehicleJourney().getLineRef().getValue(), false);
            }

            logger.info("GTFS-RT - Ingested  vehicle positions {} on {} . datasetId:{}, URL:{}", ingestedVehicleJourneys.size(), vehicleActivities.size(), datasetId, url);

        } catch (JAXBException | XMLStreamException jaxbException) {
            logger.error("Error while unmarshalling siri message from gtfsrt VM", e);
        }
    }

}
