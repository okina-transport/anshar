package no.rutebanken.anshar.routes.mapping;

import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.util.CSVUtils;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;



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

    @Value("${anshar.mapping.stopplaces.update.frequency.min:60}")
    private int updateFrequency = 60;


    @Value("${anshar.stop.times.root.directory}")
    private String stopTimesRootDir;

    @Autowired
    SubscriptionConfig subscriptionConfig;

    //datasetId -> tripId -> sequence
    private Map<String, Map<String, Map<Integer,String>>> stopTimesCache = new HashMap();

    private FilenameFilter filenameFilter = (f, name) -> name.startsWith("stop_times_") && name.endsWith(".txt");

    @PostConstruct
    private void initialize() {

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::refreshCache, 0, updateFrequency, TimeUnit.MINUTES);
        logger.info("Initialized stopTimesService, updateFrequency:{} min", updateFrequency);
    }

    /**
     * Refresh the cache containing data from stop_times files
     */
    private void refreshCache() {
        synchronized (LOCK) {
            updateStopTimesCache();
        }
    }

    /**
     * Refresh the cache containing data from stop_times files
     */
    private void updateStopTimesCache() {

        List<String> datasetList = subscriptionConfig.getGtfsRTApis().stream()
                                                    .map(GtfsRTApi::getDatasetId)
                                                    .distinct()
                                                    .collect(Collectors.toList());

        for (String dataset : datasetList) {
            updateStopTimesCacheForDatasetId(dataset);
        }
    }


    /**
     * Refresh the cache containing data from stop_times files, for a particular datasetId
     * @param datasetId
     *  the dataset for which cache must be refreshed
     */
    private void updateStopTimesCacheForDatasetId(String datasetId) {

        File organisationDirectory = new File(stopTimesRootDir, datasetId);
        if (!organisationDirectory.exists()){
            return;
        }
        logger.info("Starting updating stop times cache for dataset : " + datasetId);


        String[] fileList = organisationDirectory.list(filenameFilter);

        for (String fileName : fileList) {
            File fileToRead = new File(organisationDirectory, fileName);
            feedCacheWithFile(fileToRead, datasetId);
        }

        logger.info("Feeding cache completed for datasetId: " + datasetId);
    }


    /**
     * Refresh the cache for a particular file/datasetId
     * @param fileToRead
     *  the stop_times file
     * @param datasetId
     *  the datasetId
     */
    private void feedCacheWithFile(File fileToRead, String datasetId) {

        try{
            Iterable<CSVRecord> records = CSVUtils.getRecords(fileToRead);

            Map<String, Map<Integer,String>> currentDatasetCache;

            if (stopTimesCache.containsKey(datasetId)){
                currentDatasetCache = stopTimesCache.get(datasetId);
            }else{
                currentDatasetCache = new HashMap<>();
                stopTimesCache.put(datasetId, currentDatasetCache);
            }

            for (CSVRecord record : records) {

                String stopId = record.get("stop_id");
                String tripId = record.get("trip_id");
                Integer sequence = Integer.parseInt(record.get("stop_sequence"));

                Map<Integer,String> currentTripCache;

                if (currentDatasetCache.containsKey(tripId)){
                    currentTripCache = currentDatasetCache.get(tripId);
                }else{
                    currentTripCache = new HashMap<>();
                    currentDatasetCache.put(tripId,currentTripCache);
                }
                currentTripCache.put(sequence, stopId);
            }
            logger.info("Feeding cache with stop_times file: " + fileToRead.getAbsolutePath() + " completed");

        }catch (IOException | IllegalArgumentException e){
            logger.error("Unable to feed cache with file:" + fileToRead.getAbsolutePath(), e);
        }
    }


    /**
     * Read the cache and recover a stop_id, for a given datasetId/tripId/stopSequence
     * @param datasetId
     *  the datasetId for which the stop_id must be recovered
     * @param tripId
     *  the trip_id for which the stop_id must be recovered
     * @param stopSequence
     *  the stop_sequence for which the stop_id must be recovered
     * @return
     */
    public Optional<String> getStopId(String datasetId, String tripId, Integer stopSequence){

           if (!stopTimesCache.containsKey(datasetId)){
               return Optional.empty();
           }

        Map<String, Map<Integer, String>> datasetMap = stopTimesCache.get(datasetId);

           if (!datasetMap.containsKey(tripId)){
               return Optional.empty();
           }

        Map<Integer, String> tripMap = datasetMap.get(tripId);

           if (!tripMap.containsKey(stopSequence)){
               return Optional.empty();
           }

           return Optional.of(tripMap.get(stopSequence));
    }

}
