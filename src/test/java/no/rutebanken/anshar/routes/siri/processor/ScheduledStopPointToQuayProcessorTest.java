package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.mapping.ScheduledStopPointUpdaterService;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.ApplicationContext;
import uk.org.siri.siri21.DatedVehicleJourneyIndirectRefStructure;
import uk.org.siri.siri21.DestinationRef;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.JourneyPlaceRefStructure;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopPointRefStructure;

import java.util.List;
import java.util.Optional;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.SCHEDULED_STOP_POINT_TO_QUAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledStopPointToQuayProcessorTest {

    private static final String DATASET_ID = "LOCGUI1119";
    private static final String PREFIX_TO_REMOVE = "FR:ScheduledStopPoint::";
    // ids reçus dans l'ET
    private static final String SSP_1 = "FR:ScheduledStopPoint::85011031";
    private static final String SSP_2 = "FR:ScheduledStopPoint::85042150";
    private static final String SSP_UNKNOWN = "FR:ScheduledStopPoint::UNKNOWN";
    // ids attendus par le service (clés du CSV)
    private static final String CACHE_SSP_1 = "LOCGUI1119:ScheduledStopPoint:85011031";
    private static final String CACHE_SSP_2 = "LOCGUI1119:ScheduledStopPoint:85042150";
    private static final String QUAY_1 = "MOBIITI:Quay:3609";
    private static final String QUAY_2 = "MOBIITI:Quay:3577";
    // Quay dont le mapping inverse vers le format producteur n'existe pas
    private static final String SSP_NOT_REVERTIBLE = "FR:ScheduledStopPoint::99999999";
    private static final String CACHE_SSP_NOT_REVERTIBLE = "LOCGUI1119:ScheduledStopPoint:99999999";
    private static final String QUAY_NOT_REVERTIBLE = "MOBIITI:Quay:9999";
    // ids producteur renvoyés par le mapping inverse
    private static final String PRODUCER_QUAY_1 = "LOCGUI1119:Quay:85011031";
    private static final String PRODUCER_QUAY_2 = "LOCGUI1119:Quay:85042150";
    // codes producteur bruts attendus en sortie (préfixe de sortie retiré)
    private static final String PRODUCER_CODE_1 = "85011031";
    private static final String PRODUCER_CODE_2 = "85042150";

    private ApplicationContext previousApplicationContext;
    private PrometheusMetricsService metricsService;
    private ScheduledStopPointToQuayProcessor processor;

    @BeforeEach
    void setUp() {
        previousApplicationContext = ApplicationContextHolder.getContext();
        metricsService = mock(PrometheusMetricsService.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(PrometheusMetricsService.class)).thenReturn(metricsService);
        new ApplicationContextHolder().setApplicationContext(applicationContext);

        ScheduledStopPointUpdaterService sspService = mock(ScheduledStopPointUpdaterService.class);
        when(sspService.getQuay(anyString())).thenReturn(Optional.empty());
        when(sspService.getQuay(CACHE_SSP_1)).thenReturn(Optional.of(QUAY_1));
        when(sspService.getQuay(CACHE_SSP_2)).thenReturn(Optional.of(QUAY_2));
        when(sspService.getQuay(CACHE_SSP_NOT_REVERTIBLE)).thenReturn(Optional.of(QUAY_NOT_REVERTIBLE));

        StopPlaceUpdaterService stopPlaceUpdaterService = mock(StopPlaceUpdaterService.class);
        when(stopPlaceUpdaterService.getReverse(anyString(), eq(DATASET_ID))).thenReturn(List.of());
        when(stopPlaceUpdaterService.getReverse(QUAY_1, DATASET_ID)).thenReturn(List.of(PRODUCER_QUAY_1));
        when(stopPlaceUpdaterService.getReverse(QUAY_2, DATASET_ID)).thenReturn(List.of(PRODUCER_QUAY_2));
        when(applicationContext.getBean(StopPlaceUpdaterService.class)).thenReturn(stopPlaceUpdaterService);

        IdProcessingParameters stopIdParams = new IdProcessingParameters();
        stopIdParams.setDatasetId(DATASET_ID);
        stopIdParams.setObjectType(ObjectType.STOP);
        stopIdParams.setOutputPrefixToAdd(DATASET_ID + ":Quay:");
        SubscriptionConfig subscriptionConfig = mock(SubscriptionConfig.class);
        when(subscriptionConfig.getIdParametersForDataset(DATASET_ID, ObjectType.STOP)).thenReturn(Optional.of(stopIdParams));
        when(applicationContext.getBean(SubscriptionConfig.class)).thenReturn(subscriptionConfig);

        processor = new ScheduledStopPointToQuayProcessor(DATASET_ID, PREFIX_TO_REMOVE, sspService, stopPlaceUpdaterService, subscriptionConfig);
    }

    @AfterEach
    void tearDown() {
        new ApplicationContextHolder().setApplicationContext(previousApplicationContext);
    }

    @Test
    void process_shouldReplaceScheduledStopPointsByProducerQuaysInRecordedAndEstimatedCalls() {
        Siri siri = etSiri(SSP_1, SSP_2, SSP_UNKNOWN, SSP_NOT_REVERTIBLE);

        processor.process(siri);

        EstimatedVehicleJourney journey = firstJourney(siri);
        // Quay MOBIITI reconverti en code producteur brut
        assertThat(journey.getRecordedCalls().getRecordedCalls().getFirst().getStopPointRef().getValue()).isEqualTo(PRODUCER_CODE_1);
        assertThat(journey.getEstimatedCalls().getEstimatedCalls().get(0).getStopPointRef().getValue()).isEqualTo(PRODUCER_CODE_2);
        // pas de Quay trouvé : valeur d'origine conservée
        assertThat(journey.getEstimatedCalls().getEstimatedCalls().get(1).getStopPointRef().getValue()).isEqualTo(SSP_UNKNOWN);
        // Quay trouvé mais non reconvertible au format producteur : valeur d'origine conservée
        assertThat(journey.getEstimatedCalls().getEstimatedCalls().get(2).getStopPointRef().getValue()).isEqualTo(SSP_NOT_REVERTIBLE);
        verify(metricsService).registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, DATASET_ID, SCHEDULED_STOP_POINT_TO_QUAY, 2);
        // SSP_1, SSP_2 et SSP_NOT_REVERTIBLE sont connus du cache, SSP_UNKNOWN ne l'est pas
        verify(metricsService).registerScheduledStopPointLookup(DATASET_ID, 3, 1);
    }

    @Test
    void process_shouldReplaceOriginAndDestinationRefsOfDatedVehicleJourneyIndirectRef() {
        Siri siri = etSiri(SSP_UNKNOWN);
        DatedVehicleJourneyIndirectRefStructure indirectRef = new DatedVehicleJourneyIndirectRefStructure();
        indirectRef.setOriginRef(stopPointRef(SSP_1));
        indirectRef.setDestinationRef(stopPointRef(SSP_2));
        firstJourney(siri).setDatedVehicleJourneyIndirectRef(indirectRef);

        processor.process(siri);

        assertThat(indirectRef.getOriginRef().getValue()).isEqualTo(PRODUCER_CODE_1);
        assertThat(indirectRef.getDestinationRef().getValue()).isEqualTo(PRODUCER_CODE_2);
        verify(metricsService).registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, DATASET_ID, SCHEDULED_STOP_POINT_TO_QUAY, 2);
    }

    @Test
    void process_shouldReplaceOriginAndDestinationRefsOfJourney() {
        Siri siri = etSiri(SSP_UNKNOWN);
        JourneyPlaceRefStructure originRef = new JourneyPlaceRefStructure();
        originRef.setValue(SSP_1);
        firstJourney(siri).setOriginRef(originRef);
        DestinationRef destinationRef = new DestinationRef();
        destinationRef.setValue(SSP_2);
        firstJourney(siri).setDestinationRef(destinationRef);

        processor.process(siri);

        assertThat(firstJourney(siri).getOriginRef().getValue()).isEqualTo(PRODUCER_CODE_1);
        assertThat(firstJourney(siri).getDestinationRef().getValue()).isEqualTo(PRODUCER_CODE_2);
        verify(metricsService).registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, DATASET_ID, SCHEDULED_STOP_POINT_TO_QUAY, 2);
    }

    @Test
    void process_withoutEtDelivery_shouldDoNothing() {
        Siri siri = new Siri();
        siri.setServiceDelivery(new ServiceDelivery());

        processor.process(siri);
        processor.process(null);

        assertThat(siri.getServiceDelivery().getEstimatedTimetableDeliveries()).isEmpty();
    }

    @Test
    void process_whenServiceIsNotAvailable_shouldKeepScheduledStopPoints() {
        // bean absent (ssp.mapping.csv=false) : le contexte mocké renvoie un provider vide
        ApplicationContext applicationContext = ApplicationContextHolder.getContext();
        when(applicationContext.getBeanProvider(ScheduledStopPointUpdaterService.class))
                .thenReturn(new StaticListableBeanFactory().getBeanProvider(ScheduledStopPointUpdaterService.class));
        ScheduledStopPointToQuayProcessor processorWithoutService = new ScheduledStopPointToQuayProcessor(DATASET_ID, PREFIX_TO_REMOVE);
        Siri siri = etSiri(SSP_1, SSP_2);

        processorWithoutService.process(siri);

        EstimatedVehicleJourney journey = firstJourney(siri);
        assertThat(journey.getRecordedCalls().getRecordedCalls().getFirst().getStopPointRef().getValue()).isEqualTo(SSP_1);
        assertThat(journey.getEstimatedCalls().getEstimatedCalls().getFirst().getStopPointRef().getValue()).isEqualTo(SSP_2);
    }

    @Test
    void buildScheduledStopPointId_shouldRemovePrefixAndAddDatasetPrefix() {
        assertThat(processor.buildScheduledStopPointId("FR:ScheduledStopPoint::85011031")).isEqualTo(CACHE_SSP_1);
        // préfixe absent : l'id est conservé tel quel avant ajout du préfixe dataset
        assertThat(processor.buildScheduledStopPointId("85011031")).isEqualTo(CACHE_SSP_1);
        // pas de préfixe à retirer
        assertThat(new ScheduledStopPointToQuayProcessor(DATASET_ID, null, null, null, null).buildScheduledStopPointId("85011031")).isEqualTo(CACHE_SSP_1);
    }

    private static EstimatedVehicleJourney firstJourney(Siri siri) {
        return siri.getServiceDelivery().getEstimatedTimetableDeliveries().getFirst()
                .getEstimatedJourneyVersionFrames().getFirst()
                .getEstimatedVehicleJourneies().getFirst();
    }

    /**
     * Construit un ET avec un RecordedCall (1er arrêt) et des EstimatedCalls (arrêts suivants).
     */
    private static Siri etSiri(String recordedStop, String... estimatedStops) {
        EstimatedVehicleJourney journey = new EstimatedVehicleJourney();

        RecordedCall recordedCall = new RecordedCall();
        recordedCall.setStopPointRef(stopPointRef(recordedStop));
        EstimatedVehicleJourney.RecordedCalls recordedCalls = new EstimatedVehicleJourney.RecordedCalls();
        recordedCalls.getRecordedCalls().add(recordedCall);
        journey.setRecordedCalls(recordedCalls);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (String stop : estimatedStops) {
            EstimatedCall estimatedCall = new EstimatedCall();
            estimatedCall.setStopPointRef(stopPointRef(stop));
            estimatedCalls.getEstimatedCalls().add(estimatedCall);
        }
        journey.setEstimatedCalls(estimatedCalls);

        EstimatedVersionFrameStructure versionFrame = new EstimatedVersionFrameStructure();
        versionFrame.getEstimatedVehicleJourneies().add(journey);
        EstimatedTimetableDeliveryStructure etDelivery = new EstimatedTimetableDeliveryStructure();
        etDelivery.getEstimatedJourneyVersionFrames().add(versionFrame);
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getEstimatedTimetableDeliveries().add(etDelivery);
        Siri siri = new Siri();
        siri.setServiceDelivery(delivery);
        return siri;
    }

    private static StopPointRefStructure stopPointRef(String value) {
        StopPointRefStructure ref = new StopPointRefStructure();
        ref.setValue(value);
        return ref;
    }
}
