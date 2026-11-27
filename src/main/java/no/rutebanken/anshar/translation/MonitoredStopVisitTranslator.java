package no.rutebanken.anshar.translation;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.mapping.TranslationService;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.util.StopMonitoringUtils;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.MonitoredCallStructure;
import uk.org.siri.siri21.MonitoredStopVisit;
import uk.org.siri.siri21.MonitoredVehicleJourneyStructure;

import java.util.Optional;

@Service
public class MonitoredStopVisitTranslator extends BaseSiriEntityTranslator<MonitoredStopVisit> {

    private final SubscriptionConfig subscriptionConfig;

    public MonitoredStopVisitTranslator(TranslationService translationService, StopPlaceUpdaterService stopPlaceUpdaterService,
                                        SubscriptionConfig subscriptionConfig) {
        super(translationService, stopPlaceUpdaterService);
        this.subscriptionConfig = subscriptionConfig;
    }

    @Override
    public void handleTranslations(MonitoredStopVisit entity, String datasetId) {
        if (entity == null) {
            return;
        }
        MonitoredVehicleJourneyStructure vehicleJourney = entity.getMonitoredVehicleJourney();
        if (vehicleJourney == null) {
            return;
        }
        // stop ids are stored as received from the producer : the translations are referenced
        // by the id without the producer prefix / suffix defined in the STOP idProcessingParameters
        Optional<IdProcessingParameters> stopIdProcessingParameters = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.STOP);

        Optional<String> lineRef = StopMonitoringUtils.getLineRef(entity);
        Optional<String> monitoringRef = StopMonitoringUtils.getMonitoringRef(entity).map(id -> toTranslationStopId(id, stopIdProcessingParameters));
        Optional<String> destinationRef = StopMonitoringUtils.getDestinationRef(entity).map(id -> toTranslationStopId(id, stopIdProcessingParameters));
        Optional<String> originRef = StopMonitoringUtils.getOriginRef(entity).map(id -> toTranslationStopId(id, stopIdProcessingParameters));
        Optional<String> vehicleJourneyRef = StopMonitoringUtils.getVehicleJourneyRef(entity);

        // vehicle journey translations
        lineRef.ifPresent(value -> addLineNameTranslationsNLSS(datasetId, value, vehicleJourney.getPublishedLineNames()));
        destinationRef.ifPresent(value -> addStopNameTranslationsNLSS(datasetId, value, vehicleJourney.getDestinationNames()));
        originRef.ifPresent(value -> addStopNameTranslationsNLPNSS(datasetId, value, vehicleJourney.getOriginNames()));
        vehicleJourneyRef.ifPresent(value -> addVehicleJourneyNameTranslationsNLSS(datasetId, value,
                vehicleJourney.getVehicleJourneyNames()));

        // monitored call translations
        MonitoredCallStructure monitoredCall = vehicleJourney.getMonitoredCall();
        if (monitoredCall == null) {
            return;
        }
        monitoringRef.ifPresent(value -> addStopNameTranslationsNLSS(datasetId, value,
                monitoredCall.getStopPointNames()));
        destinationRef.ifPresent(value -> addStopNameTranslationsNLSS(datasetId, value,
                monitoredCall.getDestinationDisplaies()));
    }

    /**
     * Removes the input prefix and the input suffix of the STOP idProcessingParameters (if any) from a stop id.
     * e.g : "0:PRSON" with inputPrefixToRemove "0:" gives "PRSON"
     */
    private String toTranslationStopId(String stopId, Optional<IdProcessingParameters> stopIdProcessingParameters) {
        return stopIdProcessingParameters
                .map(parameters -> parameters.removeInputPrefixAndSuffix(stopId))
                .orElse(stopId);
    }
}
