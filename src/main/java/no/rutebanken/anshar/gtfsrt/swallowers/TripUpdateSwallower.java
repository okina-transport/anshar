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
import uk.org.siri.siri20.MonitoredStopVisit;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
    }


    /**
     * Main function to ingest data : take a complete GTFS-RT object (FeedMessage), read and map data about TripUpdates and ingest it
     *
     * @param completeGTFSRTMessage
     *      The complete message (GTFS-RT format)
     */
    public void ingestTripUpdateData(String datasetId, GtfsRealtime.FeedMessage completeGTFSRTMessage ){

        //// ESTIMATED TIME TABLES
        List<EstimatedVehicleJourney> estimatedVehicleJourneys = buildEstimatedVehicleJourneyList(completeGTFSRTMessage);
        List<String> etSubscriptionList = getSubscriptionsFromEstimatedTimeTables(estimatedVehicleJourneys) ;
        checkAndCreateSubscriptions(etSubscriptionList,"GTFS-RT_ET_", SiriDataType.ESTIMATED_TIMETABLE, RequestType.GET_ESTIMATED_TIMETABLE, datasetId);

        Collection<EstimatedVehicleJourney> ingestedEstimatedTimetables = handler.ingestEstimatedTimeTables(datasetId, estimatedVehicleJourneys);

        for (EstimatedVehicleJourney estimatedVehicleJourney : ingestedEstimatedTimetables) {
            subscriptionManager.touchSubscription(prefix + estimatedVehicleJourney.getDatedVehicleJourneyRef().getValue(), false);
        }

        logger.info("Ingested estimated time tables {} on {} ", ingestedEstimatedTimetables.size(), estimatedVehicleJourneys.size());



        //// STOP VISITS
        List<MonitoredStopVisit> stopVisits = buildStopVisitList(completeGTFSRTMessage);
        List<String> visitSubscriptionList = getSubscriptionsFromVisits(stopVisits) ;
        checkAndCreateSubscriptions(visitSubscriptionList, "GTFS-RT_SM_", SiriDataType.STOP_MONITORING, RequestType.GET_STOP_MONITORING, datasetId);

        Collection<MonitoredStopVisit> ingestedVisits = handler.ingestStopVisits(datasetId, stopVisits);

        for (MonitoredStopVisit visit : ingestedVisits) {
            subscriptionManager.touchSubscription("GTFS-RT_SM_" + visit.getMonitoringRef().getValue(),false);
        }

        logger.info("Ingested stop Times {} on {} ", ingestedVisits.size(), stopVisits.size());
    }


    /**
     * Read all stopVisit messages and build a list of subscriptions that must be checked(or created if not exists)
     * @param stopVisits
     *      The list of stop visits
     * @return
     *      The list of subscription ids build by reading the visits
     */
    private List<String> getSubscriptionsFromVisits(List<MonitoredStopVisit> stopVisits) {

        return stopVisits.stream()
                            .filter(visit -> visit.getMonitoringRef() != null &&  visit.getMonitoringRef().getValue() != null)
                            .map(visit -> visit.getMonitoringRef().getValue())
                            .collect(Collectors.toList());


    }

    /**
     * Read the complete GTS-RT message and build a list of stop visits to integrate
     * @param feedMessage
     *         The complete message (GTFS-RT format)
     * @return
     *         A list of visits, build by mapping trip updates from GTFS-RT message
     */
    private List<MonitoredStopVisit> buildStopVisitList(GtfsRealtime.FeedMessage feedMessage) {
        List<MonitoredStopVisit> stopVisits = new ArrayList<>();

        long recordedAtTimeLong = feedMessage.getHeader().getTimestamp();
        ZonedDateTime recordedAtTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(recordedAtTimeLong * 1000), ZoneId.systemDefault());

        for (GtfsRealtime.FeedEntity feedEntity : feedMessage.getEntityList()) {
            if (feedEntity.getTripUpdate() == null)
                continue;

            List<MonitoredStopVisit> currentStopVisitList = TripUpdateMapper.mapStopVisitFromTripUpdate(feedEntity.getTripUpdate());
            stopVisits.addAll(currentStopVisitList);
        }

        stopVisits.forEach(stopVisit -> stopVisit.setRecordedAtTime(recordedAtTime));

        return stopVisits;

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
    private void checkAndCreateSubscriptions(List<String> subscriptionsList, String customPrefix, SiriDataType dataType, RequestType requestType, String datasetId) {

        for (String subscriptionId : subscriptionsList) {
            if (subscriptionManager.isSubscriptionExisting(customPrefix + subscriptionId))
                //A subscription is already existing for this vehicle journey. No need to create one
                continue;
            createNewSubscription(subscriptionId, customPrefix, dataType, requestType, datasetId);
        }
    }

    /**
     * Create a new subscription for the ref given in parameter
     * @param ref
     *      The id for which a subscription must be created
     * @param customPrefix
     * @param dataType
     * @param requestType
     */
    private void createNewSubscription(String ref, String customPrefix, SiriDataType dataType, RequestType requestType, String datasetId){
        SubscriptionSetup setup = createStandardSubscription(ref, datasetId);
        String subscriptionId = customPrefix + ref;
        setup.setName(subscriptionId);
        setup.setSubscriptionType(dataType);
        setup.setSubscriptionId(subscriptionId);
        setup.getUrlMap().clear();
        setup.getUrlMap().put(requestType,url);
        subscriptionManager.addSubscription(ref, setup);
    }






}
