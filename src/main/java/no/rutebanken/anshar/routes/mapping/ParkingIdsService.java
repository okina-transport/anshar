package no.rutebanken.anshar.routes.mapping;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ParkingIdsService {

    private final static ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private final static Map<String, String> PARKING_ORIGINAL_ID_TO_NETEX_ID = new ConcurrentHashMap<>();
    private final File parkingIdMappingPath;
    private final int updateFrequency;

    public ParkingIdsService(@Value("${anshar.mapping.parkings.path:/tmp/exports/technique/parkingIdMappings.csv}") File parkingIdMappingPath,
                             @Value("${anshar.mapping.parkings.update.frequency.min:120}") int updateFrequency) {
        this.parkingIdMappingPath = parkingIdMappingPath;
        this.updateFrequency = updateFrequency;
    }

    @PostConstruct
    private void initialize() {
        EXECUTOR.scheduleAtFixedRate(this::updateParkingIds, 0, updateFrequency, TimeUnit.MINUTES);
        log.info("Initialized parking id updater mapping service with path: {}, updateFrequency: {} minutes", parkingIdMappingPath, updateFrequency);
    }

    private void updateParkingIds() {
        synchronized (this) {
            log.info("Update parking id mappings map");

            PARKING_ORIGINAL_ID_TO_NETEX_ID.clear();

            try(CSVParser csvParser = CSVParser.builder()
                    .setFile(parkingIdMappingPath)
                    .setCharset(StandardCharsets.UTF_8)
                    .setFormat(CSVFormat.RFC4180
                            .builder()
                            .setHeader("originalId", "netexId")
                            .setSkipHeaderRecord(true)
                            .get())
                    .get()) {

                Iterable<CSVRecord> records = csvParser.getRecords();

                log.info("Read {} records from {}", CollectionUtils.size(records), parkingIdMappingPath);
                for (CSVRecord record : records) {

                    String originalId = record.get("originalId");
                    String netexId = record.get("netexId");

                    if (PARKING_ORIGINAL_ID_TO_NETEX_ID.containsKey(originalId)) {
                        log.info("Duplicate parking originalId in mapping file for originalId {}", originalId);
                        log.info("Current netexId: {}",  PARKING_ORIGINAL_ID_TO_NETEX_ID.get(originalId));
                        log.info("New netexId: {}",  netexId);
                    }

                    log.debug("originalId: {}, netexId: {}", originalId, netexId);
                    PARKING_ORIGINAL_ID_TO_NETEX_ID.put(originalId, netexId);
                }
            } catch (Exception e) {
                log.error("Failed to read CSV records from {}", parkingIdMappingPath, e);
            }
            log.info("Parking id mappings map has {} elements",  PARKING_ORIGINAL_ID_TO_NETEX_ID.size());
        }
    }

    public Optional<String> getNetexParkingId(String originalId) {

        if (PARKING_ORIGINAL_ID_TO_NETEX_ID.isEmpty()) {
            updateParkingIds();
        }

        return Optional.ofNullable(PARKING_ORIGINAL_ID_TO_NETEX_ID.get(originalId));
    }

}
