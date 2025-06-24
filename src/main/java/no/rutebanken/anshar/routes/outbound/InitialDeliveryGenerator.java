package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.data.*;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.outbound.SituationExchangeOutbound;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.util.SiriUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.FacilityConditionStructure;
import uk.org.siri.siri21.GeneralMessage;
import uk.org.siri.siri21.Siri;

import java.util.*;

@Component
public class InitialDeliveryGenerator {

    private static final Logger logger = LoggerFactory.getLogger(InitialDeliveryGenerator.class);


    @Autowired
    private Situations situations;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private MonitoredStopVisits monitoredStopVisits;

    @Autowired
    private GeneralMessages generalMessages;

    @Autowired
    private FacilityMonitoring facilityMonitoring;

    @Autowired
    private SituationExchangeOutbound situationExchangeOutbound;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private SiriHelper siriHelper;


    public Map<String, Siri> findInitialDeliveriesByDataset(OutboundSubscriptionSetup subscriptionRequest) {
        Map<String, Siri> results = new HashMap<>();
        switch (subscriptionRequest.getSubscriptionType()) {
            case STOP_MONITORING:
                Set<String> searchedStopIds = siriHelper.getSeachedStopIds(subscriptionRequest);
                return getSMinitialDeliveries(subscriptionRequest, searchedStopIds);
            case ESTIMATED_TIMETABLE:
                return getETinitialDeliveries(subscriptionRequest);
            case VEHICLE_MONITORING:
                return getVMinitialDeliveries(subscriptionRequest);
            case GENERAL_MESSAGE:
                return getGMinitialDeliveries(subscriptionRequest);
        }

        return results;
    }

    private Map<String, Siri> getGMinitialDeliveries(OutboundSubscriptionSetup subscriptionRequest) {

        Set<String> datasetList = generalMessages.getAllDatasetIds();
        Map<String, Siri> results = new HashMap<>();

        for (String dataset : datasetList) {
            Collection<GeneralMessage> messages = generalMessages.getAll(dataset);
            Siri delivery = siriObjectFactory.createGMServiceDelivery(messages);
            results.put(dataset, delivery);
        }
        return results;
    }

    private Map<String, Siri> getVMinitialDeliveries(OutboundSubscriptionSetup subscriptionRequest) {
        Map<String, Siri> results = new HashMap<>();

        if (subscriptionRequest.getDatasetId() != null) {
            Siri delivery = getVMinitialDeliveryForDataset(subscriptionRequest, subscriptionRequest.getDatasetId());
            results.put(subscriptionRequest.getDatasetId(), delivery);
        } else {
            // no dataset specified in subscription. Need to request all datasets
            Set<String> datasetIds = vehicleActivities.getAllDatasetIds();
            for (String datasetId : datasetIds) {
                Siri delivery = getVMinitialDeliveryForDataset(subscriptionRequest, datasetId);
                if (SiriUtils.hasDataOfType(delivery, SiriDataType.VEHICLE_MONITORING)) {
                    results.put(subscriptionRequest.getDatasetId(), delivery);
                }
            }
        }

        return results;

    }

    private Siri getVMinitialDeliveryForDataset(OutboundSubscriptionSetup subscriptionRequest, String datasetId) {
        Set<String> filteredLines = siriHelper.getLineFiltersForDatasetId(subscriptionRequest, datasetId);
        return vehicleActivities.createServiceDelivery(subscriptionRequest.getRequestorRef(), datasetId, "initialDelivery", new ArrayList<>(), Integer.MAX_VALUE, filteredLines, new HashSet<>());
    }

    private Map<String, Siri> getSMinitialDeliveries(OutboundSubscriptionSetup subscriptionRequest, Set<String> searchedStopIds) {
        Map<String, Siri> results = new HashMap<>();
        if (StringUtils.isNotEmpty(subscriptionRequest.getDatasetId())) {
            Siri delivery = monitoredStopVisits.createServiceDelivery(subscriptionRequest.getRequestorRef(), subscriptionRequest.getDatasetId(), "initialDelivery", Integer.MAX_VALUE, searchedStopIds);
            results.put(subscriptionRequest.getDatasetId(), delivery);
        } else {
            Set<String> datasetList = monitoredStopVisits.getAllDatasetIds();
            for (String datasetId : datasetList) {
                Siri delivery = monitoredStopVisits.createServiceDelivery(subscriptionRequest.getRequestorRef(), datasetId, "initialDelivery", Integer.MAX_VALUE, searchedStopIds);
                if (SiriUtils.hasDataOfType(delivery, SiriDataType.STOP_MONITORING)) {
                    results.put(datasetId, delivery);
                }
            }
        }
        return results;
    }

    private Map<String, Siri> getETinitialDeliveries(OutboundSubscriptionSetup subscriptionRequest) {
        Map<String, Siri> results = new HashMap<>();

        if (subscriptionRequest.getDatasetId() != null) {
            Siri delivery = getETinitialDeliveryForDataset(subscriptionRequest, subscriptionRequest.getDatasetId());
            results.put(subscriptionRequest.getDatasetId(), delivery);
        } else {
            // no dataset specified in subscription. Need to request all datasets
            Set<String> datasetIds = estimatedTimetables.getAllDatasetIds();
            for (String datasetId : datasetIds) {
                Siri delivery = getETinitialDeliveryForDataset(subscriptionRequest, datasetId);
                if (SiriUtils.hasDataOfType(delivery, SiriDataType.ESTIMATED_TIMETABLE)) {
                    results.put(subscriptionRequest.getDatasetId(), delivery);
                }
            }
        }

        return results;
    }

    private Siri getETinitialDeliveryForDataset(OutboundSubscriptionSetup subscriptionRequest, String datasetId) {
        Set<String> filteredLines = siriHelper.getLineFiltersForDatasetId(subscriptionRequest, datasetId);
        return estimatedTimetables.createServiceDelivery(subscriptionRequest.getRequestorRef(), datasetId, "initialDelivery", new ArrayList<>(), Integer.MAX_VALUE, -1, filteredLines);
    }

    public Siri findInitialDeliveryData(OutboundSubscriptionSetup subscriptionRequest, OutboundIdMappingPolicy policy) {
        Siri delivery = null;

        switch (subscriptionRequest.getSubscriptionType()) {

            case SITUATION_EXCHANGE:
                delivery = situationExchangeOutbound.createServiceDelivery(subscriptionRequest.getRequestorRef(), subscriptionRequest.getDatasetId(), subscriptionRequest.getClientTrackingName(), policy, 1000);
                logger.info("Initial SX-delivery: {} elements", delivery.getServiceDelivery().getSituationExchangeDeliveries().size());
                break;

            case FACILITY_MONITORING:
                Collection<FacilityConditionStructure> facility = facilityMonitoring.getAll(subscriptionRequest.getDatasetId());
                logger.info("Initial FM-delivery: {} elements", facility.size());
                delivery = siriObjectFactory.createFMServiceDelivery(facility);
                break;
        }
        return delivery;
    }
}
