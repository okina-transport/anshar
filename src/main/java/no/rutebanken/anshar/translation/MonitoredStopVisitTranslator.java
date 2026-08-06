package no.rutebanken.anshar.translation;

import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.mapping.TranslationService;
import no.rutebanken.anshar.util.StopMonitoringUtils;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.MonitoredCallStructure;
import uk.org.siri.siri21.MonitoredStopVisit;
import uk.org.siri.siri21.MonitoredVehicleJourneyStructure;

import java.util.Optional;

@Service
public class MonitoredStopVisitTranslator extends BaseSiriEntityTranslator<MonitoredStopVisit> {

    public MonitoredStopVisitTranslator(TranslationService translationService, StopPlaceUpdaterService stopPlaceUpdaterService) {
        super(translationService, stopPlaceUpdaterService);
    }

    @Override
    public void handleTranslations(MonitoredStopVisit entity, String datasetId) {
        if (entity == null) {
            return;
        }
        if (!translationService.hasTranslationsForDatasetId(datasetId)) {
            return;
        }
        MonitoredVehicleJourneyStructure vehicleJourney = entity.getMonitoredVehicleJourney();
        if (vehicleJourney == null) {
            return;
        }
        Optional<String> lineRef = StopMonitoringUtils.getLineRef(entity);
        Optional<String> lineName = StopMonitoringUtils.getLineName(entity);

        Optional<String> monitoringRef = StopMonitoringUtils.getMonitoringRef(entity);
        Optional<String> monitoringName = Optional.empty();
        if (monitoringRef.isPresent()) {
            monitoringName = Optional.ofNullable(stopPlaceUpdaterService.getStopName(datasetId,
                    monitoringRef.get()));
        }

        Optional<String> destinationRef = StopMonitoringUtils.getDestinationRef(entity);
        Optional<String> destinationName = StopMonitoringUtils.getDestinationName(entity);

        Optional<String> vehicleJourneyRef = StopMonitoringUtils.getVehicleJourneyRef(entity);
        Optional<String> vehicleJourneyName = StopMonitoringUtils.getVehicleJourneyName(entity);

        // vehicle journey translations
        addLineNameTranslations(datasetId, lineRef.orElse(null), lineName.orElse(null),
                vehicleJourney.getPublishedLineNames());
        addStopNameTranslations(datasetId, destinationRef.orElse(null), destinationName.orElse(null),
                vehicleJourney.getDestinationNames());
        addVehicleJourneyNameTranslations(datasetId, vehicleJourneyRef.orElse(null), vehicleJourneyName.orElse(null),
                vehicleJourney.getVehicleJourneyNames());

        // monitored call translations
        MonitoredCallStructure monitoredCall = vehicleJourney.getMonitoredCall();
        if (monitoredCall == null) {
            return;
        }
        addStopNameTranslations(datasetId, monitoringRef.orElse(null), monitoringName.orElse(null),
                monitoredCall.getStopPointNames());
        addStopNameTranslations(datasetId, destinationRef.orElse(null), destinationName.orElse(null),
                monitoredCall.getDestinationDisplaies());
    }
}
