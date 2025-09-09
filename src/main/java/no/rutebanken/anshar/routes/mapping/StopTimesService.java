package no.rutebanken.anshar.routes.mapping;

import lombok.Data;
import no.rutebanken.anshar.util.CSVUtils;
import no.rutebanken.anshar.util.FileUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;


/**
 * Service to handle stop sequence in incoming GTFS-RT data.
 * - Build a cache by reading stop_times files
 * - Can map an incoming trip_id/sequence to a stop id by reading the cache
 */
@Component
@Configuration
public class StopTimesService {

    private static final Logger logger = LoggerFactory.getLogger(StopTimesService.class);

    private static final Object LOCK = new Object();

    // datasetId -> tripId -> StopTimeCacheEntry
    private final Map<String, Map<String, List<StopTimeCacheEntry>>> stopTimesCache = new ConcurrentHashMap<>();
    //datasetId -> tripId -> TripCacheEntry
    private final Map<String, Map<String, TripCacheEntry>> tripsCache = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> knownRoutesCache = new ConcurrentHashMap<>();
    private final Predicate<File> stopTimesFilter =
            (f) -> f.getName().startsWith("stop_times_") && f.getName().endsWith(".txt");
    private final Predicate<File> tripsFilter = (f) -> f.getName().startsWith("trips_") && f.getName().endsWith(
            ".txt");
    @Value("${anshar.mapping.stopplaces.update.frequency.min:60}")
    private int updateFrequency = 60;
    @Value("${anshar.stop.times.root.directory}")
    private String stopTimesRootDir;
    @Value("${anshar.trips.root.directory}")
    private String tripsRootDir;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    private void initialize() {
        executor.scheduleAtFixedRate(this::refreshCache, 0, updateFrequency, TimeUnit.MINUTES);
        logger.info("Initialized stopTimesService, updateFrequency:{} min", updateFrequency);
    }

    @PreDestroy
    private void destroy() {
        logger.info("Destroy StopTimesService");
        executor.shutdown();
    }

    /**
     * Refresh the cache containing data from stop_times files
     */
    private void refreshCache() {
        List<String> datasetList = FileUtils.listDirectories(stopTimesRootDir);
        synchronized (LOCK) {
            logger.info("Refreshing stop times cache for datasets: {}", datasetList);
            stopTimesCache.clear();
            knownRoutesCache.clear();
            tripsCache.clear();
            for (String dataset : datasetList) {
                try {
                    updateStopTimesCacheForDatasetId(dataset);
                } catch (IOException e) {
                    logger.error("Failed to update stop times cache for dataset {}", dataset, e);
                }
                try {
                    updateTripsCacheForDatasetId(dataset);
                } catch (IOException e) {
                    logger.error("Failed to update trips cache for dataset {}", dataset, e);
                }
            }
        }
    }

    private void refreshCacheIfEmpty() {
        synchronized (LOCK) {
            if (MapUtils.isEmpty(knownRoutesCache) || MapUtils.isEmpty(stopTimesCache) || MapUtils.isEmpty(tripsCache)) {
                refreshCache();
            }
        }
    }


    /**
     * Refresh the cache containing data from stop_times files, for a particular datasetId
     *
     * @param datasetId the dataset for which cache must be refreshed
     */
    private void updateStopTimesCacheForDatasetId(String datasetId) throws IOException {

        File organisationDirectory = new File(stopTimesRootDir, datasetId);
        if (!organisationDirectory.exists()) {
            return;
        }
        logger.info("Starting updating stop times cache for dataset : {}", datasetId);

        File[] stopTimesFiles = organisationDirectory.listFiles();
        if (stopTimesFiles != null) {
            Optional<File> mostRecentStopTimesFile = Stream.of(stopTimesFiles)
                    .filter(stopTimesFilter)
                    .max(Comparator.comparing(File::getName));
            mostRecentStopTimesFile.ifPresent(p -> feedCacheWithFile(p, datasetId));
        }

        logger.info("Feeding cache completed for datasetId: {}", datasetId);
    }

    /**
     * Refresh the cache containing data from stop_times files, for a particular datasetId
     *
     * @param datasetId the dataset for which cache must be refreshed
     */
    private void updateTripsCacheForDatasetId(String datasetId) throws IOException {

        File organisationDirectory = new File(tripsRootDir, datasetId);
        if (!organisationDirectory.exists()) {
            return;
        }
        logger.info("Starting updating trips cache for dataset : {}", datasetId);

        File[] tripsFiles = organisationDirectory.listFiles();
        if (tripsFiles != null) {
            Optional<File> mostRecentTripsFile = Stream.of(tripsFiles)
                    .filter(tripsFilter)
                    .max(Comparator.comparing(File::getName));
            mostRecentTripsFile.ifPresent(f -> feedCacheWithTripsFile(f, datasetId));
        }

        logger.info("Feeding cache completed for datasetId: {}", datasetId);
    }

