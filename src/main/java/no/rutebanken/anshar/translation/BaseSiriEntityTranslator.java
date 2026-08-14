package no.rutebanken.anshar.translation;

import jakarta.annotation.Nullable;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.mapping.TranslationService;
import org.apache.commons.lang3.StringUtils;
import uk.org.siri.siri21.NaturalLanguagePlaceNameStructure;
import uk.org.siri.siri21.NaturalLanguageStringStructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    protected void addLineNameTranslationsNLSS(String datasetId, @Nullable String lineOriginalId, @Nullable String lineName,
                                               List<NaturalLanguageStringStructure> target) {
        addTranslationsNLSS(ObjectType.LINE, datasetId, lineOriginalId, lineName, FIELD_NAME_LINE_NAME, target);
    }

    protected void addStopNameTranslationsNLSS(String datasetId, @Nullable String stopOriginalId,
                                               @Nullable String stopName, List<NaturalLanguageStringStructure> target) {
        addTranslationsNLSS(ObjectType.STOP, datasetId, stopOriginalId, stopName, FIELD_NAME_STOP_NAME, target);
    }

    protected void addStopNameTranslationsNLPNSS(String datasetId, @Nullable String stopOriginalId,
                                                 @Nullable String stopName, List<NaturalLanguagePlaceNameStructure> target) {
        addTranslationNLPNSS(ObjectType.STOP, datasetId, stopOriginalId, stopName, FIELD_NAME_STOP_NAME, target);
    }

    protected void addVehicleJourneyNameTranslationsNLSS(String datasetId, @Nullable String vjOriginalId,
                                                         @Nullable String vjName, List<NaturalLanguageStringStructure> target) {
        addTranslationsNLSS(ObjectType.VEHICLE_JOURNEY, datasetId, vjOriginalId, vjName, FIELD_NAME_SERVICE_JOURNEY_NAME, target);
    }

    protected void addTranslationsNLSS(ObjectType objectType, String datasetId, @Nullable String originalId,
                                       @Nullable String fieldValue, String fieldName,
                                       List<NaturalLanguageStringStructure> target) {
        if (StringUtils.isBlank(originalId) && StringUtils.isBlank(fieldValue)) {
            return;
        }

        // language -> translation; object_id translations are added last so they win over field_value ones for the same language
        Map<String, TranslationService.TranslationDto> translationsByLanguage = new LinkedHashMap<>();

        if (StringUtils.isNotBlank(fieldValue)) {
            for (TranslationService.TranslationDto translation :
                    translationService.getTranslationsByDatasetIdAndObjectTypeAndFieldValue(datasetId.toUpperCase(), objectType, fieldValue, fieldName)) {
                translationsByLanguage.put(translation.language().toUpperCase(), translation);
            }
        }

        if (StringUtils.isNotBlank(originalId)) {
            for (TranslationService.TranslationDto translation :
                    translationService.getTranslationsByDatasetIdAndObjectTypeAndOriginalId(datasetId.toUpperCase(), objectType, originalId, fieldName)) {
                translationsByLanguage.put(translation.language().toUpperCase(), translation);
            }
        }

        for (TranslationService.TranslationDto translation : translationsByLanguage.values()) {
            target.removeIf(existing -> translation.language().equalsIgnoreCase(existing.getLang()));
            NaturalLanguageStringStructure translatedName = new NaturalLanguageStringStructure();
            translatedName.setLang(translation.language().toUpperCase());
            translatedName.setValue(translation.value());
            target.add(translatedName);
        }
    }

    protected void addTranslationNLPNSS(ObjectType objectType, String datasetId, @Nullable String originalId,
                                        @Nullable String fieldValue, String fieldName,
                                        List<NaturalLanguagePlaceNameStructure> target) {
        if (StringUtils.isBlank(originalId) && StringUtils.isBlank(fieldValue)) {
            return;
        }

        // language -> translation; object_id translations are added last so they win over field_value ones for the same language
        Map<String, TranslationService.TranslationDto> translationsByLanguage = new LinkedHashMap<>();

        if (StringUtils.isNotBlank(fieldValue)) {
            for (TranslationService.TranslationDto translation :
                    translationService.getTranslationsByDatasetIdAndObjectTypeAndFieldValue(datasetId.toUpperCase(), objectType, fieldValue, fieldName)) {
                translationsByLanguage.put(translation.language().toUpperCase(), translation);
            }
        }

        if (StringUtils.isNotBlank(originalId)) {
            for (TranslationService.TranslationDto translation :
                    translationService.getTranslationsByDatasetIdAndObjectTypeAndOriginalId(datasetId.toUpperCase(), objectType, originalId, fieldName)) {
                translationsByLanguage.put(translation.language().toUpperCase(), translation);
            }
        }

        for (TranslationService.TranslationDto translation : translationsByLanguage.values()) {
            target.removeIf(existing -> translation.language().equalsIgnoreCase(existing.getLang()));
            NaturalLanguagePlaceNameStructure translatedName = new NaturalLanguagePlaceNameStructure();
            translatedName.setLang(translation.language().toUpperCase());
            translatedName.setValue(translation.value());
            target.add(translatedName);
        }
    }
}
