package no.rutebanken.anshar.routes.mapping;


import jakarta.annotation.PostConstruct;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.util.CSVUtils;
import no.rutebanken.anshar.util.FileUtils;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Configuration
public class InputExternalIdsService extends BaseRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(InputExternalIdsService.class);

    private static final String pathLines = "inputLines";

    // dataset -> rawId in TR -> replacement id
    private final Map<String, Map<String, String>> linesCache = new HashMap<>();

    private final String mappingExternalIdsRootDir;

    public InputExternalIdsService(AnsharConfiguration config, SubscriptionManager subscriptionManager, @Value("${anshar.mapping.external.ids.root.directory}") String mappingExternalIdsRootDir) {
        super(config, subscriptionManager);
        this.mappingExternalIdsRootDir = mappingExternalIdsRootDir;
    }

    @PostConstruct
    public void initCaches() {
        linesCache.clear();
        updateMappingExternalIdsCache();
    }

    private void updateMappingExternalIdsCache() {
        Set<String> datasetList = getDatasetList();
        for (String dataset : datasetList) {
            updateMappingExternalIdsCache(dataset);
        }

    }

    private Set<String> getDatasetList() {
        Set<String> csvFiles = FileUtils.listCSVFiles(mappingExternalIdsRootDir);
        return csvFiles.stream()
                .map(filename -> filename.replace("_lines_mapping.csv", "").replace("_stops_mapping.csv", ""))
                .collect(Collectors.toSet());
    }


    private void updateMappingExternalIdsCache(String datasetId) {
        File mappingLinesDirectory = new File(mappingExternalIdsRootDir, pathLines);
        if (!mappingLinesDirectory.exists()) {
            log.info("inputLine mapping directory does not exists. No input replacement configured");
            return;
        }

        logger.info("Starting updating  input ids mapping , line cache for dataset : {}", datasetId);
        if (mappingLinesDirectory.list() != null) {
            for (String fileName : Objects.requireNonNull(mappingLinesDirectory.list())) {
                String datasetIdInFileName = fileName.replace("_lines_mapping.csv", "");
                if (datasetId.equalsIgnoreCase(datasetIdInFileName)) {
                    File fileToRead = new File(mappingLinesDirectory, fileName);
                    feedCacheLineWithFile(fileToRead, datasetId);
                }
            }
        }
    }

    private void feedCacheLineWithFile(File fileToRead, String datasetId) {

        try {
            Iterable<CSVRecord> records = CSVUtils.getRecordsWithBomHandling(fileToRead);

            Map<String, String> currentLineAltLineCache;

            if (linesCache.containsKey(datasetId)) {
                currentLineAltLineCache = linesCache.get(datasetId);
            } else {
                currentLineAltLineCache = new HashMap<>();
                linesCache.put(datasetId, currentLineAltLineCache);
            }

            for (CSVRecord record : records) {
                String inputLineId = record.get("input_line_id");
                String replacementId = record.get("replacement_id");
                currentLineAltLineCache.put(inputLineId, replacementId);
            }
            logger.info("Feeding cache with input lines_mapping file: {} completed", fileToRead.getAbsolutePath());

        } catch (IOException | IllegalArgumentException e) {
            logger.error("Unable to feed cache with file:{}", fileToRead.getAbsolutePath(), e);
        }
    }

    public Optional<String> getLineReplacement(String datasetId, String originalId) {
        if (!linesCache.containsKey(datasetId) || !linesCache.get(datasetId).containsKey(originalId)) {
            return Optional.empty();
        }

        return Optional.of(linesCache.get(datasetId).get(originalId));
    }


}
