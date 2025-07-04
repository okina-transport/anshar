package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.*;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.outbound.SituationExchangeOutbound;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import no.rutebanken.anshar.util.SiriUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.FacilityConditionStructure;
import uk.org.siri.siri21.GeneralMessage;
import uk.org.siri.siri21.PtSituationElement;
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

    @Autowired
    private SubscriptionConfig incomingSubscriptionConfig;


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
            case SITUATION_EXCHANGE:
                return getSXInitialDeliveries(subscriptionRequest);
            case FACILITY_MONITORING:
                return getFMinitialDeliveries(subscriptionRequest);
        }

        return results;
    }

    private Map<String, Siri> getFMinitialDeliveries(OutboundSubscriptionSetup subscriptionRequest) {
        Set<String> datasetsToRequest;
        if (subscriptionRequest.getDatasetList().isEmpty()) {
            datasetsToRequest = facilityMonitoring.getAllDatasetIds();
        } else {
            datasetsToRequest = new HashSet<>(subscriptionRequest.getDatasetList());
        }

        Map<String, Siri> results = new HashMap<>();

        for (String dataset : datasetsToRequest) {
            Collection<FacilityConditionStructure> fmData = facilityMonitoring.getAll(dataset);
            Siri delivery = siriObjectFactory.createFMServiceDelivery(fmData);
            results.put(dataset, delivery);
        }
        return results;
    }

    private Map<String, Siri> getSXInitialDeliveries(OutboundSubscriptionSetup subscriptionRequest) {

        Set<String> datasetsToRequest;
        if (subscriptionRequest.getDatasetList().isEmpty()) {
            datasetsToRequest = situations.getAllDatasetIds();
        } else {
            datasetsToRequest = new HashSet<>(subscriptionRequest.getDatasetList());
        }

        Map<String, Siri> results = new HashMap<>();
        OutboundIdMappingPolicy policy = subscriptionRequest.getOutboundIdMappingPolicy();

        for (String dataset : datasetsToRequest) {
            Collection<PtSituationElement> sxData = situations.getAll(dataset);
            Siri delivery = siriObjectFactory.createSXServiceDelivery(sxData);
            Map<ObjectType, Optional<IdProcessingParameters>> idProcessingParams = incomingSubscriptionConfig.buildIdProcessingParamsFromDataset(dataset);
            List<ValueAdapter> outboundAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.SITUATION_EXCHANGE, policy, idProcessingParams);
            Siri idProcessedDelivery = SiriValueTransformer.transform(delivery, outboundAdapters, true, false);
            results.put(dataset, idProcessedDelivery);
        }
        return results;
    }

    private Map<String, Siri> getGMinitialDeliveries(OutboundSubscriptionSetup subscriptionRequest) {

        Set<String> datasetsToRequest;
        if (subscriptionRequest.getDatasetList().isEmpty()) {
            datasetsToRequest = generalMessages.getAllDatasetIds();
        } else {
            datasetsToRequest = new HashSet<>(subscriptionRequest.getDatasetList());
        }

        Map<String, Siri> results = new HashMap<>();

        for (String dataset : datasetsToRequest) {
            Collection<GeneralMessage> messages = generalMessages.getAll(dataset);
            Siri delivery = siriObjectFactory.createGMServiceDelivery(messages);
            results.put(dataset, delivery);
        }
        return results;
    }

    public Map<String, Siri> getVMinitialDeliveries(OutboundSubscriptionSetup subscriptionRequest) {
        Map<String, Siri> results = new HashMap<>();


        Set<String> datasetsToRequest;
        if (subscriptionRequest.getDatasetList().isEmpty()) {
            datasetsToRequest = vehicleActivities.getAllDatasetIds();
        } else {
            datasetsToRequest = new HashSet<>(subscriptionRequest.getDatasetList());
        }


        for (String datasetId : datasetsToRequest) {
            Siri delivery = getVMinitialDeliveryForDataset(subscriptionRequest, datasetId);
            if (SiriUtils.hasDataOfType(delivery, SiriDataType.VEHICLE_MONITORING)) {
                results.put(datasetId, delivery);
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

        Set<String> datasetsToRequest;
        if (subscriptionRequest.getDatasetList().isEmpty()) {
            datasetsToRequest = monitoredStopVisits.getAllDatasetIds();
        } else {
            datasetsToRequest = new HashSet<>(subscriptionRequest.getDatasetList());
        }

        for (String datasetId : datasetsToRequest) {
            Siri delivery = monitoredStopVisits.createServiceDelivery(subscriptionRequest.getRequestorRef(), datasetId, "initialDelivery", Integer.MAX_VALUE, searchedStopIds);
            if (SiriUtils.hasDataOfType(delivery, SiriDataType.STOP_MONITORING)) {
                results.put(datasetId, delivery);
            }
        }
        return results;
    }

    private Map<String, Siri> getETinitialDeliveries(OutboundSubscriptionSetup subscriptionRequest) {
        Map<String, Siri> results = new HashMap<>();


        Set<String> datasetsToRequest;
        if (subscriptionRequest.getDatasetList().isEmpty()) {
            datasetsToRequest = estimatedTimetables.getAllDatasetIds();
        } else {
            datasetsToRequest = new HashSet<>(subscriptionRequest.getDatasetList());
        }

        for (String datasetId : datasetsToRequest) {
            Siri delivery = getETinitialDeliveryForDataset(subscriptionRequest, datasetId);
            if (SiriUtils.hasDataOfType(delivery, SiriDataType.ESTIMATED_TIMETABLE)) {
                results.put(datasetId, delivery);
            }
        }
        return results;
    }

    private Siri getETinitialDeliveryForDataset(OutboundSubscriptionSetup subscriptionRequest, String datasetId) {
        Set<String> filteredLines = siriHelper.getLineFiltersForDatasetId(subscriptionRequest, datasetId);
        return estimatedTimetables.createServiceDelivery(subscriptionRequest.getRequestorRef(), datasetId, "initialDelivery", new ArrayList<>(), Integer.MAX_VALUE, -1, filteredLines);
    }

}