    /**
     * Refresh the cache for a particular file/datasetId
     *
     * @param fileToRead the stop_times file
     * @param datasetId  the datasetId
     */
    private void feedCacheWithFile(File fileToRead, String datasetId) {
        try {
            Iterable<CSVRecord> records = CSVUtils.getRecords(fileToRead);
            Map<String, List<StopTimeCacheEntry>> datasetCache = stopTimesCache.computeIfAbsent(datasetId, key -> new HashMap<>());
            for (CSVRecord csvRecord : records) {
                String stopId = csvRecord.get("stop_id");
                String tripId = csvRecord.get("trip_id");
                String departureTime = csvRecord.get("departure_time");
                String arrivalTime = csvRecord.get("arrival_time");
                int stopSequence = Integer.parseInt(csvRecord.get("stop_sequence"));
                datasetCache.computeIfAbsent(tripId, key -> new ArrayList<>())
                        .add(new StopTimeCacheEntry(arrivalTime, departureTime, stopId, stopSequence));
            }
            logger.info("Feeding cache with stop_times file: {} completed", fileToRead.getAbsolutePath());

        } catch (IOException | IllegalArgumentException e) {
            logger.error("Unable to feed cache with file: {}", fileToRead.getAbsolutePath(), e);
        }
    }

    /**
     * Refresh the cache for a particular file/datasetId
     *
     * @param fileToRead the stop_times file
     * @param datasetId  the datasetId
     */
    private void feedCacheWithTripsFile(File fileToRead, String datasetId) {

        try {
            Iterable<CSVRecord> records = CSVUtils.getRecords(fileToRead);

            Map<String, TripCacheEntry> currentDatasetCache;
            Set<String> knownRoutes = new HashSet<>();

            if (tripsCache.containsKey(datasetId)) {
                currentDatasetCache = tripsCache.get(datasetId);
            } else {
                currentDatasetCache = new HashMap<>();
                tripsCache.put(datasetId, currentDatasetCache);
            }

            for (CSVRecord csvRecord : records) {

                String routeId = csvRecord.get("route_id");
                String tripId = csvRecord.get("trip_id");
                Integer directionId = csvRecord.isSet("direction_id") ? Integer.parseInt(csvRecord.get("direction_id")) : null;

                TripCacheEntry tripCacheEntry = new TripCacheEntry(routeId, directionId);
                currentDatasetCache.put(tripId, tripCacheEntry);
                knownRoutes.add(routeId);

            }
            knownRoutesCache.put(datasetId, knownRoutes);
            logger.info("Feeding cache with trips file: {} completed", fileToRead.getAbsolutePath());

        } catch (IOException | IllegalArgumentException e) {
            logger.error("Unable to feed cache with file: {}", fileToRead.getAbsolutePath(), e);
        }
    }

    /**
     * Read the cache and recover a stop_id, for a given datasetId/tripId/stopSequence
     *
     * @param datasetId    the datasetId for which the stop_id must be recovered
     * @param tripId       the trip_id for which the stop_id must be recovered
     * @param stopSequence the stop_sequence for which the stop_id must be recovered
     * @return stopId if found, else null wrapped in Optional
     */
    public Optional<String> findStopIdByDatasetIdAndTripIdAndStopSequence(String datasetId, String tripId, Integer stopSequence) {
        refreshCacheIfEmpty();

        Map<String, List<StopTimeCacheEntry>> datasetCache = stopTimesCache.get(datasetId);
        if (MapUtils.isEmpty(datasetCache)) {
            return Optional.empty();
        }

        List<StopTimeCacheEntry> stopTimeCacheEntries = datasetCache.get(tripId);
        if (CollectionUtils.isEmpty(stopTimeCacheEntries)) {
            return Optional.empty();
        }

        return stopTimeCacheEntries
                .stream()
                .filter(stopTimeCacheEntry -> stopTimeCacheEntry.getStopSequence() == stopSequence)
                .map(StopTimeCacheEntry::getStopId)
                .findFirst();
    }

