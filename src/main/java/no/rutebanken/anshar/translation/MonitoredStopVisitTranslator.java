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
        MonitoredVehicleJourneyStructure vehicleJourney = entity.getMonitoredVehicleJourney();
        if (vehicleJourney == null) {
            return;
        }
        Optional<String> lineRef = StopMonitoringUtils.getLineRef(entity);
        Optional<String> monitoringRef = StopMonitoringUtils.getMonitoringRef(entity);
        Optional<String> destinationRef = StopMonitoringUtils.getDestinationRef(entity);
        Optional<String> originRef = StopMonitoringUtils.getOriginRef(entity);
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
}
