package no.rutebanken.anshar.routes.siri.processor;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.mapping.ScheduledStopPointUpdaterService;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.commons.lang3.Strings;
import uk.org.siri.siri21.DatedVehicleJourneyIndirectRefStructure;
import uk.org.siri.siri21.DestinationRef;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.JourneyPlaceRefStructure;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopPointRefStructure;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.SCHEDULED_STOP_POINT_TO_QUAY;

/**
 * Remplace les StopPointRef (ScheduledStopPoint) des EstimatedCalls / RecordedCalls d'un ET,
 * les OriginRef / DestinationRef de l'EstimatedVehicleJourney, ainsi que les OriginRef / DestinationRef
 * du DatedVehicleJourneyIndirectRef, par le Quay associé, en s'appuyant sur {@link ScheduledStopPointUpdaterService}.
 * <p>
 * Avant la recherche, l'id est normalisé : le préfixe {@code prefixToRemove} est retiré,
 * puis le préfixe {@code <datasetId>:ScheduledStopPoint:} est ajouté
 * (ex : {@code FR:ScheduledStopPoint::123} -> {@code DATASET:ScheduledStopPoint:123}).
 * <p>
 * Le Quay obtenu (format MOBIITI:Quay:xxx) est ensuite reconverti en code producteur brut (xxx) :
 * mapping inverse de {@link StopPlaceUpdaterService} pour le dataset, puis suppression du préfixe/suffixe
 * de sortie des IdProcessingParameters STOP du dataset (même logique que {@code SuperIdReversionProcess}).
 * <p>
 * Si aucun Quay n'est trouvé ou si le Quay ne peut pas être reconverti au format producteur,
 * la valeur d'origine est conservée.
 */
@Slf4j
public class ScheduledStopPointToQuayProcessor extends ValueAdapter implements PostProcessor {

    private static final String SCHEDULED_STOP_POINT_TYPE = ":ScheduledStopPoint:";

    private final String datasetId;

    private final String prefixToRemove;

    private transient ScheduledStopPointUpdaterService sspUpdaterService;

    private transient StopPlaceUpdaterService stopPlaceUpdaterService;

    private transient SubscriptionConfig subscriptionConfig;

