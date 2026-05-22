package no.rutebanken.anshar.gtfsrt.ingesters;

import no.rutebanken.anshar.data.DiscoveryCache;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.siri.handlers.inbound.VehicleMonitoringInbound;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.VehicleActivityStructure;

import jakarta.xml.bind.JAXBException;

import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.validation.validators.Constants.*;

@Service
public class VehicleMonitoringIngester extends AbstractIngester {

    private static final Logger logger = LoggerFactory.getLogger(VehicleMonitoringIngester.class);


    private final SubscriptionManager subscriptionManager;
    private final VehicleMonitoringInbound vehicleMonitoringInbound;
    private final HealthManager healthManager;
    private final DiscoveryCache discoveryCache;

    public VehicleMonitoringIngester(SubscriptionManager subscriptionManager, VehicleMonitoringInbound vehicleMonitoringInbound, HealthManager healthManager, DiscoveryCache discoveryCache) {
        this.subscriptionManager = subscriptionManager;
        this.vehicleMonitoringInbound = vehicleMonitoringInbound;
        this.healthManager = healthManager;
        this.discoveryCache = discoveryCache;
        this.dataType = SiriDataType.VEHICLE_MONITORING;
    }

    public void processIncomingVMFromGTFSRT(Exchange e) {
        InputStream xml = e.getIn().getBody(InputStream.class);
        Long inboundTime = e.getIn().getHeader(INBOUND_TIME_HEADER_NAME, Long.class);
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
            List<String> subscriptionList = getSubscriptions(vehicleActivities);
            checkAndCreateSubscriptions(subscriptionList, datasetId, url);
            Collection<VehicleActivityStructure> ingestedVehicleJourneys = vehicleMonitoringInbound.ingestVehicleActivities(datasetId, vehicleActivities, inboundTime);
            for (VehicleActivityStructure vehicleActivity : ingestedVehicleJourneys) {
                subscriptionManager.touchSubscription(GTFSRT_VM_PREFIX + vehicleActivity.getMonitoredVehicleJourney().getLineRef().getValue(), false);
            }

            logger.info("GTFS-RT - Ingested  vehicle positions {} on {} . datasetId:{}, URL:{}", ingestedVehicleJourneys.size(), vehicleActivities.size(), datasetId, url);

        } catch (JAXBException | XMLStreamException jaxbException) {
            logger.error("Error while unmarshalling siri message from gtfsrt VM", e);
        }
    }

    /**
     * Read all vehicleActivities messages and build a list of subscriptions that must be checked(or created if not exists)
     *
     * @param vehicleActivities The list of vehicleActivities
     * @return The list of subscription ids build by reading the vehicle activities
     */
    private List<String> getSubscriptions(List<VehicleActivityStructure> vehicleActivities) {
        return vehicleActivities.stream()
                .filter(vehicleActivity -> vehicleActivity.getMonitoredVehicleJourney() != null && vehicleActivity.getMonitoredVehicleJourney().getLineRef() != null)
                .map(vehicleActivity -> vehicleActivity.getMonitoredVehicleJourney().getLineRef().getValue())
                .collect(Collectors.toList());
    }

    /***
     * Read the list of subscription ids and for each, check if it exists. If not, a new subscription is created
     * @param subscriptionsList
     *  The list of subscription ids
     */
    private void checkAndCreateSubscriptions(List<String> subscriptionsList, String datasetId, String url) {

        for (String subscriptionId : subscriptionsList) {
            if (subscriptionManager.isGTFSRTSubscriptionExisting(prefix + datasetId + "_" + subscriptionId))
                //A subscription is already existing for this Line. No need to create one
                continue;

            discoveryCache.addLine(datasetId, subscriptionId);
            createNewSubscription(subscriptionId, datasetId, url);
            subscriptionManager.addGTFSRTSubscription(prefix + datasetId + "_" + subscriptionId);
        }
    }

    /**
     * Create a new subscription for the id given in parameter
     *
     * @param objectRef The object id for which a subscription must be created
     */
    private void createNewSubscription(String objectRef, String datasetId, String url) {

        // 1 subscription by type (SM/ET/SX/VM) and by datasetId
        String globalSubscriptionId = prefix + datasetId;
        SubscriptionSetup globalSub = subscriptionManager.getSubscriptionBySubscriptionId(globalSubscriptionId);

        if (globalSub != null) {
            if (!globalSub.getLineRefValues().contains(objectRef)) {
                globalSub.getLineRefValues().add(objectRef);
            }
        } else {
            SubscriptionSetup setup = createStandardSubscription(objectRef, datasetId, url);
            setup.setName(globalSubscriptionId);
            setup.setSubscriptionId(globalSubscriptionId);
            setup.getLineRefValues().add(objectRef);
            subscriptionManager.addSubscription(globalSubscriptionId, setup);
        }
    }


}
