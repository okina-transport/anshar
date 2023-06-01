package no.rutebanken.anshar.gtfsrt.readers;

import com.google.transit.realtime.GtfsRealtime;

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

import uk.org.siri.siri20.*;


import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.validation.validators.Constants.GTFSRT_VM_PREFIX;

/**
 * Class to handle and ingest vehicleposition data
 * vehicleposition (GTFS-RT) = VehicleActivity (SIRI)
 */

@Component
public class VehiclePositionReader extends AbstractSwallower {

    private Logger logger = LoggerFactory.getLogger(this.getClass());


    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private SiriHandler handler;

    @Produce(uri = "direct:send.vm.to.realtime.server")
    protected ProducerTemplate gtfsrtVmProducer;


    public VehiclePositionReader() {
        prefix = GTFSRT_VM_PREFIX;
        dataType = SiriDataType.VEHICLE_MONITORING;
        requestType = RequestType.GET_VEHICLE_MONITORING;
    }

    /**
     * Main function to ingest data : take a complete GTFS-RT object (FeedMessage), read and map data about vehiclePositions and ingest it
     *
     * @param completeGTFSRTMessage
     *      The complete message (GTFS-RT format)
     */
    public void ingestVehiclePositionData(String datasetId, GtfsRealtime.FeedMessage completeGTFSRTMessage ){
        List<VehicleActivityStructure> vehicleActivities = buildVehicleActivityList(completeGTFSRTMessage);


        if (vehicleActivities.size() == 0){
            logger.info("No vehicle activities in GTFS RT feed");
            return;
        }

        List<String> subscriptionList = getSubscriptions(vehicleActivities);
        checkAndCreateSubscriptions(subscriptionList, datasetId);
        buildSiriAndSend(vehicleActivities, datasetId);
    }

    private void buildSiriAndSend(List<VehicleActivityStructure> vehicleActivities, String datasetId) {
        if (vehicleActivities.isEmpty()){
            logger.info("no vehicleActivities to ingest");
            return;
        }

        Siri siri = new Siri();
        ServiceDelivery serviceDel = new ServiceDelivery();
        VehicleMonitoringDeliveryStructure vehicleMonStruct = new VehicleMonitoringDeliveryStructure();
        vehicleMonStruct.getVehicleActivities().addAll(vehicleActivities);
        serviceDel.getVehicleMonitoringDeliveries().add(vehicleMonStruct);
        siri.setServiceDelivery(serviceDel);
        sendToRealTimeServer(gtfsrtVmProducer,siri, datasetId);
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
    private void checkAndCreateSubscriptions(List<String> subscriptionsList, String datasetId) {

        for (String subscriptionId : subscriptionsList) {
            if (subscriptionManager.isGTFSRTSubscriptionExisting(prefix + subscriptionId))
                //A subscription is already existing for this Line. No need to create one
                continue;
            createNewSubscription(subscriptionId, datasetId);
            subscriptionManager.addGTFSRTSubscription(subscriptionId);
        }
    }

    /**
     * Create a new subscription for the id given in parameter
     * @param objectRef
     *      The object id for which a subscription must be created
     */
    private void createNewSubscription(String objectRef, String datasetId){
        SubscriptionSetup setup = createStandardSubscription(objectRef, datasetId);
        setup.setLineRefValue(objectRef);
        subscriptionManager.addSubscription(objectRef, setup);
    }

}
