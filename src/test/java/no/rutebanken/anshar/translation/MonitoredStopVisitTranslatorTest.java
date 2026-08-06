package no.rutebanken.anshar.translation;

import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.mapping.TranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MonitoredStopVisitTranslatorTest {

    private static final String DATASET_ID = "TST";

    private TranslationService translationService;
    private MonitoredStopVisitTranslator translator;

    @BeforeEach
    void setUp() {
        translationService = mock(TranslationService.class);
        when(translationService.hasTranslationsForDatasetId(DATASET_ID)).thenReturn(true);

        translator = new MonitoredStopVisitTranslator(translationService, mock(StopPlaceUpdaterService.class));
    }

    @Test
    void addsLineNameTranslations() {
        when(translationService.getTranslationsByDatasetIdAndObjectTypeAndOriginalId(DATASET_ID, ObjectType.LINE, "LINE1", "publishedName"))
                .thenReturn(List.of(new TranslationService.TranslationDto("EN", "Line one")));

        MonitoredStopVisit visit = createVisit("LINE1", null, null, null);

        translator.handleTranslations(visit, DATASET_ID);

        List<NaturalLanguageStringStructure> lineNames = getVehicleJourney(visit).getPublishedLineNames();
        assertThat(lineNames).hasSize(1);
        assertThat(lineNames.getFirst().getLang()).isEqualTo("EN");
        assertThat(lineNames.getFirst().getValue()).isEqualTo("Line one");
    }

    @Test
    void addsDestinationNameTranslations() {
        when(translationService.getTranslationsByDatasetIdAndObjectTypeAndOriginalId(DATASET_ID, ObjectType.STOP, "STOP1", "stopName"))
                .thenReturn(List.of(new TranslationService.TranslationDto("EN", "Stop one")));

        MonitoredStopVisit visit = createVisit(null, "STOP1", null, null);

        translator.handleTranslations(visit, DATASET_ID);

        List<NaturalLanguageStringStructure> destinationNames = getVehicleJourney(visit).getDestinationNames();
        assertThat(destinationNames).hasSize(1);
        assertThat(destinationNames.getFirst().getLang()).isEqualTo("EN");
        assertThat(destinationNames.getFirst().getValue()).isEqualTo("Stop one");
    }

    @Test
    void addsVehicleJourneyNameTranslations() {
        when(translationService.getTranslationsByDatasetIdAndObjectTypeAndOriginalId(DATASET_ID, ObjectType.VEHICLE_JOURNEY, "VJ1", "publishedJourneyName"))
                .thenReturn(List.of(new TranslationService.TranslationDto("EN", "Journey one")));

        MonitoredStopVisit visit = createVisit(null, null, "VJ1", null);

        translator.handleTranslations(visit, DATASET_ID);

        List<NaturalLanguageStringStructure> vjNames = getVehicleJourney(visit).getVehicleJourneyNames();
        assertThat(vjNames).hasSize(1);
        assertThat(vjNames.getFirst().getLang()).isEqualTo("EN");
        assertThat(vjNames.getFirst().getValue()).isEqualTo("Journey one");
    }

    @Test
    void addsStopPointNameTranslationsOnMonitoredCall() {
        when(translationService.getTranslationsByDatasetIdAndObjectTypeAndOriginalId(DATASET_ID, ObjectType.STOP, "MON1", "stopName"))
                .thenReturn(List.of(new TranslationService.TranslationDto("EN", "Stop point one")));

        MonitoredStopVisit visit = createVisit(null, null, null, "MON1");
        MonitoredCallStructure monitoredCall = new MonitoredCallStructure();
        getVehicleJourney(visit).setMonitoredCall(monitoredCall);

        translator.handleTranslations(visit, DATASET_ID);

        List<NaturalLanguageStringStructure> stopPointNames = monitoredCall.getStopPointNames();
        assertThat(stopPointNames).hasSize(1);
        assertThat(stopPointNames.getFirst().getLang()).isEqualTo("EN");
        assertThat(stopPointNames.getFirst().getValue()).isEqualTo("Stop point one");
    }

    @Test
    void addsDestinationDisplayTranslationsOnMonitoredCall() {
        when(translationService.getTranslationsByDatasetIdAndObjectTypeAndOriginalId(DATASET_ID, ObjectType.STOP, "STOP1", "stopName"))
                .thenReturn(List.of(new TranslationService.TranslationDto("EN", "Stop one")));

        MonitoredStopVisit visit = createVisit(null, "STOP1", null, null);
        MonitoredCallStructure monitoredCall = new MonitoredCallStructure();
        getVehicleJourney(visit).setMonitoredCall(monitoredCall);

        translator.handleTranslations(visit, DATASET_ID);

        List<NaturalLanguageStringStructure> destinationDisplaies = monitoredCall.getDestinationDisplaies();
        assertThat(destinationDisplaies).hasSize(1);
        assertThat(destinationDisplaies.getFirst().getLang()).isEqualTo("EN");
        assertThat(destinationDisplaies.getFirst().getValue()).isEqualTo("Stop one");
        // vehicle-journey level destination names get the same translation independently
        assertThat(getVehicleJourney(visit).getDestinationNames()).hasSize(1);
    }

    @Test
    void replacesExistingTranslationWithSameLanguage() {
        when(translationService.getTranslationsByDatasetIdAndObjectTypeAndOriginalId(DATASET_ID, ObjectType.LINE, "LINE1", "publishedName"))
                .thenReturn(List.of(new TranslationService.TranslationDto("EN", "New name")));

        MonitoredStopVisit visit = createVisit("LINE1", null, null, null);
        NaturalLanguageStringStructure existing = new NaturalLanguageStringStructure();
        existing.setLang("EN");
        existing.setValue("Old name");
        getVehicleJourney(visit).getPublishedLineNames().add(existing);

        translator.handleTranslations(visit, DATASET_ID);

        List<NaturalLanguageStringStructure> lineNames = getVehicleJourney(visit).getPublishedLineNames();
        assertThat(lineNames).hasSize(1);
        assertThat(lineNames.getFirst().getValue()).isEqualTo("New name");
    }

    @Test
    void fallsBackToFieldValueTranslationWhenNoOriginalIdTranslation() {
        NaturalLanguageStringStructure original = new NaturalLanguageStringStructure();
        original.setLang("FR");
        original.setValue("Ligne un");

        MonitoredStopVisit visit = createVisit("LINE1", null, null, null);
        getVehicleJourney(visit).getPublishedLineNames().add(original);

        when(translationService.getTranslationsByDatasetIdAndObjectTypeAndFieldValue(DATASET_ID, ObjectType.LINE, "Ligne un", "publishedName"))
                .thenReturn(List.of(new TranslationService.TranslationDto("EN", "Line one (by field value)")));

        translator.handleTranslations(visit, DATASET_ID);

        List<NaturalLanguageStringStructure> lineNames = getVehicleJourney(visit).getPublishedLineNames();
        assertThat(lineNames).extracting(NaturalLanguageStringStructure::getValue)
                .contains("Line one (by field value)");
    }

    @Test
    void originalIdTranslationTakesPrecedenceOverFieldValueForSameLanguage() {
        NaturalLanguageStringStructure original = new NaturalLanguageStringStructure();
        original.setLang("FR");
        original.setValue("Ligne un");

        MonitoredStopVisit visit = createVisit("LINE1", null, null, null);
        getVehicleJourney(visit).getPublishedLineNames().add(original);

        when(translationService.getTranslationsByDatasetIdAndObjectTypeAndFieldValue(DATASET_ID, ObjectType.LINE, "Ligne un", "publishedName"))
                .thenReturn(List.of(new TranslationService.TranslationDto("EN", "By field value")));
        when(translationService.getTranslationsByDatasetIdAndObjectTypeAndOriginalId(DATASET_ID, ObjectType.LINE, "LINE1", "publishedName"))
                .thenReturn(List.of(new TranslationService.TranslationDto("EN", "By object id")));

        translator.handleTranslations(visit, DATASET_ID);

        List<NaturalLanguageStringStructure> lineNames = getVehicleJourney(visit).getPublishedLineNames();
        assertThat(lineNames).filteredOn(name -> "EN".equalsIgnoreCase(name.getLang()))
                .extracting(NaturalLanguageStringStructure::getValue)
                .containsExactly("By object id");
    }

    @Test
    void noRefs_doesNotFail() {
        MonitoredStopVisit visit = createVisit(null, null, null, null);

        translator.handleTranslations(visit, DATASET_ID);

        assertThat(getVehicleJourney(visit).getPublishedLineNames()).isEmpty();
        assertThat(getVehicleJourney(visit).getDestinationNames()).isEmpty();
        assertThat(getVehicleJourney(visit).getVehicleJourneyNames()).isEmpty();
    }

    @Test
    void noVehicleJourney_doesNotFail() {
        MonitoredStopVisit visit = new MonitoredStopVisit();

        translator.handleTranslations(visit, DATASET_ID);
    }

    @Test
    void nullEntity_doesNotFail() {
        translator.handleTranslations(null, DATASET_ID);
    }

    @Test
    void noTranslationsForDataset_skipsLookup() {
        when(translationService.hasTranslationsForDatasetId(DATASET_ID)).thenReturn(false);

        MonitoredStopVisit visit = createVisit("LINE1", "STOP1", "VJ1", "MON1");

        translator.handleTranslations(visit, DATASET_ID);

        assertThat(getVehicleJourney(visit).getPublishedLineNames()).isEmpty();
        verify(translationService, never())
                .getTranslationsByDatasetIdAndObjectTypeAndOriginalId(anyString(), any(), anyString(), anyString());
    }

    // -- helpers --

    private MonitoredStopVisit createVisit(String lineRef, String destinationRef, String vehicleJourneyRef, String monitoringRef) {
        MonitoredStopVisit visit = new MonitoredStopVisit();
        MonitoredVehicleJourneyStructure vehicleJourney = new MonitoredVehicleJourneyStructure();
        visit.setMonitoredVehicleJourney(vehicleJourney);

        if (lineRef != null) {
            LineRef ref = new LineRef();
            ref.setValue(lineRef);
            vehicleJourney.setLineRef(ref);
        }
        if (destinationRef != null) {
            DestinationRef ref = new DestinationRef();
            ref.setValue(destinationRef);
            vehicleJourney.setDestinationRef(ref);
        }
        if (vehicleJourneyRef != null) {
            FramedVehicleJourneyRefStructure ref = new FramedVehicleJourneyRefStructure();
            ref.setDatedVehicleJourneyRef(vehicleJourneyRef);
            vehicleJourney.setFramedVehicleJourneyRef(ref);
        }
        if (monitoringRef != null) {
            MonitoringRefStructure ref = new MonitoringRefStructure();
            ref.setValue(monitoringRef);
            visit.setMonitoringRef(ref);
        }

        return visit;
    }

    private MonitoredVehicleJourneyStructure getVehicleJourney(MonitoredStopVisit visit) {
        return visit.getMonitoredVehicleJourney();
    }
}
