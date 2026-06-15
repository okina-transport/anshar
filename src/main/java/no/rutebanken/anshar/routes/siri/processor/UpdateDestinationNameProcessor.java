package no.rutebanken.anshar.routes.siri.processor;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;

import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import uk.org.siri.siri21.*;

import java.util.List;
import java.util.Optional;


@Slf4j
public class UpdateDestinationNameProcessor extends ValueAdapter implements PostProcessor {

    private final String datasetId;
    private transient SubscriptionConfig subscriptionConfig;
    private transient StopPlaceUpdaterService stopPlaceUpdaterService;

    public UpdateDestinationNameProcessor(String datasetId) {
        this.datasetId = datasetId;
        subscriptionConfig       = ApplicationContextHolder.getContext().getBean(SubscriptionConfig.class);
        stopPlaceUpdaterService  = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);
    }

    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {

        if (siri == null || siri.getServiceDelivery() == null || CollectionUtils.isEmpty(siri.getServiceDelivery().getStopMonitoringDeliveries())) {
            return;
        }

        if (subscriptionConfig == null) {
            subscriptionConfig = ApplicationContextHolder.getContext().getBean(SubscriptionConfig.class);
        }
        if (stopPlaceUpdaterService == null) {
            stopPlaceUpdaterService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);
        }

        Optional<IdProcessingParameters> stopIdProcessingParameter = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.STOP);

        for (StopMonitoringDeliveryStructure delivery : siri.getServiceDelivery().getStopMonitoringDeliveries()) {

            if (CollectionUtils.isEmpty(delivery.getMonitoredStopVisits())) {
                continue;
            }

            for (MonitoredStopVisit monitoredStopVisit : delivery.getMonitoredStopVisits()) {
                try {
                    processMonitoredStopVisit(monitoredStopVisit, stopIdProcessingParameter);
                } catch (Exception e) {
                    log.debug("Error while applying UpdateDestinationNameProcessor on item: {}", monitoredStopVisit.getItemIdentifier(), e);
                }
            }
        }
    }

    private void processMonitoredStopVisit(MonitoredStopVisit monitoredStopVisit,
                                           Optional<IdProcessingParameters> stopIdProcessingParameter) {

        MonitoredVehicleJourneyStructure vehicleJourney = monitoredStopVisit.getMonitoredVehicleJourney();

        if (vehicleJourney == null || vehicleJourney.getMonitoredCall() == null) {
            return;
        }

        DestinationRef destinationRef = vehicleJourney.getDestinationRef();
        if (destinationRef == null) {
            return;
        }

        String destinationRefValue = destinationRef.getValue();
        if (destinationRefValue == null) {
            return;
        }

        String stopId = destinationRefValue;

        if (stopIdProcessingParameter.isPresent()) {
            stopId = stopIdProcessingParameter.get().removeInputPrefixAndSuffix(destinationRefValue);
        }

        String resolvedName = stopPlaceUpdaterService.getStopName(stopId, datasetId);

        if (StringUtils.isBlank(resolvedName)) {
            log.debug("No stop name found for destinationRef={} (resolved id={}) / datasetId={} on item {}",
                    destinationRefValue, stopId, datasetId, monitoredStopVisit.getItemIdentifier());
            return;
        }

        overrideDestinationNames(vehicleJourney.getDestinationNames(), resolvedName, monitoredStopVisit.getItemIdentifier());
    }

    private void overrideDestinationNames(List<NaturalLanguageStringStructure> names,
                                          String resolvedName,
                                          String itemId) {
        names.clear();
        NaturalLanguageStringStructure entry = new NaturalLanguageStringStructure();
        entry.setValue(resolvedName);
        names.add(entry);
        log.debug("Added MonitoredVehicleJourney.DestinationName = '{}' on item {}", resolvedName, itemId);
    }
}
