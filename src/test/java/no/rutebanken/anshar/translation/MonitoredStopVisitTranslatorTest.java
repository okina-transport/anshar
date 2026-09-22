package no.rutebanken.anshar.translation;

import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.mapping.TranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
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
    void keepsExistingTranslationWhenLanguageAlreadyPresent() {
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
        assertThat(lineNames.getFirst().getValue()).isEqualTo("Old name");
    }

    @Test
    void defaultTranslationResolvesFieldValueKeyForOtherLanguages() {
        NaturalLanguageStringStructure original = new NaturalLanguageStringStructure();
        original.setLang("FR");
        original.setValue("Wrong name from SIRI message");

        MonitoredStopVisit visit = createVisit("LINE1", null, null, null);
        getVehicleJourney(visit).getPublishedLineNames().add(original);

        when(translationService.getDefaultTranslationsByDatasetIdAndObjectTypeAndOriginalId(DATASET_ID, ObjectType.LINE, "LINE1", "publishedName"))
                .thenReturn(Optional.of(new TranslationService.TranslationDto("FR", "Ligne un", "publishedName")));
        when(translationService.getTranslationsByDatasetIdAndObjectTypeAndFieldValue(DATASET_ID, ObjectType.LINE, "Ligne un", "publishedName"))
                .thenReturn(List.of(new TranslationService.TranslationDto("EN", "Line one", "publishedName")));

        translator.handleTranslations(visit, DATASET_ID);

        List<NaturalLanguageStringStructure> lineNames = getVehicleJourney(visit).getPublishedLineNames();
        // FR already present in the raw SIRI message, so it is left untouched
        assertThat(lineNames).filteredOn(name -> "FR".equalsIgnoreCase(name.getLang()))
                .extracting(NaturalLanguageStringStructure::getValue)
                .containsExactly("Wrong name from SIRI message");
        // EN is missing from the message, so the field_value translation (keyed off the default's own text) is added
        assertThat(lineNames).filteredOn(name -> "EN".equalsIgnoreCase(name.getLang()))
                .extracting(NaturalLanguageStringStructure::getValue)
                .containsExactly("Line one");
        verify(translationService, never())
                .getTranslationsByDatasetIdAndObjectTypeAndFieldValue(DATASET_ID, ObjectType.LINE, "Wrong name from SIRI message", "publishedName");
    }

    @Test
    void withoutDefaultTranslation_fieldValueLookupIsNeverAttempted() {
        MonitoredStopVisit visit = createVisit("LINE1", null, null, null);

        when(translationService.getTranslationsByDatasetIdAndObjectTypeAndOriginalId(DATASET_ID, ObjectType.LINE, "LINE1", "publishedName"))
                .thenReturn(List.of(new TranslationService.TranslationDto("EN", "By object id")));

        translator.handleTranslations(visit, DATASET_ID);

        List<NaturalLanguageStringStructure> lineNames = getVehicleJourney(visit).getPublishedLineNames();
        assertThat(lineNames).extracting(NaturalLanguageStringStructure::getValue)
                .containsExactly("By object id");
        verify(translationService, never())
                .getTranslationsByDatasetIdAndObjectTypeAndFieldValue(anyString(), any(), anyString(), anyString());
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

        assertThatNoException().isThrownBy(() -> translator.handleTranslations(visit, DATASET_ID));
    }

    @Test
    void nullEntity_doesNotFail() {
        assertThatNoException().isThrownBy(() -> translator.handleTranslations(null, DATASET_ID));
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
