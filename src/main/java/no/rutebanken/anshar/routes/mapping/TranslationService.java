package no.rutebanken.anshar.routes.mapping;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.export.file.BlobStoreService;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TranslationService {

    // datasetid -> ObjectType -> objectid -> translations
    private static final Map<String, Map<ObjectType, Map<String, List<TranslationDto>>>> TRANSLATIONS_BY_OBJECT_ID_CACHE =
            new ConcurrentHashMap<>();

    // datasetid -> ObjectType -> field_value -> translations
    private static final Map<String, Map<ObjectType, Map<String, List<TranslationDto>>>> TRANSLATIONS_BY_FIELD_VALUE_CACHE =
            new ConcurrentHashMap<>();

    // datasetid -> ObjectType -> objectid -> default translations only
    private static final Map<String, Map<String, TranslationDto>> TRANSLATIONS_DEFAULT_BY_OBJECT_ID_CACHE =
            new ConcurrentHashMap<>();

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private final BlobStoreService blobStoreService;

    @Value("${anshar.translations.file}")
    private String translationsPath;

    @Value("${anshar.translations.update.frequency.minutes:5}")
    private int updateFrequency = 5;

    // last modified timestamp of the translations file
    private volatile long lastModified = -1;

    public TranslationService(BlobStoreService blobStoreService) {
        this.blobStoreService = blobStoreService;
    }

    @PostConstruct
    private void initialize() {
        executor.scheduleAtFixedRate(this::updateTranslations, 0, updateFrequency, TimeUnit.MINUTES);
        log.info("Initialized translations-updater with path:{}, updateFrequency:{} minutes", translationsPath, updateFrequency);
    }

    @PreDestroy
    private void destroy() {
        log.info("Destroy TranslationService");
        executor.shutdown();
    }

    private void updateTranslations() {
        synchronized (this) {
            updateTranslationsMapping(translationsPath);
        }
    }

    private void updateTranslationsMapping(String translationsPath) {
        File file = new File(translationsPath);
        if (file.exists()) {
            long modified = file.lastModified();
            if (modified == lastModified && modified > 0L) {
                log.info("Translations file {} has not changed since last update, skipping reload", translationsPath);
                return;
            }
            lastModified = modified;
        }

        log.info("Fetching translation data - start. Fetching translations from {}", translationsPath);
        long t1 = System.currentTimeMillis();

        final InputStream blob = blobStoreService.getBlob(translationsPath);

        if (blob == null) {
            log.error("Blob is null. Can't update translations mapping");
            return;
        }

        Map<String, Map<ObjectType, Map<String, List<TranslationDto>>>> updatedCache = new HashMap<>();
        Map<String, Map<ObjectType, Map<String, List<TranslationDto>>>> updatedFieldValueCache = new HashMap<>();
        Map<String, Map<String, TranslationDto>> updatedDefaultByObjectIdCache = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(blob, StandardCharsets.UTF_8));
             CSVParser csvParser = CSVFormat.RFC4180
                     .builder()
                     .setHeader("dataset", "object_type", "object_id", "field_name", "field_value", "language", "translation", "is_default")
                     .setSkipHeaderRecord(true)
                     .get()
                     .parse(reader)) {

            Iterable<CSVRecord> records = csvParser.getRecords();
            int count = 0;
            for (CSVRecord csvRecord : records) {
                if (addTranslation(updatedCache, updatedFieldValueCache, updatedDefaultByObjectIdCache, csvRecord)) {
                    count++;
                }
            }

            long t2 = System.currentTimeMillis();
            log.info("Fetched translations data - {} translations. [fetched: {}ms]", count, (t2 - t1));
        } catch (IOException e) {
            log.warn("Failed to read translations from {}", translationsPath, e);
            return;
        }

        TRANSLATIONS_BY_OBJECT_ID_CACHE.clear();
        TRANSLATIONS_BY_OBJECT_ID_CACHE.putAll(updatedCache);

        TRANSLATIONS_BY_FIELD_VALUE_CACHE.clear();
        TRANSLATIONS_BY_FIELD_VALUE_CACHE.putAll(updatedFieldValueCache);

        TRANSLATIONS_DEFAULT_BY_OBJECT_ID_CACHE.clear();
        TRANSLATIONS_DEFAULT_BY_OBJECT_ID_CACHE.putAll(updatedDefaultByObjectIdCache);
    }

    private boolean addTranslation(Map<String, Map<ObjectType, Map<String, List<TranslationDto>>>> cache,
                                   Map<String, Map<ObjectType, Map<String, List<TranslationDto>>>> fieldValueCache,
                                   Map<String, Map<String, TranslationDto>> defaultByObjectIdCache,
                                   CSVRecord csvRecord) {
        String dataset = csvRecord.get("dataset");
        String objectTypeStr = csvRecord.get("object_type");
        String objectId = csvRecord.get("object_id");
        String fieldName = csvRecord.get("field_name");
        String fieldValue = csvRecord.get("field_value");
        String language = csvRecord.get("language");
        String translation = csvRecord.get("translation");
        String isDefault = csvRecord.isSet("is_default") ? csvRecord.get("is_default") : "0";

        if (StringUtils.isBlank(dataset)
                || (StringUtils.isBlank(objectId) && StringUtils.isBlank(fieldValue))
                || StringUtils.isBlank(language)
                || StringUtils.isBlank(translation)) {
            log.warn("Invalid translation record, missing dataset, (object_id or field_value), language or translation: {}", csvRecord);
            return false;
        }

        ObjectType objectType;
        try {
            objectType = ObjectType.valueOf(objectTypeStr.toUpperCase());
        } catch (Exception e) {
            log.warn("Invalid translation record, unknown object_type '{}': {}", objectTypeStr, csvRecord);
            return false;
        }

        boolean isDefaultTranslation = "1".equals(StringUtils.trim(isDefault));
        TranslationDto translationDto = new TranslationDto(language.toUpperCase(), translation, fieldName);

        if (isDefaultTranslation) {
            String defaultCacheKey = buildDefaultCacheKey(objectType, objectId, fieldName);
            defaultByObjectIdCache
                    .computeIfAbsent(dataset.toUpperCase(), key -> new HashMap<>())
                    .put(defaultCacheKey, translationDto);
        } else if (StringUtils.isNotBlank(fieldValue)) {
            fieldValueCache
                    .computeIfAbsent(dataset.toUpperCase(), key -> new EnumMap<>(ObjectType.class))
                    .computeIfAbsent(objectType, key -> new HashMap<>())
                    .computeIfAbsent(fieldValue, key -> new ArrayList<>())
                    .add(translationDto);
        } else {
            cache
                    .computeIfAbsent(dataset.toUpperCase(), key -> new EnumMap<>(ObjectType.class))
                    .computeIfAbsent(objectType, key -> new HashMap<>())
                    .computeIfAbsent(objectId, key -> new ArrayList<>())
                    .add(translationDto);
        }

        return true;
    }

    public boolean hasTranslationsForDatasetId(String datasetId) {
        synchronized (this) {
            return (TRANSLATIONS_BY_OBJECT_ID_CACHE.containsKey(datasetId) && MapUtils.isNotEmpty(TRANSLATIONS_BY_OBJECT_ID_CACHE.get(datasetId)))
                    || (TRANSLATIONS_BY_FIELD_VALUE_CACHE.containsKey(datasetId) && MapUtils.isNotEmpty(TRANSLATIONS_BY_FIELD_VALUE_CACHE.get(datasetId)));
        }
    }

    public List<TranslationDto> getTranslationsByDatasetIdAndObjectTypeAndOriginalId(String datasetid, ObjectType type,
                                                                                     String originalId, String fieldName) {
        synchronized (this) {
            List<TranslationDto> translations = TRANSLATIONS_BY_OBJECT_ID_CACHE
                    .getOrDefault(datasetid, Map.of())
                    .getOrDefault(type, Map.of())
                    .getOrDefault(originalId, List.of());
            return filterByFieldName(translations, fieldName);
        }
    }

    public Optional<TranslationDto> getDefaultTranslationsByDatasetIdAndObjectTypeAndOriginalId(String datasetid,
                                                                                                ObjectType type,
                                                                                                String originalId,
                                                                                                String fieldName) {
        synchronized (this) {
            String defaultCacheKey = buildDefaultCacheKey(type, originalId, fieldName);
            return Optional.ofNullable(TRANSLATIONS_DEFAULT_BY_OBJECT_ID_CACHE
                    .getOrDefault(datasetid, Map.of())
                    .getOrDefault(defaultCacheKey, null));
        }
    }

    public List<TranslationDto> getTranslationsByDatasetIdAndObjectTypeAndFieldValue(String datasetid, ObjectType type,
                                                                                     String fieldValue, String fieldName) {
        synchronized (this) {
            List<TranslationDto> translations = TRANSLATIONS_BY_FIELD_VALUE_CACHE
                    .getOrDefault(datasetid, Map.of())
                    .getOrDefault(type, Map.of())
                    .getOrDefault(fieldValue, List.of());
            return filterByFieldName(translations, fieldName);
        }
    }

    private List<TranslationDto> filterByFieldName(List<TranslationDto> translations, String fieldName) {
        return translations.stream()
                .filter(t -> Strings.CS.equals(t.fieldName(), fieldName))
                .toList();
    }

    private String buildDefaultCacheKey(ObjectType type, String originalId, String fieldName) {
        return String.format("%s##%s##%s", type, originalId, fieldName);
    }

    public record TranslationDto(String language, String value, String fieldName) {
        public TranslationDto(String language, String value) {
            this(language, value, null);
        }
    }

}
