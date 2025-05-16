package no.rutebanken.anshar.routes.mapping;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.util.CSVUtils;
import no.rutebanken.anshar.util.FileUtils;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Service to handle alternative ids in mapping files.
 * - Build a cache by reading mapping files
 */
@Component
@Configuration
public class ExternalIdsService extends BaseRouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ExternalIdsService.class);

    @Value("${cron.download.files.mapping}")
    private String cronDownloadFilesMapping;

    @Value("${urls.stops.mapping.file}")
    private String urlsStopsMappingFile;

    @Value("${urls.lines.mapping.file}")
    private String urlsLinesMappingFile;

    @Value("${anshar.mapping.external.ids.root.directory}")
    private String mappingExternalIdsRootDir;

    @Autowired
    SubscriptionConfig subscriptionConfig;

    @Autowired
    SubscriptionManager subscriptionManager;

    private final WebClient webClient;

    private final Map<String, Map<String, String>> stopsCache = new HashMap();
    private final Map<String, Map<String, List<String>>> linesCache = new HashMap();

    private final String pathStops = "stops";
    private final String pathLines = "lines";

    protected ExternalIdsService(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
        this.webClient = createWebClient();
    }

    @Override
    public void configure() throws Exception {
        singletonFrom("quartz://anshar/DownloadFilesMapping?cron=" + cronDownloadFilesMapping + "&trigger.timeZone=Europe/Paris", "monitor.download.files.mapping")
                .log("Starting downloading and importing files mapping")
                .process(p -> downloadFilesAndRefreshCache());


        singletonFrom("quartz://anshar/DownloadFilesMappingFirstStart?trigger.repeatCount=0&trigger.startDelay=0&trigger.timeZone=Europe/Paris", "monitor.download.files.mapping.first.start")
                .log("Starting downloading and importing files mapping")
                .process(p -> downloadFilesAndRefreshCache());

    }

    /**
     * Creates a WebClient configured to handle redirects and large files
     */
    private WebClient createWebClient() {
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)
                .responseTimeout(Duration.ofMinutes(2));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    /**
     * Download and refresh the cache containing data from mapping stops and lines files
     */
    public void downloadFilesAndRefreshCache() {
        Flux<String> stopsMappingUrls = Flux.fromArray(urlsStopsMappingFile.split(","));
        Flux<String> linesMappingUrls = Flux.fromArray(urlsLinesMappingFile.split(","));

        Mono<Void> downloadFilesMono = Flux.zip(
                        downloadFilesMapping(stopsMappingUrls, pathStops, "BERTHELET_stops_mapping.csv"),
                        downloadFilesMapping(linesMappingUrls, pathLines, "BERTHELET_lines_mapping.csv"))
                .then();

        downloadFilesMono.block();

        stopsCache.clear();
        linesCache.clear();
        updateMappingExternalIdsCache();
    }

    private Mono<Void> downloadFilesMapping(Flux<String> urls, String path, String name) {
        return urls.flatMap(url -> downloadFileMapping(url, path, name))
                .then();
    }

    private Mono<Void> downloadFileMapping(String url, String path, String name) {
        logger.info("Downloading file from Google Drive URL: {}", url);

        return webClient.get()
                .uri(url)
                .exchangeToMono(response -> {
                    if (response.statusCode().is3xxRedirection()) {
                        String redirectUrl = response.headers().header("Location").stream().findFirst().orElse(null);
                        logger.info("Redirecting to: {}", redirectUrl);
                        if (redirectUrl != null) {
                            return webClient.get()
                                    .uri(redirectUrl)
                                    .retrieve()
                                    .bodyToMono(byte[].class);
                        } else {
                            return Mono.error(new RuntimeException("Redirection without Location header"));
                        }
                    } else if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(byte[].class);
                    } else {
                        return response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    logger.error("Unexpected response: {} - {}", response.statusCode(), body);
                                    return Mono.error(new RuntimeException("Failed to download file: " + response.statusCode()));
                                });
                    }
                })
                .doOnNext(bytes -> logger.info("Downloaded {} bytes", bytes.length))
                .flatMap(fileBytes -> Mono.fromRunnable(() -> {
                    try {
                        Path directoryPath = Paths.get(mappingExternalIdsRootDir, path);
                        Files.createDirectories(directoryPath);
                        Path filePath = directoryPath.resolve(name);

                        Files.write(filePath, fileBytes,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING);

                        logger.info("Successfully saved file to: {}", filePath.toAbsolutePath());
                    } catch (Exception e) {
                        logger.error("Failed to save downloaded file: {}", e.getMessage(), e);
                        throw new RuntimeException("Error saving downloaded file", e);
                    }
                }))
                .doOnError(error -> logger.error("Failed to download file from {}: {}", url, error.getMessage(), error))
                .onErrorResume(error -> Mono.empty())
                .then();
    }


    /**
     * Refresh the cache containing data for all datasetIds
     */
    private void updateMappingExternalIdsCache() {
        Set<String> datasetList = getDatasetList();
        for (String dataset : datasetList) {
            updateMappingExternalIdsCache(dataset);
        }
    }


    private void updateMappingExternalIdsCache(String datasetId) {

        File mappingStopsDirectory = new File(mappingExternalIdsRootDir, pathStops);
        File mappingLinesDirectory = new File(mappingExternalIdsRootDir, pathLines);

        if (!mappingStopsDirectory.exists() && !mappingLinesDirectory.exists()) {
            return;
        }
        logger.info("Starting updating mapping external ids stops cache for dataset : {}", datasetId);

        if (mappingStopsDirectory.list() == null) {
            logger.info("Pas de répertoires de mapping externe");
            return;
        }


        for (String fileName : Objects.requireNonNull(mappingStopsDirectory.list())) {
            String datasetIdInFileName = fileName.replace("_stops_mapping.csv", "");
            if (datasetId.equalsIgnoreCase(datasetIdInFileName)) {
                File fileToRead = new File(mappingStopsDirectory, fileName);
                feedCacheStopWithFile(fileToRead, datasetId);
            }
        }

        logger.info("Finishing updating mapping external ids stops cache for dataset : {}", datasetId);

        logger.info("Starting updating mapping external ids lines cache for dataset : {}", datasetId);


        for (String fileName : Objects.requireNonNull(mappingLinesDirectory.list())) {
            String datasetIdInFileName = fileName.replace("_lines_mapping.csv", "");
            if (datasetId.equalsIgnoreCase(datasetIdInFileName)) {
                File fileToRead = new File(mappingLinesDirectory, fileName);
                feedCacheLineWithFile(fileToRead, datasetId);
            }
        }

        logger.info("Finishing updating mapping external ids lines cache for dataset : " + datasetId);

        logger.info("Feeding cache completed for datasetId: " + datasetId);
    }


    private Set<String> getDatasetList() {
        Set<String> csvFiles = FileUtils.listCSVFiles(mappingExternalIdsRootDir);
        return csvFiles.stream()
                .map(filename -> filename.replace("_lines_mapping.csv", "").replace("_stops_mapping.csv", ""))
                .collect(Collectors.toSet());
    }


    /**
     * Refresh the cache for a particular file/datasetId
     *
     * @param fileToRead the stops_mapping file
     * @param datasetId  the datasetId
     */
    public void feedCacheStopWithFile(File fileToRead, String datasetId) {


        Optional<IdProcessingParameters> idParametersOpt = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.STOP);

        try {
            Iterable<CSVRecord> records = CSVUtils.getRecordsWithBomHandling(fileToRead);

            Map<String, String> currentStopAltStopCache;
            boolean firstRecord = true;

            if (stopsCache.containsKey(datasetId)) {
                currentStopAltStopCache = stopsCache.get(datasetId);
            } else {
                currentStopAltStopCache = new HashMap<>();
                stopsCache.put(datasetId, currentStopAltStopCache);
            }

            for (CSVRecord record : records) {
                String stopId = record.get("stop_id");
                String stopAltId = record.get("stop_alt_id");
                stopId = applyTransformation(stopId, idParametersOpt);
                currentStopAltStopCache.put(stopId, stopAltId);

                if (firstRecord) {
                    logger.info("stop cache sample - alt : {}, stopId:{}", stopAltId, stopId);
                    firstRecord = false;
                }
            }
            logger.info("Feeding cache with stops_mapping file: " + fileToRead.getAbsolutePath() + " completed");

        } catch (IOException | IllegalArgumentException e) {
            logger.error("Unable to feed cache with file:" + fileToRead.getAbsolutePath(), e);
        }
    }

    private String applyTransformation(String text, Optional<IdProcessingParameters> idParametersOpt) {
        if (idParametersOpt.isEmpty() || text == null) {
            return text;
        }

        IdProcessingParameters parameters = idParametersOpt.get();
        return parameters.applyTransformationToString(text);

    }

    /**
     * Refresh the cache for a particular file/datasetId
     *
     * @param fileToRead the lines_mapping file
     * @param datasetId  the datasetId
     */
    public void feedCacheLineWithFile(File fileToRead, String datasetId) {
        boolean firstRecord = true;

        Optional<IdProcessingParameters> idParametersOpt = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.LINE);

        try {
            Iterable<CSVRecord> records = CSVUtils.getRecordsWithBomHandling(fileToRead);

            Map<String, List<String>> currentLineAltLineCache;

            if (linesCache.containsKey(datasetId)) {
                currentLineAltLineCache = linesCache.get(datasetId);
            } else {
                currentLineAltLineCache = new HashMap<>();
                linesCache.put(datasetId, currentLineAltLineCache);
            }

            for (CSVRecord record : records) {


                String lineId = record.isSet("line_id") ? record.get("line_id") : record.get("route_id");
                String lineAltId = record.isSet("line_alt_id") ? record.get("line_alt_id") : record.get("route_alt_id Titan");

                lineId = applyTransformation(lineId, idParametersOpt);
                List<String> lineIdList;
                if (currentLineAltLineCache.containsKey(lineAltId)) {
                    lineIdList = currentLineAltLineCache.get(lineAltId);
                } else {
                    lineIdList = new ArrayList<>();
                }
                lineIdList.add(lineId);
                if (firstRecord) {
                    for (String line : lineIdList) {
                        logger.info("line cache sample - alt : {}, lineId:{}", lineAltId, line);
                    }
                    firstRecord = false;
                }
                currentLineAltLineCache.put(lineAltId, lineIdList);
            }
            logger.info("Feeding cache with lines_mapping file: {} completed", fileToRead.getAbsolutePath());

        } catch (IOException | IllegalArgumentException e) {
            logger.error("Unable to feed cache with file:{}", fileToRead.getAbsolutePath(), e);
        }
    }


    public Optional<String> getAltId(String datasetId, String id, ObjectType objectType) {
        if (ObjectType.STOP.equals(objectType)) {
            return getAltIdFromCacheStops(datasetId, id, stopsCache);
        } else if (ObjectType.LINE.equals(objectType)) {
            return getAltIdFromCacheLines(datasetId, id, linesCache);
        } else {
            return Optional.empty();
        }


    }

    private Optional<String> getAltIdFromCacheStops(String datasetId, String id, Map<String, Map<String, String>> cache) {
        if (!cache.containsKey(datasetId)) {
            return Optional.empty();
        }

        Map<String, String> datasetMap = cache.get(datasetId);

        if (!datasetMap.containsKey(id)) {
            return Optional.empty();
        }

        return Optional.of(datasetMap.get(id));
    }

    private Optional<String> getAltIdFromCacheLines(String datasetId, String id, Map<String, Map<String, List<String>>> cache) {
        if (!cache.containsKey(datasetId)) {
            return Optional.empty();
        }

        Map<String, List<String>> datasetMap = cache.get(datasetId);
        for (String originalLineId : datasetMap.keySet()) {
            List<String> altLineIds = datasetMap.get(originalLineId);
            if (altLineIds.contains(id)) {
                return Optional.of(originalLineId);
            }
        }
        return Optional.empty();
    }

    public List<String> getReverseAltIdStop(String datasetId, String stopId) {
        return getRevertAltIdStops(datasetId, stopId, stopsCache);

    }

    public List<String> getReverseAltIdLines(String datasetId, String lineId) {
        return getRevertAltIdLines(datasetId, lineId, linesCache);
    }

    private List<String> getRevertAltIdStops(String datasetId, String id, Map<String, Map<String, String>> cache) {
        if (!cache.containsKey(datasetId)) {
            return new ArrayList<>();
        }

        Map<String, String> datasetMap = cache.get(datasetId);

        if (!datasetMap.containsValue(id)) {
            return new ArrayList<>();
        }

        return datasetMap
                .entrySet()
                .stream()
                .filter(entry -> id.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<String> getRevertAltIdLines(String datasetId, String id, Map<String, Map<String, List<String>>> cache) {
        if (!cache.containsKey(datasetId)) {
            return new ArrayList<>();
        }

        Map<String, List<String>> datasetMap = cache.get(datasetId);

        return datasetMap.get(id);
    }
}
