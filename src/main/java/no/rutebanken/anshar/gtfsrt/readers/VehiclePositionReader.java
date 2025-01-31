package no.rutebanken.anshar.gtfsrt.readers;

import com.google.transit.realtime.GtfsRealtime;
import net.logstash.logback.encoder.org.apache.commons.lang3.StringUtils;
import no.rutebanken.anshar.data.DiscoveryCache;
import no.rutebanken.anshar.gtfsrt.mappers.VehiclePositionMapper;
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
import uk.org.siri.siri20.ServiceDelivery;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.VehicleActivityStructure;
import uk.org.siri.siri20.VehicleMonitoringDeliveryStructure;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.validation.validators.Constants.GTFSRT_VM_PREFIX;

/**
 * Class to handle and ingest vehicleposition data
 * vehicleposition (GTFS-RT) = VehicleActivity (SIRI)
 */

@Component
public class VehiclePositionReader extends AbstractSwallower {

    private static final Logger logger = LoggerFactory.getLogger(VehiclePositionReader.class);


    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private SiriHandler handler;

    @Produce(uri = "direct:send.vm.to.realtime.server")
    protected ProducerTemplate gtfsrtVmProducer;

    @Autowired
    private DiscoveryCache discoveryCache;


    public VehiclePositionReader() {
        prefix = GTFSRT_VM_PREFIX;
        dataType = SiriDataType.VEHICLE_MONITORING;
        requestType = RequestType.GET_VEHICLE_MONITORING;
    }

    /**
     * Processes and ingests GTFS-Realtime vehicle position data, converting it into structured SIRI data.
     * This method builds vehicle activity records, manages subscriptions, and sends the structured data.
     *
     * @param datasetId The identifier of the dataset associated with the GTFS-Realtime feed.
     * @param routeIdList A list of route IDs used to filter relevant vehicle positions.
     * @param completeGTFSRTMessage The complete GTFS-Realtime {@link GtfsRealtime.FeedMessage} containing vehicle position data.
     */
    public void ingestVehiclePositionData(String datasetId, List<String> routeIdList, GtfsRealtime.FeedMessage completeGTFSRTMessage) {
        List<VehicleActivityStructure> vehicleActivities = buildVehicleActivityList(completeGTFSRTMessage, routeIdList);


        if (vehicleActivities.size() == 0) {
            logger.info("No vehicle activities in GTFS RT feed");
            return;
        }

        List<String> subscriptionList = getSubscriptions(vehicleActivities);
        checkAndCreateSubscriptions(subscriptionList, datasetId);
        buildSiriAndSend(vehicleActivities, datasetId);
    }

    private void buildSiriAndSend(List<VehicleActivityStructure> vehicleActivities, String datasetId) {
        if (vehicleActivities.isEmpty()) {
            logger.info("no vehicleActivities to ingest");
            return;
        }

        Siri siri = new Siri();
        ServiceDelivery serviceDel = new ServiceDelivery();
        VehicleMonitoringDeliveryStructure vehicleMonStruct = new VehicleMonitoringDeliveryStructure();
        vehicleMonStruct.getVehicleActivities().addAll(vehicleActivities);
        serviceDel.getVehicleMonitoringDeliveries().add(vehicleMonStruct);
        siri.setServiceDelivery(serviceDel);
        sendToRealTimeServer(gtfsrtVmProducer, siri, datasetId);
    }

    /**
     * Builds a list of {@link VehicleActivityStructure} instances from a GTFS-Realtime {@link GtfsRealtime.FeedMessage}.
     * This method processes vehicle position updates from the feed message and converts them into structured vehicle activity records.
     *
     * @param feedMessage The GTFS-Realtime {@link GtfsRealtime.FeedMessage} containing vehicle position data.
     * @param routeIdList A list of route IDs used to filter relevant vehicle activities.
     * @return A list of {@link VehicleActivityStructure} objects representing structured vehicle activity data.
     */
    private List<VehicleActivityStructure> buildVehicleActivityList(GtfsRealtime.FeedMessage feedMessage, List<String> routeIdList) {
        List<VehicleActivityStructure> vehicleActivities = new ArrayList<>();


        for (GtfsRealtime.FeedEntity feedEntity : feedMessage.getEntityList()) {
            if (feedEntity.getVehicle() == null)
                continue;

            VehicleActivityStructure vehicleActivity = VehiclePositionMapper.mapVehicleActivityFromVehiclePosition(feedEntity.getVehicle(), routeIdList);
            if (vehicleActivity == null) {
                continue;
            }
            if (isEmptyVehicleRef(vehicleActivity) && isEmptyLocation(vehicleActivity)) {
                continue;
            }
            ZonedDateTime dateTime = ZonedDateTime.now();
            vehicleActivity.setValidUntilTime(dateTime.plusMinutes(10));
            vehicleActivities.add(vehicleActivity);
        }
        return vehicleActivities;
    }

    private boolean isEmptyLocation(VehicleActivityStructure vehicleActivity) {
        VehicleActivityStructure.MonitoredVehicleJourney vehicleJourney = vehicleActivity.getMonitoredVehicleJourney();
        return vehicleJourney.getVehicleLocation() == null || vehicleJourney.getVehicleLocation().getLatitude().compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean isEmptyVehicleRef(VehicleActivityStructure vehicleActivity) {
        return vehicleActivity.getVehicleMonitoringRef() == null || StringUtils.isEmpty(vehicleActivity.getVehicleMonitoringRef().getValue());
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
    private void checkAndCreateSubscriptions(List<String> subscriptionsList, String datasetId) {

        for (String subscriptionId : subscriptionsList) {
            if (subscriptionManager.isGTFSRTSubscriptionExisting(prefix + datasetId + "_" + subscriptionId))
                //A subscription is already existing for this Line. No need to create one
                continue;

            discoveryCache.addLine(datasetId, subscriptionId);
            createNewSubscription(subscriptionId, datasetId);
            subscriptionManager.addGTFSRTSubscription(prefix + datasetId + "_" + subscriptionId);
        }
    }

    /**
     * Create a new subscription for the id given in parameter
     *
     * @param objectRef The object id for which a subscription must be created
     */
    private void createNewSubscription(String objectRef, String datasetId) {

        // 1 subscription by type (SM/ET/SX/VM) and by datasetId
        String globalSubscriptionId = prefix + datasetId;
        SubscriptionSetup globalSub = subscriptionManager.getSubscriptionBySubscriptionId(globalSubscriptionId);

        if (globalSub != null) {
            if (!globalSub.getLineRefValues().contains(objectRef)) {
                globalSub.getLineRefValues().add(objectRef);
            }
        } else {
            SubscriptionSetup setup = createStandardSubscription(objectRef, datasetId);
            setup.setName(globalSubscriptionId);
            setup.setSubscriptionId(globalSubscriptionId);
            setup.getLineRefValues().add(objectRef);
            subscriptionManager.addSubscription(globalSubscriptionId, setup);
        }
    }

}
