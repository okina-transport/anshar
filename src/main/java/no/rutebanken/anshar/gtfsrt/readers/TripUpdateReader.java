package no.rutebanken.anshar.gtfsrt.readers;

import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.api.PublishedLineNameMapping;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.DiscoveryCache;
import no.rutebanken.anshar.gtfsrt.mappers.TripUpdateMapper;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.*;

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

    private final SubscriptionManager subscriptionManager;

    private final AnsharConfiguration configuration;

    private final TripUpdateMapper tripUpdateMapper;

    private final DiscoveryCache discoveryCache;

    @Produce("direct:send.et.to.realtime.server")
    protected ProducerTemplate gtfsrtEtProducer;

    @Produce("direct:send.sm.to.realtime.server")
    protected ProducerTemplate gtfsrtSmProducer;

    public TripUpdateReader(SubscriptionManager subscriptionManager, AnsharConfiguration configuration, TripUpdateMapper tripUpdateMapper, DiscoveryCache discoveryCache) {
        this.subscriptionManager = subscriptionManager;
        this.configuration = configuration;
        this.tripUpdateMapper = tripUpdateMapper;
        this.discoveryCache = discoveryCache;
    }


    /**
     * Processes and ingests GTFS-Realtime trip update data, converting it into structured SIRI data formats
     * for estimated timetables and stop monitoring. This method handles subscription management and message dispatching.
     *
     * @param datasetId                The identifier of the dataset associated with the GTFS-Realtime feed.
     * @param routeIdList              A list of route IDs used to filter relevant trip updates.
     * @param completeGTFSRTMessage    The complete GTFS-Realtime {@link GtfsRealtime.FeedMessage} containing trip update data.
     * @param publishedLineNameMapping The way to map PublishedLineName from GTFS-RT TripUpdate to Siri StopMonitoring
     */
    public void ingestTripUpdateData(String datasetId, List<String> routeIdList, GtfsRealtime.FeedMessage completeGTFSRTMessage, PublishedLineNameMapping publishedLineNameMapping) {

        if (configuration.processET()) {
            //// ESTIMATED TIME TABLES
            List<EstimatedVehicleJourney> estimatedVehicleJourneys = buildEstimatedVehicleJourneyList(completeGTFSRTMessage, datasetId, routeIdList);
            List<String> etSubscriptionList = getSubscriptionsFromEstimatedTimeTables(estimatedVehicleJourneys);
            checkAndCreateSubscriptions(etSubscriptionList, GTFSRT_ET_PREFIX, SiriDataType.ESTIMATED_TIMETABLE, RequestType.GET_ESTIMATED_TIMETABLE, datasetId);
            List<String> lineList = getLines(estimatedVehicleJourneys);
            discoveryCache.addLines(datasetId, lineList);
            buildSiriAndSend(estimatedVehicleJourneys, datasetId);


        }

        if (configuration.processSM()) {
            //// STOP VISITS
            List<MonitoredStopVisit> stopVisits = buildStopVisitList(completeGTFSRTMessage, datasetId, routeIdList, publishedLineNameMapping);
            List<MonitoredStopVisitCancellation> stopCancellations = buildStopCancellationList(completeGTFSRTMessage, datasetId, routeIdList);
            List<String> visitSubscriptionList = getSubscriptionsFromVisits(stopVisits);
            checkAndCreateSubscriptions(visitSubscriptionList, GTFSRT_SM_PREFIX, SiriDataType.STOP_MONITORING, RequestType.GET_STOP_MONITORING, datasetId);
            buildSiriSMAndSend(stopVisits, stopCancellations, datasetId);
        }

    }

    private List<String> getLines(List<EstimatedVehicleJourney> estimatedVehicleJourneys) {
        return estimatedVehicleJourneys.stream()
                .filter(estimatedVehicleJourney -> estimatedVehicleJourney.getLineRef() != null && estimatedVehicleJourney.getLineRef().getValue() != null)
                .map(estimatedVehicleJourney -> estimatedVehicleJourney.getLineRef().getValue())
                .collect(Collectors.toList());
    }

    private void buildSiriSMAndSend(List<MonitoredStopVisit> stopVisits, List<MonitoredStopVisitCancellation> stopCancellation, String datasetId) {
        Siri siri = new Siri();
        ServiceDelivery serviceDel = new ServiceDelivery();
        StopMonitoringDeliveryStructure stopDelStruct = new StopMonitoringDeliveryStructure();
        stopDelStruct.getMonitoredStopVisits().addAll(stopVisits);
        stopDelStruct.getMonitoredStopVisitCancellations().addAll(stopCancellation);
        serviceDel.getStopMonitoringDeliveries().add(stopDelStruct);
        siri.setServiceDelivery(serviceDel);
        sendToRealTimeServer(gtfsrtSmProducer, siri, datasetId);
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
        sendToRealTimeServer(gtfsrtEtProducer, siri, datasetId);
    }


    /**
     * Read all stopVisit messages and build a list of subscriptions that must be checked(or created if not exists)
     *
     * @param stopVisits The list of stop visits
     * @return The list of subscription ids build by reading the visits
     */
    private List<String> getSubscriptionsFromVisits(List<MonitoredStopVisit> stopVisits) {

        return stopVisits.stream()
                .filter(visit -> visit.getMonitoringRef() != null && visit.getMonitoringRef().getValue() != null)
                .map(visit -> visit.getMonitoringRef().getValue())
                .collect(Collectors.toList());


    }

    /**
     * Builds a list of {@link MonitoredStopVisit} instances from a GTFS-Realtime {@link GtfsRealtime.FeedMessage}.
     * This method processes trip updates from the feed message, extracts stop visit information,
     * and assigns a recorded timestamp.
     *
     * @param feedMessage              The GTFS-Realtime {@link GtfsRealtime.FeedMessage} containing trip update data.
     * @param datasetId                The identifier of the dataset associated with the stop visits.
     * @param routeIdList              A list of route IDs used to filter relevant stop visits.
     * @param publishedLineNameMapping The way to map PublishedLineName from GTFS-RT TripUpdate to Siri StopMonitoring
     * @return A list of {@link MonitoredStopVisit} objects representing structured stop visit data.
     */
    private List<MonitoredStopVisit> buildStopVisitList(GtfsRealtime.FeedMessage feedMessage, String datasetId, List<String> routeIdList, PublishedLineNameMapping publishedLineNameMapping) {
        List<MonitoredStopVisit> stopVisits = new ArrayList<>();

        long recordedAtTimeLong = feedMessage.getHeader().getTimestamp();
        ZonedDateTime recordedAtTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(recordedAtTimeLong * 1000), ZoneId.systemDefault());

        for (GtfsRealtime.FeedEntity feedEntity : feedMessage.getEntityList()) {
            if (feedEntity.getTripUpdate() == null)
                continue;

            List<MonitoredStopVisit> currentStopVisitList = tripUpdateMapper.mapStopVisitFromTripUpdate(feedEntity.getTripUpdate(), datasetId, routeIdList, publishedLineNameMapping);
            stopVisits.addAll(currentStopVisitList);
        }

        stopVisits.forEach(stopVisit -> stopVisit.setRecordedAtTime(recordedAtTime));

        return stopVisits;

    }

    /**
     * Builds a list of {@link MonitoredStopVisitCancellation} instances from a GTFS-Realtime {@link GtfsRealtime.FeedMessage}.
     * This method processes trip updates from the feed message, extracts stop visit cancellations,
     * and assigns a recorded timestamp.
     *
     * @param feedMessage The GTFS-Realtime {@link GtfsRealtime.FeedMessage} containing trip update data.
     * @param datasetId   The identifier of the dataset associated with the stop visit cancellations.
     * @param routeIdList A list of route IDs used to filter relevant stop visit cancellations.
     * @return A list of {@link MonitoredStopVisitCancellation} objects representing structured stop visit cancellation data.
     */
    private List<MonitoredStopVisitCancellation> buildStopCancellationList(GtfsRealtime.FeedMessage feedMessage, String datasetId, List<String> routeIdList) {
        List<MonitoredStopVisitCancellation> stopVisitCancellations = new ArrayList<>();

        long recordedAtTimeLong = feedMessage.getHeader().getTimestamp();
        ZonedDateTime recordedAtTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(recordedAtTimeLong * 1000), ZoneId.systemDefault());

        for (GtfsRealtime.FeedEntity feedEntity : feedMessage.getEntityList()) {
            if (feedEntity.getTripUpdate() == null)
                continue;

            List<MonitoredStopVisitCancellation> currentStopCancellationList = tripUpdateMapper.mapStopCancellationFromTripUpdate(feedEntity.getTripUpdate(), datasetId, routeIdList);
            stopVisitCancellations.addAll(currentStopCancellationList);
        }

        stopVisitCancellations.forEach(stopVisit -> stopVisit.setRecordedAtTime(recordedAtTime));

        return stopVisitCancellations;
    }

    /**
     * Builds a list of {@link EstimatedVehicleJourney} instances from a GTFS-Realtime {@link GtfsRealtime.FeedMessage}.
     * This method processes trip updates from the feed message and converts them into structured estimated vehicle journeys.
     *
     * @param feedMessage The GTFS-Realtime {@link GtfsRealtime.FeedMessage} containing trip update data.
     * @param datasedId   datasetId used to check known id
     * @param routeIdList A list of route IDs used to filter relevant estimated vehicle journeys.
     * @return A list of {@link EstimatedVehicleJourney} objects representing structured estimated vehicle journey data.
     */
    private List<EstimatedVehicleJourney> buildEstimatedVehicleJourneyList(GtfsRealtime.FeedMessage feedMessage, String datasedId, List<String> routeIdList) {
        List<EstimatedVehicleJourney> estimatedVehicleJourneys = new ArrayList<>();


        for (GtfsRealtime.FeedEntity feedEntity : feedMessage.getEntityList()) {
            if (feedEntity.getTripUpdate() == null)
                continue;

            EstimatedVehicleJourney estimatedVehicleJourney = tripUpdateMapper.mapVehicleJourneyFromTripUpdate(feedEntity.getTripUpdate(), datasedId, routeIdList);
            if (estimatedVehicleJourney != null) {
                estimatedVehicleJourneys.add(estimatedVehicleJourney);
            }
        }
        return estimatedVehicleJourneys;

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
    private void checkAndCreateSubscriptions(List<String> subscriptionsList, String customPrefix, SiriDataType dataType, RequestType requestType, String datasetId) {

        for (String subscriptionId : subscriptionsList) {
            if (subscriptionManager.isGTFSRTSubscriptionExisting(customPrefix + datasetId + "_" + subscriptionId))
                //A subscription is already existing for this vehicle journey. No need to create one
                continue;

            if (dataType.equals(SiriDataType.STOP_MONITORING)) {
                discoveryCache.addStop(datasetId, subscriptionId);
            }

            createNewSubscription(subscriptionId, customPrefix, dataType, requestType, datasetId);
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
    private void createNewSubscription(String ref, String customPrefix, SiriDataType dataType, RequestType requestType, String datasetId) {

        // 1 subscription by type (SM/ET/SX/VM) and by datasetId
        String globalSubscriptionId = customPrefix + datasetId;
        SubscriptionSetup globalSub = subscriptionManager.getSubscriptionBySubscriptionId(globalSubscriptionId);

        if (globalSub != null) {
            if (!globalSub.getStopMonitoringRefValues().contains(ref)) {
                globalSub.getStopMonitoringRefValues().add(ref);
            }
        } else {
            SubscriptionSetup setup = createStandardSubscription(ref, datasetId);
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
