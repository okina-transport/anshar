package no.rutebanken.anshar.routes.mapping;

import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.util.CSVUtils;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Service to handle alternative ids in mapping files.
 * - Build a cache by reading mapping files files
 */
@Component
@Configuration
public class ExternalIdsService {

    private final Logger logger = LoggerFactory.getLogger(ExternalIdsService.class);

    private static final Object LOCK = new Object();

    @Value("${anshar.mapping.external.ids.update.frequency.min:1}")
    private int updateFrequency = 5;


    @Value("${anshar.mapping.external.ids.root.directory}")
    private String mappingExternalIdsRootDir;

    @Autowired
    SubscriptionConfig subscriptionConfig;

    @Autowired
    SubscriptionManager subscriptionManager;

    private final Map<String, Map<String, String>> stopsCache = new HashMap();
    private final Map<String, Map<String, String>> linesCache = new HashMap();


    @PostConstruct
    private void initialize() {

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::refreshCache, 0, updateFrequency, TimeUnit.MINUTES);
        logger.info("Initialized mappingExternalIdsService, updateFrequency:{} min", updateFrequency);
    }

    /**
     * Refresh the cache containing data from mapping stops and lines files
     */
    private void refreshCache() {
        synchronized (LOCK) {
            updateMappingExternalIdsCache();
        }
    }

    /**
     * Refresh the cache containing data for all datasetIds
     */
    private void updateMappingExternalIdsCache() {
        Set<String> datasetList = subscriptionManager.getAllDatasetIds();
        for (String dataset : datasetList) {
            updateMappingExternalIdsCache(dataset);
        }
    }


    private void updateMappingExternalIdsCache(String datasetId) {

        File mappingStopsDirectory = new File(mappingExternalIdsRootDir, "stops");
        File mappingLinesDirectory = new File(mappingExternalIdsRootDir, "lines");

        if (!mappingStopsDirectory.exists() && !mappingLinesDirectory.exists()) {
            return;
        }
        logger.info("Starting updating mapping external ids stops cache for dataset : " + datasetId);

        for (String fileName : Objects.requireNonNull(mappingStopsDirectory.list())) {
            String datasetIdInFileName = fileName.replace("_stops_mapping.csv", "");
            if (datasetId.equalsIgnoreCase(datasetIdInFileName)) {
                File fileToRead = new File(mappingStopsDirectory, fileName);
                feedCacheStopWithFile(fileToRead, datasetId);
            }
        }

        logger.info("Finishing updating mapping external ids stops cache for dataset : " + datasetId);

        logger.info("Starting updating mapping external ids lines cache for dataset : " + datasetId);



        for (String fileName : Objects.requireNonNull(mappingLinesDirectory.list())) {
            String datasetIdInFileName = fileName.replace("_lines_mapping.csv", "");
            if (datasetId.equalsIgnoreCase(datasetIdInFileName)) {
                File fileToRead = new File(mappingLinesDirectory, fileName);
                feedCacheLineWithFile(fileToRead, datasetId);
            }
        }

        logger.info("Finishing Updating mapping external ids lines cache for dataset : " + datasetId);

        logger.info("Feeding cache completed for datasetId: " + datasetId);
    }


    /**
     * Refresh the cache for a particular file/datasetId
     *
     * @param fileToRead the stops_mapping file
     * @param datasetId  the datasetId
     */
    public void feedCacheStopWithFile(File fileToRead, String datasetId) {

        try {
            Iterable<CSVRecord> records = CSVUtils.getRecords(fileToRead);

            Map<String, String> currentStopAltStopCache;

            if (stopsCache.containsKey(datasetId)) {
                currentStopAltStopCache = stopsCache.get(datasetId);
            } else {
                currentStopAltStopCache = new HashMap<>();
                stopsCache.put(datasetId, currentStopAltStopCache);
            }

            for (CSVRecord record : records) {
                String stopId = record.get("stop_id");
                String stopAltId = record.get("stop_alt_id");
                currentStopAltStopCache.put(stopId, stopAltId);
            }
            logger.info("Feeding cache with stops_mapping file: " + fileToRead.getAbsolutePath() + " completed");

        } catch (IOException | IllegalArgumentException e) {
            logger.error("Unable to feed cache with file:" + fileToRead.getAbsolutePath(), e);
        }
    }

    /**
     * Refresh the cache for a particular file/datasetId
     *
     * @param fileToRead the lines_mapping file
     * @param datasetId  the datasetId
     */
    public void feedCacheLineWithFile(File fileToRead, String datasetId) {

        try {
            Iterable<CSVRecord> records = CSVUtils.getRecords(fileToRead);

            Map<String, String> currentLineAltLineCache;

            if (linesCache.containsKey(datasetId)) {
                currentLineAltLineCache = linesCache.get(datasetId);
            } else {
                currentLineAltLineCache = new HashMap<>();
                linesCache.put(datasetId, currentLineAltLineCache);
            }

            for (CSVRecord record : records) {
                String lineId = record.get("line_id");
                String lineAltId = record.get("line_alt_id");
                currentLineAltLineCache.put(lineId, lineAltId);
            }
            logger.info("Feeding cache with lines_mapping file: " + fileToRead.getAbsolutePath() + " completed");

        } catch (IOException | IllegalArgumentException e) {
            logger.error("Unable to feed cache with file:" + fileToRead.getAbsolutePath(), e);
        }
    }


    public Optional<String> getAltId(String datasetId, String id, ObjectType objectType) {
        if (ObjectType.STOP.equals(objectType)) {
            return getAltIdFromCache(datasetId, id, stopsCache);
        } else if (ObjectType.LINE.equals(objectType)) {
            return getAltIdFromCache(datasetId, id, linesCache);
        } else {
            return Optional.empty();
        }


    }

    private Optional<String> getAltIdFromCache(String datasetId, String id, Map<String, Map<String, String>> cache) {
        if (!cache.containsKey(datasetId)) {
            return Optional.empty();
        }

        Map<String, String> datasetMap = cache.get(datasetId);

        if (!datasetMap.containsKey(id)) {
            return Optional.empty();
        }

        return Optional.of(datasetMap.get(id));
    }

    public Optional<String> getReverseAltIdStop(String datasetId, String stopId) {
        return getRevertAltId(datasetId, stopId, stopsCache);

    }

    public Optional<String> getReverseAltIdLine(String datasetId, String lineId) {
        return getRevertAltId(datasetId, lineId, linesCache);
    }

    private Optional<String> getRevertAltId(String datasetId, String id, Map<String, Map<String, String>> cache) {
        if (!cache.containsKey(datasetId)) {
            return Optional.empty();
        }

        Map<String, String> datasetMap = cache.get(datasetId);

        if (!datasetMap.containsValue(id)) {
            return Optional.empty();
        }

        return datasetMap
                .entrySet()
                .stream()
                .filter(entry -> id.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst();
    }


}