    /**
     * Read the cache and recover last stop_id of trip for a given datasetId/tripId
     *
     * @param datasetId the datasetId for which the stop_id must be recovered
     * @param tripId    the trip_id for which the stop_id must be recovered
     * @return last stop_id of trip if found else null wrapped in Optional
     */
    public Optional<String> getDestinationId(String datasetId, String tripId) {
        refreshCacheIfEmpty();

        Map<String, List<StopTimeCacheEntry>> datasetCache = stopTimesCache.get(datasetId);
        if (MapUtils.isEmpty(datasetCache)) {
            return Optional.empty();
        }

        List<StopTimeCacheEntry> stopTimeCacheEntries = datasetCache.get(tripId);
        if (CollectionUtils.isEmpty(stopTimeCacheEntries)) {
            return Optional.empty();
        }

        return stopTimeCacheEntries
                .stream()
                .max(Comparator.comparing(StopTimeCacheEntry::getStopSequence))
                .map(StopTimeCacheEntry::getStopId);
    }

    /**
     * Read the cache and recover a route_id, for a given datasetId/tripId
     *
     * @param datasetId the datasetId for which the route_id must be recovered
     * @param tripId    the trip_id for which the route_id must be recovered
     * @return route_id if found else null wrapped in Optional
     */
    public Optional<String> getRouteId(String datasetId, String tripId) {
        refreshCacheIfEmpty();

        Map<String, TripCacheEntry> datasetMap = tripsCache.get(datasetId);
        if (MapUtils.isEmpty(datasetMap)) {
            return Optional.empty();
        }

        TripCacheEntry trip = datasetMap.get(tripId);

        if (trip == null) {
            logger.debug("Trip {} not in dataset {}", tripId, datasetId);
            return Optional.empty();
        }

        return Optional.ofNullable(trip.getRouteId());
    }

    public Optional<String> getDirectionId(String datasetId, String tripId) {
        refreshCacheIfEmpty();

        Map<String, TripCacheEntry> datasetMap = tripsCache.get(datasetId);
        if (MapUtils.isEmpty(datasetMap)) {
            return Optional.empty();
        }

        TripCacheEntry trip = datasetMap.get(tripId);

        if (trip == null) {
            logger.debug("Trip {} not in dataset {}", tripId, datasetId);
            return Optional.empty();
        }

        Integer directionId = trip.getDirectionId();
        if (directionId == null) {
            return Optional.empty();
        }
        return Optional.of(directionId == 0 ? "A" : "R");
    }

    /**
     * Check if routeId is in datasetId's cache
     *
     * @param datasetId datasetId to look for routeId
     * @param routeId   routeId to check
     * @return true if routeId is in cache, false otherwise
     */
    public boolean checkIfKnownRouteId(String datasetId, String routeId) {
        refreshCacheIfEmpty();

        Set<String> knownRouteIdInCache = knownRoutesCache.get(datasetId);
        if (CollectionUtils.isEmpty(knownRouteIdInCache)) {
            return false;
        }

        return knownRouteIdInCache.contains(routeId);
    }

    /**
     * Read the cache and recover a {@link StopTimeCacheEntry} for a given datasetId/tripId/stopId
     *
     * @param datasetId the datasetId to look for {@link StopTimeCacheEntry}
     * @param tripId    the trip_id to look for {@link StopTimeCacheEntry}
     * @param stopId    the stop_id to look for {@link StopTimeCacheEntry}
     * @return {@link StopTimeCacheEntry} if found else null wrapped in Optional
     */
    public Optional<StopTimeCacheEntry> findStopTimeCacheEntryByDatasetIdAndTripIdAndStopId(String datasetId, String tripId, String stopId) {
        refreshCacheIfEmpty();

        Map<String, List<StopTimeCacheEntry>> datasetCache = stopTimesCache.get(datasetId);
        if (MapUtils.isEmpty(datasetCache)) {
            return Optional.empty();
        }

        List<StopTimeCacheEntry> stopTimeCacheEntries = datasetCache.get(tripId);
        if (CollectionUtils.isEmpty(stopTimeCacheEntries)) {
            return Optional.empty();
        }

        return stopTimeCacheEntries.stream()
                .filter(e -> stopId.equals(e.getStopId()))
                .findFirst();
    }

    @Data
    public static class StopTimeCacheEntry {
        private final String arrivalTime;
        private final String departureTime;
        private final String stopId;
        private final int stopSequence;
    }

    @Data
    public static class TripCacheEntry {
        private final String routeId;
        private final Integer directionId;
    }

}
