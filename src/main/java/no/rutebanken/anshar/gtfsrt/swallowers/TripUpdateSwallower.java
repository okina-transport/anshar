package no.rutebanken.anshar.gtfsrt.swallowers;

import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.gtfsrt.mappers.TripUpdateMapper;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.EstimatedVehicleJourney;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import java.util.stream.Collectors;

/**
 * Class to handle and ingest tripUpdate data
 * TripUpdate (GTFS-RT) = Estimated time table (SIRI)
 */

@Component
public class TripUpdateSwallower extends AbstractSwallower {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private SiriHandler handler;



    public TripUpdateSwallower() {
        prefix = "GTFS-RT_ET_";
        dataType = SiriDataType.ESTIMATED_TIMETABLE;
        requestType = RequestType.GET_ESTIMATED_TIMETABLE;
    }


    /**
     * Main function to ingest data : take a complete GTFS-RT object (FeedMessage), read and map data about TripUpdates and ingest it
     *
     * @param completeGTFSRTMessage
     *      The complete message (GTFS-RT format)
     */
    public void ingestTripUpdateData(GtfsRealtime.FeedMessage completeGTFSRTMessage ){
        List<EstimatedVehicleJourney> estimatedVehicleJourneys = buildEstimatedVehicleJourneyList(completeGTFSRTMessage);
        List<String> subscriptionList = getSubscriptions(estimatedVehicleJourneys) ;
        checkAndCreateSubscriptions(subscriptionList);

        Collection<EstimatedVehicleJourney> ingestedEstimatedTimetables = handler.ingestEstimatedTimeTables("GTFS-RT", estimatedVehicleJourneys);

        for (EstimatedVehicleJourney estimatedVehicleJourney : ingestedEstimatedTimetables) {
            subscriptionManager.touchSubscription(prefix + estimatedVehicleJourney.getDatedVehicleJourneyRef().getValue());
        }

        logger.info("Ingested trip updates {} on {} ", ingestedEstimatedTimetables.size(), estimatedVehicleJourneys.size());

    }

    /**
     * Read the complete GTS-RT message and build a list of estimated journeys to integrate
     * @param feedMessage
     *         The complete message (GTFS-RT format)
     * @return
     *         A list of estimated vehicle journeys, build by mapping trip updates from GTFS-RT message
     */
    private List<EstimatedVehicleJourney> buildEstimatedVehicleJourneyList(GtfsRealtime.FeedMessage feedMessage) {
        List<EstimatedVehicleJourney> estimatedVehicleJourneys = new ArrayList<>();


        for (GtfsRealtime.FeedEntity feedEntity : feedMessage.getEntityList()) {
            if (feedEntity.getTripUpdate() == null)
                continue;

            EstimatedVehicleJourney estimatedVehicleJourney = TripUpdateMapper.mapVehicleJourneyFromTripUpdate(feedEntity.getTripUpdate());
            estimatedVehicleJourneys.add(estimatedVehicleJourney);
        }
        return estimatedVehicleJourneys;

    }


    /**
     * Read all estimated timetable messages and build a list of subscriptions that must be checked(or created if not exists)
     * @param estimatedVehicleJourneys
     *      The list of estimated time tables
     * @return
     *      The list of subscription ids build by reading the estimated time tables
     */
    private List<String> getSubscriptions(List<EstimatedVehicleJourney> estimatedVehicleJourneys) {
        return estimatedVehicleJourneys.stream()
                .filter(estimatedVehicleJourney -> estimatedVehicleJourney.getDatedVehicleJourneyRef() != null &&  estimatedVehicleJourney.getDatedVehicleJourneyRef().getValue() != null)
                .map(estimatedVehicleJourney -> estimatedVehicleJourney.getDatedVehicleJourneyRef().getValue())
                .collect(Collectors.toList());
    }


    /***
     * Read the list of subscription ids and for each, check if it exists. If not, a new subscription is created
     * @param subscriptionsList
     *  The list of subscription ids
     */
    private void checkAndCreateSubscriptions(List<String> subscriptionsList) {

        for (String vehicleJourneyRef : subscriptionsList) {
            if (subscriptionManager.isSubscriptionExisting(prefix + vehicleJourneyRef))
                //A subscription is already existing for this vehicle journey. No need to create one
                continue;
            createNewSubscriptionForVehicleJourneyRef(vehicleJourneyRef);
        }
    }

    /**
     * Create a new subscription for the vehicleRef given in parameter
     * @param vehicleJourneyRef
     *      The vehicle journey for which a subscription must be created
     */
    private void createNewSubscriptionForVehicleJourneyRef(String vehicleJourneyRef){
        SubscriptionSetup setup = createStandardSubscription(vehicleJourneyRef);
        subscriptionManager.addSubscription(vehicleJourneyRef,setup);
    }

}
