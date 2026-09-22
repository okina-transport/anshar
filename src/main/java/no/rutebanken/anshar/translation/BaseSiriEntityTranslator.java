package no.rutebanken.anshar.translation;

import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.mapping.TranslationService;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import uk.org.siri.siri21.NaturalLanguagePlaceNameStructure;
import uk.org.siri.siri21.NaturalLanguageStringStructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class BaseSiriEntityTranslator<T> implements SiriEntityTranslator<T> {

    public static final String FIELD_NAME_LINE_NAME = "publishedName";
    public static final String FIELD_NAME_STOP_NAME = "stopName";
    public static final String FIELD_NAME_SERVICE_JOURNEY_NAME = "publishedJourneyName";

    protected final TranslationService translationService;
    protected final StopPlaceUpdaterService stopPlaceUpdaterService;

    protected BaseSiriEntityTranslator(TranslationService translationService, StopPlaceUpdaterService stopPlaceUpdaterService) {
        this.translationService = translationService;
        this.stopPlaceUpdaterService = stopPlaceUpdaterService;
    }

    protected void addLineNameTranslationsNLSS(String datasetId, String lineOriginalId,
                                               List<NaturalLanguageStringStructure> target) {
        addTranslationsNLSS(ObjectType.LINE, datasetId, lineOriginalId, FIELD_NAME_LINE_NAME, target);
    }

    protected void addStopNameTranslationsNLSS(String datasetId, String stopOriginalId, List<NaturalLanguageStringStructure> target) {
        addTranslationsNLSS(ObjectType.STOP, datasetId, stopOriginalId, FIELD_NAME_STOP_NAME, target);
    }

    protected void addStopNameTranslationsNLPNSS(String datasetId, String stopOriginalId, List<NaturalLanguagePlaceNameStructure> target) {
        addTranslationNLPNSS(ObjectType.STOP, datasetId, stopOriginalId, FIELD_NAME_STOP_NAME, target);
    }

    protected void addVehicleJourneyNameTranslationsNLSS(String datasetId, String vjOriginalId, List<NaturalLanguageStringStructure> target) {
        addTranslationsNLSS(ObjectType.VEHICLE_JOURNEY, datasetId, vjOriginalId, FIELD_NAME_SERVICE_JOURNEY_NAME, target);
    }

    protected void addTranslationsNLSS(ObjectType objectType, String datasetId, String originalId,
                                       String fieldName,
                                       List<NaturalLanguageStringStructure> target) {
        Map<String, TranslationService.TranslationDto> translationsByLanguage = getTranslationsByLanguage(objectType, datasetId, originalId, fieldName);
        if (MapUtils.isEmpty(translationsByLanguage)) return;

        for (TranslationService.TranslationDto translation : translationsByLanguage.values()) {
            if (target.stream().anyMatch(existing -> translation.language().equalsIgnoreCase(existing.getLang()))) {
                continue;
            }
            NaturalLanguageStringStructure translatedName = new NaturalLanguageStringStructure();
            translatedName.setLang(translation.language());
            translatedName.setValue(translation.value());
            target.add(translatedName);
        }
    }

    protected void addTranslationNLPNSS(ObjectType objectType, String datasetId, String originalId,
                                        String fieldName, List<NaturalLanguagePlaceNameStructure> target) {
        Map<String, TranslationService.TranslationDto> translationsByLanguage = getTranslationsByLanguage(objectType, datasetId, originalId, fieldName);
        if (MapUtils.isEmpty(translationsByLanguage)) return;

        for (TranslationService.TranslationDto translation : translationsByLanguage.values()) {
            if (target.stream().anyMatch(existing -> translation.language().equalsIgnoreCase(existing.getLang()))) {
                continue;
            }
            NaturalLanguagePlaceNameStructure translatedName = new NaturalLanguagePlaceNameStructure();
            translatedName.setLang(translation.language());
            translatedName.setValue(translation.value());
            target.add(translatedName);
        }
    }

    private Map<String, TranslationService.TranslationDto> getTranslationsByLanguage(ObjectType objectType, String datasetId, String originalId, String fieldName) {
        if (StringUtils.isBlank(originalId)) {
            return Map.of();
        }

        // language -> translation; object_id translations are added last so they win over field_value ones for the same language
        Map<String, TranslationService.TranslationDto> translationsByLanguage = new LinkedHashMap<>();

        Optional<TranslationService.TranslationDto> defaultTranslation =
                translationService.getDefaultTranslationsByDatasetIdAndObjectTypeAndOriginalId(datasetId, objectType,
                        originalId, fieldName);

        if (defaultTranslation.isPresent()) {
            translationsByLanguage.put(defaultTranslation.get().language(), defaultTranslation.get());
            for (TranslationService.TranslationDto translation :
                    translationService.getTranslationsByDatasetIdAndObjectTypeAndFieldValue(datasetId.toUpperCase(),
                            objectType, defaultTranslation.get().value(), fieldName)) {
                translationsByLanguage.put(translation.language(), translation);
            }
        }

        if (StringUtils.isNotBlank(originalId)) {
            for (TranslationService.TranslationDto translation :
                    translationService.getTranslationsByDatasetIdAndObjectTypeAndOriginalId(datasetId.toUpperCase(), objectType, originalId, fieldName)) {
                translationsByLanguage.put(translation.language(), translation);
            }
        }
        return translationsByLanguage;
    }
}
