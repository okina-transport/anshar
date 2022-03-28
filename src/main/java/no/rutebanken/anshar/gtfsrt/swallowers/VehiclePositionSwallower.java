package no.rutebanken.anshar.gtfsrt.swallowers;

import com.google.transit.realtime.GtfsRealtime;

import io.micrometer.core.instrument.util.StringUtils;
import no.rutebanken.anshar.gtfsrt.mappers.VehiclePositionMapper;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.org.siri.siri20.VehicleActivityStructure;


import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class to handle and ingest vehicleposition data
 * vehicleposition (GTFS-RT) = VehicleActivity (SIRI)
 */

@Component
public class VehiclePositionSwallower extends AbstractSwallower {

    private Logger logger = LoggerFactory.getLogger(this.getClass());


    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private SiriHandler handler;


    public VehiclePositionSwallower() {
        prefix = "GTFS-RT_VM_";
        dataType = SiriDataType.VEHICLE_MONITORING;
        requestType = RequestType.GET_VEHICLE_MONITORING;
    }

    /**
     * Main function to ingest data : take a complete GTFS-RT object (FeedMessage), read and map data about vehiclePositions and ingest it
     *
     * @param completeGTFSRTMessage
     *      The complete message (GTFS-RT format)
     */
    public void ingestVehiclePositionData(GtfsRealtime.FeedMessage completeGTFSRTMessage ){
        List<VehicleActivityStructure> vehicleActivities = buildVehicleActivityList(completeGTFSRTMessage);
        int totalVehicleActivities = vehicleActivities.size();


        long emptyVehicleMonitoringRef = vehicleActivities.stream().filter(vehicleAct -> StringUtils.isEmpty(vehicleAct.getVehicleMonitoringRef().getValue())).count();

        if (emptyVehicleMonitoringRef > 0){
            logger.info("Skipping empty vehicleMonitoringRefs: " + emptyVehicleMonitoringRef + "/" + totalVehicleActivities);
        }

        List<VehicleActivityStructure> availableActivities = vehicleActivities.stream()
                                                                            .filter(vehicleAct -> StringUtils.isNotEmpty(vehicleAct.getVehicleMonitoringRef().getValue()))
                                                                            .collect(Collectors.toList());

        if (availableActivities.size() == 0){
            logger.info("No vehicle activities in GTFS RT feed");
            return;
        }

        List<String> subscriptionList = getSubscriptions(availableActivities);
        checkAndCreateSubscriptions(subscriptionList);
        Collection<VehicleActivityStructure> ingestedVehicleJourneys = handler.ingestVehicleActivities("GTFS-RT", availableActivities);




        for ( VehicleActivityStructure  vehicleActivity : ingestedVehicleJourneys) {
            subscriptionManager.touchSubscription(prefix + vehicleActivity.getMonitoredVehicleJourney().getLineRef().getValue(), false);
        }

        logger.info("Ingested vehicle positions {} on {} ", ingestedVehicleJourneys.size(), availableActivities.size());
    }

    /**
     * Read the complete GTS-RT message and build a list of vehicle activities to integrate
     * @param feedMessage
     *         The complete message (GTFS-RT format)
     * @return
     *         A list of vehicle activities, build by mapping vehicle positions from GTFS-RT message
     */
    private List<VehicleActivityStructure> buildVehicleActivityList(GtfsRealtime.FeedMessage feedMessage) {
        List<VehicleActivityStructure> vehicleActivities = new ArrayList<>();


        for (GtfsRealtime.FeedEntity feedEntity : feedMessage.getEntityList()) {
            if (feedEntity.getVehicle() == null)
                continue;

            VehicleActivityStructure vehicleActivity = VehiclePositionMapper.mapVehicleActivityFromVehiclePosition(feedEntity.getVehicle());
            ZonedDateTime dateTime = ZonedDateTime.now();
            vehicleActivity.setValidUntilTime(dateTime.plusMinutes(10));
            vehicleActivities.add(vehicleActivity);
        }
        return vehicleActivities;
    }


    /**
     * Read all vehicleActivities messages and build a list of subscriptions that must be checked(or created if not exists)
     * @param vehicleActivities
     *      The list of vehicleActivities
     * @return
     *      The list of subscription ids build by reading the vehicle activities
     */
    private List<String> getSubscriptions(List<VehicleActivityStructure> vehicleActivities) {
        return vehicleActivities.stream()
                .filter(vehicleActivity -> vehicleActivity.getMonitoredVehicleJourney() != null &&  vehicleActivity.getMonitoredVehicleJourney().getLineRef() != null)
                .map(vehicleActivity -> vehicleActivity.getMonitoredVehicleJourney().getLineRef().getValue())
                .collect(Collectors.toList());
    }


    /***
     * Read the list of subscription ids and for each, check if it exists. If not, a new subscription is created
     * @param subscriptionsList
     *  The list of subscription ids
     */
    private void checkAndCreateSubscriptions(List<String> subscriptionsList) {

        for (String subscriptionId : subscriptionsList) {
            if (subscriptionManager.isSubscriptionExisting(prefix + subscriptionId))
                //A subscription is already existing for this Line. No need to create one
                continue;
            createNewSubscription(subscriptionId);
        }
    }

    /**
     * Create a new subscription for the id given in parameter
     * @param objectRef
     *      The object id for which a subscription must be created
     */
    private void createNewSubscription(String objectRef){
        SubscriptionSetup setup = createStandardSubscription(objectRef);
        subscriptionManager.addSubscription(objectRef,setup);
    }

}