    public ScheduledStopPointToQuayProcessor(String datasetId, String prefixToRemove) {
        this.datasetId = datasetId;
        this.prefixToRemove = prefixToRemove;
        this.sspUpdaterService = lookupService();
        this.stopPlaceUpdaterService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);
        this.subscriptionConfig = ApplicationContextHolder.getContext().getBean(SubscriptionConfig.class);
    }

    public ScheduledStopPointToQuayProcessor(String datasetId, String prefixToRemove,
                                             ScheduledStopPointUpdaterService sspUpdaterService,
                                             StopPlaceUpdaterService stopPlaceUpdaterService,
                                             SubscriptionConfig subscriptionConfig) {
        this.datasetId = datasetId;
        this.prefixToRemove = prefixToRemove;
        this.sspUpdaterService = sspUpdaterService;
        this.stopPlaceUpdaterService = stopPlaceUpdaterService;
        this.subscriptionConfig = subscriptionConfig;
    }

    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {
            if (siri == null || siri.getServiceDelivery() == null || siri.getServiceDelivery().getEstimatedTimetableDeliveries() == null
                    || siri.getServiceDelivery().getEstimatedTimetableDeliveries().isEmpty()) {
                return;
            }

            if (sspUpdaterService == null) {
                sspUpdaterService = lookupService();
                if (sspUpdaterService == null) {
                    log.warn("ScheduledStopPointUpdaterService is not available (ssp.mapping.csv=false ?) - skipping ScheduledStopPoint to Quay mapping, dataset:{}", datasetId);
                    return;
                }
            }

            if (stopPlaceUpdaterService == null) {
                stopPlaceUpdaterService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);
            }

            if (subscriptionConfig == null) {
                subscriptionConfig = ApplicationContextHolder.getContext().getBean(SubscriptionConfig.class);
            }

            MappingStats stats = new MappingStats();
            for (EstimatedTimetableDeliveryStructure etDelivery : siri.getServiceDelivery().getEstimatedTimetableDeliveries()) {
                for (EstimatedVersionFrameStructure versionFrame : etDelivery.getEstimatedJourneyVersionFrames()) {
                    for (EstimatedVehicleJourney estimatedVehicleJourney : versionFrame.getEstimatedVehicleJourneies()) {
                        processJourney(estimatedVehicleJourney, stats);
                    }
                }
            }

            if (stats.mapped > 0) {
                getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, SCHEDULED_STOP_POINT_TO_QUAY, stats.mapped);
            }
            getMetricsService().registerScheduledStopPointLookup(datasetId, stats.found, stats.unknown);
    }

    private void processJourney(EstimatedVehicleJourney estimatedVehicleJourney, MappingStats stats) {
        try {
            JourneyPlaceRefStructure originRef = estimatedVehicleJourney.getOriginRef();
            if (originRef != null) {
                mapToQuay(originRef.getValue(), originRef::setValue, stats);
            }
            DestinationRef destinationRef = estimatedVehicleJourney.getDestinationRef();
            if (destinationRef != null) {
                mapToQuay(destinationRef.getValue(), destinationRef::setValue, stats);
            }
            DatedVehicleJourneyIndirectRefStructure indirectRef = estimatedVehicleJourney.getDatedVehicleJourneyIndirectRef();
            if (indirectRef != null) {
                mapToQuay(indirectRef.getOriginRef(), stats);
                mapToQuay(indirectRef.getDestinationRef(), stats);
            }
            if (estimatedVehicleJourney.getRecordedCalls() != null) {
                for (RecordedCall recordedCall : estimatedVehicleJourney.getRecordedCalls().getRecordedCalls()) {
                    mapToQuay(recordedCall.getStopPointRef(), stats);
                }
            }
            if (estimatedVehicleJourney.getEstimatedCalls() != null) {
                for (EstimatedCall estimatedCall : estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls()) {
                    mapToQuay(estimatedCall.getStopPointRef(), stats);
                }
            }
        } catch (Exception e) {
            log.error("Error while applying ScheduledStopPointToQuayProcessor on specific journey, dataset:{}", datasetId, e);
        }
    }

    private void mapToQuay(StopPointRefStructure stopPointRef, MappingStats stats) {
        if (stopPointRef != null) {
            mapToQuay(stopPointRef.getValue(), stopPointRef::setValue, stats);
        }
    }

    /**
     * Remplace la valeur (ScheduledStopPoint) par le code producteur du Quay associé, si un mapping est possible.
     * Sinon la valeur d'origine est conservée.
     */
    private void mapToQuay(String originalId, Consumer<String> setter, MappingStats stats) {
        if (originalId == null) {
            return;
        }
        String scheduledStopPoint = buildScheduledStopPointId(originalId);
        Optional<String> quay = sspUpdaterService.getQuay(scheduledStopPoint);
        if (quay.isEmpty()) {
            stats.unknown++;
            log.debug("No Quay found for ScheduledStopPoint {} (original id: {}) - dataset:{}", scheduledStopPoint, originalId, datasetId);
            return;
        }
        stats.found++;

        Optional<String> producerQuay = convertToProducerCode(quay.get());
        if (producerQuay.isEmpty()) {
            log.debug("Quay {} (ScheduledStopPoint {}) can't be reverted to producer id - dataset:{}", quay.get(), scheduledStopPoint, datasetId);
            return;
        }
        setter.accept(producerQuay.get());
        stats.mapped++;
    }

    /**
     * Convertit un id NeTEx (MOBIITI:Quay:xxx) en code producteur brut (xxx).
     * Même logique que SuperIdReversionProcess#revertIdInStopPoint.
     */
    private Optional<String> convertToProducerCode(String quayId) {
        List<String> providerIds = stopPlaceUpdaterService.getReverse(quayId, datasetId);
        if (providerIds.isEmpty()) {
            return Optional.empty();
        }
        String idFromProvider = providerIds.getFirst();
        Optional<IdProcessingParameters> idParamsOpt = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.STOP);
        if (idParamsOpt.isPresent()) {
            // reverted ids will have the format : "OUTPUTPREFIX xxxx OUTPUTSUFFIX". We need to remove prefix and suffix to store the raw code in memory
            idFromProvider = idParamsOpt.get().removeOutputPrefixAndSuffix(idFromProvider);
        }
        return Optional.of(idFromProvider);
    }

    String buildScheduledStopPointId(String originalId) {
        String localId = prefixToRemove == null ? originalId : Strings.CS.removeStart(originalId, prefixToRemove);
        return datasetId + SCHEDULED_STOP_POINT_TYPE + localId;
    }

    private static ScheduledStopPointUpdaterService lookupService() {
        if (ApplicationContextHolder.getContext() == null) {
            return null;
        }
        // le bean n'existe que si ssp.mapping.csv=true
        return ApplicationContextHolder.getContext().getBeanProvider(ScheduledStopPointUpdaterService.class).getIfAvailable();
    }

    /**
     * Compteurs d'un traitement : ScheduledStopPoint trouvés / inconnus du cache, et valeurs effectivement remplacées.
     */
    private static final class MappingStats {
        private int found;
        private int unknown;
        private int mapped;
    }
}
