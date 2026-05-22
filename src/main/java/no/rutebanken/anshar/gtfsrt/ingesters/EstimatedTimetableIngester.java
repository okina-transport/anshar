package no.rutebanken.anshar.gtfsrt.ingesters;

import no.rutebanken.anshar.data.DiscoveryCache;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.siri.handlers.inbound.EstimatedTimetableInbound;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.Siri;

import jakarta.xml.bind.JAXBException;

import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.validation.validators.Constants.*;

@Service
public class EstimatedTimetableIngester extends AbstractIngester {

    private static final Logger logger = LoggerFactory.getLogger(EstimatedTimetableIngester.class);


    private final SubscriptionManager subscriptionManager;
    private final EstimatedTimetableInbound estimatedTimetableInbound;
    private final HealthManager healthManager;
    private final DiscoveryCache discoveryCache;

    public EstimatedTimetableIngester(SubscriptionManager subscriptionManager, EstimatedTimetableInbound estimatedTimetableInbound, HealthManager healthManager, DiscoveryCache discoveryCache) {
        this.subscriptionManager = subscriptionManager;
        this.estimatedTimetableInbound = estimatedTimetableInbound;
        this.healthManager = healthManager;
        this.discoveryCache = discoveryCache;
        this.dataType = SiriDataType.ESTIMATED_TIMETABLE;
    }

    public void processIncomingETFromGTFSRT(Exchange e) {
        InputStream xml = e.getIn().getBody(InputStream.class);
        Long inboundTime = e.getIn().getHeader(INBOUND_TIME_HEADER_NAME, Long.class);
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
            List<String> etSubscriptionList = getSubscriptionsFromEstimatedTimeTables(estimatedVehicleJourneys);
            checkAndCreateSubscriptions(etSubscriptionList, GTFSRT_ET_PREFIX, SiriDataType.ESTIMATED_TIMETABLE, RequestType.GET_ESTIMATED_TIMETABLE, datasetId, url);
            Collection<EstimatedVehicleJourney> ingestedEstimatedTimetables = estimatedTimetableInbound.ingestEstimatedTimeTables(datasetId, estimatedVehicleJourneys, inboundTime);

            for (EstimatedVehicleJourney estimatedVehicleJourney : ingestedEstimatedTimetables) {
                subscriptionManager.touchSubscription(GTFSRT_ET_PREFIX + estimatedVehicleJourney.getDatedVehicleJourneyRef().getValue(), false);
            }

            logger.info("GTFS-RT - Ingested estimated time tables {} on {}. datasetId:{}, URL:{}", ingestedEstimatedTimetables.size(), estimatedVehicleJourneys.size(), datasetId, url);

        } catch (JAXBException | XMLStreamException jaxbException) {
            logger.error("Error while unmarshalling siri message from gtfsrt ET", e);
        }
    }

    /**
     * Read all estimated timetable messages and build a list of subscriptions that must be checked(or created if not exists)
     *
     * @param estimatedVehicleJourneys The list of estimated time tables
     * @return The list of subscription ids build by reading the estimated time tables
     */
    private List<String> getSubscriptionsFromEstimatedTimeTables(List<EstimatedVehicleJourney> estimatedVehicleJourneys) {
        return estimatedVehicleJourneys.stream()
                .filter(estimatedVehicleJourney -> estimatedVehicleJourney.getDatedVehicleJourneyRef() != null && estimatedVehicleJourney.getDatedVehicleJourneyRef().getValue() != null)
                .map(estimatedVehicleJourney -> estimatedVehicleJourney.getDatedVehicleJourneyRef().getValue())
                .collect(Collectors.toList());
    }


    /***
     * Read the list of subscription ids and for each, check if it exists. If not, a new subscription is created
     * @param subscriptionsList
     * @param customPrefix
     *  The list of subscription ids
     */
    private void checkAndCreateSubscriptions(List<String> subscriptionsList, String customPrefix, SiriDataType dataType, RequestType requestType, String datasetId, String url) {

        for (String subscriptionId : subscriptionsList) {
            if (subscriptionManager.isGTFSRTSubscriptionExisting(customPrefix + datasetId + "_" + subscriptionId))
                //A subscription is already existing for this vehicle journey. No need to create one
                continue;

            if (dataType.equals(SiriDataType.STOP_MONITORING)) {
                discoveryCache.addStop(datasetId, subscriptionId);
            }

            createNewSubscription(subscriptionId, customPrefix, dataType, requestType, datasetId, url);
            subscriptionManager.addGTFSRTSubscription(customPrefix + datasetId + "_" + subscriptionId);
        }
    }

    /**
     * Create a new subscription for the ref given in parameter
     *
     * @param ref          The id for which a subscription must be created
     * @param customPrefix
     * @param dataType
     * @param requestType
     */
    private void createNewSubscription(String ref, String customPrefix, SiriDataType dataType, RequestType requestType, String datasetId, String url) {

        // 1 subscription by type (SM/ET/SX/VM) and by datasetId
        String globalSubscriptionId = customPrefix + datasetId;
        SubscriptionSetup globalSub = subscriptionManager.getSubscriptionBySubscriptionId(globalSubscriptionId);

        if (globalSub != null) {
            if (!globalSub.getStopMonitoringRefValues().contains(ref)) {
                globalSub.getStopMonitoringRefValues().add(ref);
            }
        } else {
            SubscriptionSetup setup = createStandardSubscription(ref, datasetId, url);
            setup.setName(globalSubscriptionId);
            setup.setSubscriptionType(dataType);
            setup.setSubscriptionId(globalSubscriptionId);
            setup.getUrlMap().clear();
            setup.getUrlMap().put(requestType, url);
            setup.getStopMonitoringRefValues().add(ref);
            subscriptionManager.addSubscription(globalSubscriptionId, setup);
        }
    }

}
