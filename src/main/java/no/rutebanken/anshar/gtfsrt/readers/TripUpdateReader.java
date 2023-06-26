package no.rutebanken.anshar.gtfsrt.readers;

import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.gtfsrt.mappers.TripUpdateMapper;
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
import uk.org.siri.siri20.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.validation.validators.Constants.GTFSRT_ET_PREFIX;
import static no.rutebanken.anshar.routes.validation.validators.Constants.GTFSRT_SM_PREFIX;

/**
 * Class to handle and ingest tripUpdate data
 * TripUpdate (GTFS-RT) = Estimated time table (SIRI)
 */

@Component
public class TripUpdateReader extends AbstractSwallower {

    private static final Logger logger = LoggerFactory.getLogger(TripUpdateReader.class);

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private TripUpdateMapper tripUpdateMapper;

    @Produce(uri = "direct:send.et.to.realtime.server")
    protected ProducerTemplate gtfsrtEtProducer;

    @Produce(uri = "direct:send.sm.to.realtime.server")
    protected ProducerTemplate gtfsrtSmProducer;


    public TripUpdateReader() {
    }


    /**
     * Main function to ingest data : take a complete GTFS-RT object (FeedMessage), read and map data about TripUpdates and ingest it
     *
     * @param completeGTFSRTMessage
     *      The complete message (GTFS-RT format)
     */
    public void ingestTripUpdateData(String datasetId, GtfsRealtime.FeedMessage completeGTFSRTMessage ){


        if (configuration.processET()){
            //// ESTIMATED TIME TABLES
            List<EstimatedVehicleJourney> estimatedVehicleJourneys = buildEstimatedVehicleJourneyList(completeGTFSRTMessage);
            List<String> etSubscriptionList = getSubscriptionsFromEstimatedTimeTables(estimatedVehicleJourneys) ;
            checkAndCreateSubscriptions(etSubscriptionList,GTFSRT_ET_PREFIX, SiriDataType.ESTIMATED_TIMETABLE, RequestType.GET_ESTIMATED_TIMETABLE, datasetId);
            buildSiriAndSend(estimatedVehicleJourneys, datasetId);

        }


        if (configuration.processSM()){
            //// STOP VISITS
            List<MonitoredStopVisit> stopVisits = buildStopVisitList(completeGTFSRTMessage, datasetId);
            List<String> visitSubscriptionList = getSubscriptionsFromVisits(stopVisits) ;
            checkAndCreateSubscriptions(visitSubscriptionList, GTFSRT_SM_PREFIX, SiriDataType.STOP_MONITORING, RequestType.GET_STOP_MONITORING, datasetId);
            buildSiriSMAndSend(stopVisits, datasetId);
        }

    }

    private void buildSiriSMAndSend(List<MonitoredStopVisit> stopVisits, String datasetId) {
        Siri siri = new Siri();
        ServiceDelivery serviceDel = new ServiceDelivery();
        StopMonitoringDeliveryStructure stopDelStruct = new StopMonitoringDeliveryStructure();
        stopDelStruct.getMonitoredStopVisits().addAll(stopVisits);
        serviceDel.getStopMonitoringDeliveries().add(stopDelStruct);
        siri.setServiceDelivery(serviceDel);
        sendToRealTimeServer(gtfsrtSmProducer,siri, datasetId);
    }

    private void buildSiriAndSend(List<EstimatedVehicleJourney> estimatedVehicleJourneys, String datasetId) {
        Siri siri = new Siri();
        ServiceDelivery serviceDel = new ServiceDelivery();
        EstimatedTimetableDeliveryStructure estimatedDelStruct = new EstimatedTimetableDeliveryStructure();
        EstimatedVersionFrameStructure estimatedFrame = new EstimatedVersionFrameStructure();
        estimatedFrame.getEstimatedVehicleJourneies().addAll(estimatedVehicleJourneys);
        estimatedDelStruct.getEstimatedJourneyVersionFrames().add(estimatedFrame);
        serviceDel.getEstimatedTimetableDeliveries().add(estimatedDelStruct);
        siri.setServiceDelivery(serviceDel);
        sendToRealTimeServer(gtfsrtEtProducer,siri, datasetId);
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
     * @param datasetId
     * @return
     *         A list of visits, build by mapping trip updates from GTFS-RT message
     */
    private List<MonitoredStopVisit> buildStopVisitList(GtfsRealtime.FeedMessage feedMessage, String datasetId) {
        List<MonitoredStopVisit> stopVisits = new ArrayList<>();

        long recordedAtTimeLong = feedMessage.getHeader().getTimestamp();
        ZonedDateTime recordedAtTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(recordedAtTimeLong * 1000), ZoneId.systemDefault());

        for (GtfsRealtime.FeedEntity feedEntity : feedMessage.getEntityList()) {
            if (feedEntity.getTripUpdate() == null)
                continue;

            List<MonitoredStopVisit> currentStopVisitList = tripUpdateMapper.mapStopVisitFromTripUpdate(feedEntity.getTripUpdate(), datasetId);
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

            EstimatedVehicleJourney estimatedVehicleJourney = tripUpdateMapper.mapVehicleJourneyFromTripUpdate(feedEntity.getTripUpdate());
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
            if (subscriptionManager.isGTFSRTSubscriptionExisting(customPrefix + subscriptionId))
                //A subscription is already existing for this vehicle journey. No need to create one
                continue;
            createNewSubscription(subscriptionId, customPrefix, dataType, requestType, datasetId);
            subscriptionManager.addGTFSRTSubscription(subscriptionId);
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
        setup.setStopMonitoringRefValue(ref);
        subscriptionManager.addSubscription(ref,setup);
    }






}
