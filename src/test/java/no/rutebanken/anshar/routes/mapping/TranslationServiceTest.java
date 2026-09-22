package no.rutebanken.anshar.routes.mapping;

import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.export.file.BlobStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TranslationServiceTest {

    private static final String DATASET_ID = "TST";
    private static final String TRANSLATIONS_PATH = "translations.csv";
    private static final String HEADER = "dataset,object_type,object_id,field_name,field_value,language,translation,is_default";

    private BlobStoreService blobStoreService;
    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        blobStoreService = mock(BlobStoreService.class);
        translationService = new TranslationService(blobStoreService);
    }

    private void loadCsv(String... dataLines) {
        String csv = HEADER + "\n" + String.join("\n", dataLines);
        InputStream inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        when(blobStoreService.getBlob(anyString())).thenReturn(inputStream);
        ReflectionTestUtils.invokeMethod(translationService, "updateTranslationsMapping", TRANSLATIONS_PATH);
    }

    @Test
    void validRecordWithObjectId_isStoredInObjectIdCache() {
        loadCsv("TST,LINE,LINE1,,,EN,Line one");

        assertThat(translationService.hasTranslationsForDatasetId(DATASET_ID)).isTrue();
        List<TranslationService.TranslationDto> translations =
                translationService.getTranslationsByDatasetIdAndObjectTypeAndOriginalId(DATASET_ID, ObjectType.LINE, "LINE1", "");
        assertThat(translations).containsExactly(new TranslationService.TranslationDto("EN", "Line one", ""));
    }

    @Test
    void validRecordWithFieldValue_isStoredInFieldValueCache() {
        loadCsv("TST,LINE,,,Ligne un,EN,Line one");

        assertThat(translationService.hasTranslationsForDatasetId(DATASET_ID)).isTrue();
        List<TranslationService.TranslationDto> translations =
                translationService.getTranslationsByDatasetIdAndObjectTypeAndFieldValue(DATASET_ID, ObjectType.LINE, "Ligne un", "");
        assertThat(translations).containsExactly(new TranslationService.TranslationDto("EN", "Line one", ""));
    }

    @Test
    void objectIdRecordFlaggedAsDefault_isStoredInDefaultCacheOnly() {
        loadCsv("TST,LINE,LINE1,,,FR,Ligne un,1");

        Optional<TranslationService.TranslationDto> defaultTranslation =
                translationService.getDefaultTranslationsByDatasetIdAndObjectTypeAndOriginalId(DATASET_ID, ObjectType.LINE, "LINE1", "");
        assertThat(defaultTranslation).contains(new TranslationService.TranslationDto("FR", "Ligne un", ""));

        List<TranslationService.TranslationDto> allTranslations =
                translationService.getTranslationsByDatasetIdAndObjectTypeAndOriginalId(DATASET_ID, ObjectType.LINE, "LINE1", "");
        assertThat(allTranslations).isEmpty();
    }

    @Test
    void objectIdRecordNotFlaggedAsDefault_isNotStoredInDefaultCache() {
        loadCsv("TST,LINE,LINE1,,,EN,Line one,0");

        Optional<TranslationService.TranslationDto> defaultTranslation =
                translationService.getDefaultTranslationsByDatasetIdAndObjectTypeAndOriginalId(DATASET_ID, ObjectType.LINE, "LINE1", "");
        assertThat(defaultTranslation).isEmpty();
    }

    @Test
    void recordWithBothObjectIdAndFieldValue_isStoredByFieldValueOnly() {
        loadCsv("TST,LINE,LINE1,,Ligne un,EN,Line one");

        List<TranslationService.TranslationDto> byFieldValue =
                translationService.getTranslationsByDatasetIdAndObjectTypeAndFieldValue(DATASET_ID, ObjectType.LINE, "Ligne un", "");
        assertThat(byFieldValue).containsExactly(new TranslationService.TranslationDto("EN", "Line one", ""));

        List<TranslationService.TranslationDto> byObjectId =
                translationService.getTranslationsByDatasetIdAndObjectTypeAndOriginalId(DATASET_ID, ObjectType.LINE, "LINE1", "");
        assertThat(byObjectId).isEmpty();
    }

    @ValueSource(strings = {
            ",LINE,LINE1,,,EN,Line one",
            "TST,LINE,,,,EN,Line one",
            "TST,LINE,LINE1,,,,Line one",
            "TST,LINE,LINE1,,,EN,",
            "TST,FOO,LINE1,,,EN,Line one"
    })
    @ParameterizedTest
    void invalidRecord_isDiscarded(String csv) {
        loadCsv(csv);

        assertThat(translationService.hasTranslationsForDatasetId(DATASET_ID)).isFalse();
    }
}
