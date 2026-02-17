package no.rutebanken.anshar.routes.mapping;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ParkingIdsService {

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private static final Map<String, Map<String, String>> PARKING_BY_OPERATOR_AND_ORIGINAL_ID_TO_NETEX_ID =
            new ConcurrentHashMap<>();
    private static final Map<String, Pair<String, String>> PARKING_NETEX_ID_TO_OPERATOR_AND_ORIGINAL_ID =
            new ConcurrentHashMap<>();
    private final File parkingIdMappingPath;
    private final int updateFrequency;

    public ParkingIdsService(@Value("${anshar.mapping.parkings.path:/tmp/exports/technique/parkingIdMappings.csv}") File parkingIdMappingPath,
                             @Value("${anshar.mapping.parkings.update.frequency.min:5}") int updateFrequency) {
        this.parkingIdMappingPath = parkingIdMappingPath;
        this.updateFrequency = updateFrequency;
    }

    @PostConstruct
    private void initialize() {


        if (!parkingIdMappingPath.exists()) {
            log.info("No parking file existing. Update parking file will not be scheduled");
            return;
        }


        EXECUTOR.scheduleAtFixedRate(this::updateParkingIds, 0, updateFrequency, TimeUnit.MINUTES);
        log.info("Initialized parking id updater mapping service with path: {}, updateFrequency: {} minutes", parkingIdMappingPath, updateFrequency);
    }

    public void updateParkingIds() {
        synchronized (this) {
            log.info("Update parking id mappings map");

            PARKING_BY_OPERATOR_AND_ORIGINAL_ID_TO_NETEX_ID.clear();


            try (CSVParser csvParser = CSVParser.builder()
                    .setFile(parkingIdMappingPath)
                    .setCharset(StandardCharsets.UTF_8)
                    .setFormat(CSVFormat.RFC4180
                            .builder()
                            .setHeader("operator", "originalId", "netexId")
                            .setSkipHeaderRecord(true)
                            .get())
                    .get()) {

                Iterable<CSVRecord> records = csvParser.getRecords();
                log.info("Read {} records from {}", CollectionUtils.size(records), parkingIdMappingPath);
                for (CSVRecord _record : records) {
                    String operator = _record.get("operator");
                    String originalId = _record.get("originalId");
                    String netexId = _record.get("netexId");

                    Map<String, String> parkingByOriginalIdToNetexId =
                            PARKING_BY_OPERATOR_AND_ORIGINAL_ID_TO_NETEX_ID.computeIfAbsent(operator,
                                    key -> new HashMap<>());

                    if (parkingByOriginalIdToNetexId.containsKey(originalId)) {
                        log.info("Duplicate parking originalId in mapping file for operator {} / originalId {}",
                                operator, originalId);
                        log.info("Current netexId: {}", parkingByOriginalIdToNetexId.get(originalId));
                        log.info("New netexId: {}", netexId);
                    }

                    parkingByOriginalIdToNetexId.put(originalId, netexId);
                    PARKING_NETEX_ID_TO_OPERATOR_AND_ORIGINAL_ID.put(netexId, Pair.of(operator, originalId));
                    log.debug("operator: {}, originalId: {}, netexId: {}", operator, originalId, netexId);
                }
            } catch (Exception e) {
                log.error("Failed to read CSV records from {}", parkingIdMappingPath, e);
            }
            log.info("Parking id mappings map has {} operator(s) / {} netexId(s)",
                    PARKING_BY_OPERATOR_AND_ORIGINAL_ID_TO_NETEX_ID.size(),
                    PARKING_NETEX_ID_TO_OPERATOR_AND_ORIGINAL_ID.size());
        }
    }

    public Optional<String> getNetexParkingIdByOperatorAndOriginalId(String operator, String originalId) {
        Objects.requireNonNull(operator);
        Objects.requireNonNull(originalId);
        synchronized (this) {
            return Optional.ofNullable(PARKING_BY_OPERATOR_AND_ORIGINAL_ID_TO_NETEX_ID.getOrDefault(operator, Map.of()).get(originalId));
        }
    }

    public Optional<String> getOriginalParkingIdByNetexId(String netexId) {
        Objects.requireNonNull(netexId);
        synchronized (this) {
            return Optional.ofNullable(PARKING_NETEX_ID_TO_OPERATOR_AND_ORIGINAL_ID.getOrDefault(netexId, Pair.of(null,
                    null)).getRight());
        }
    }

    public Optional<String> getOperatorByNetexId(String netexId) {
        Objects.requireNonNull(netexId);
        synchronized (this) {
            return Optional.ofNullable(PARKING_NETEX_ID_TO_OPERATOR_AND_ORIGINAL_ID.getOrDefault(netexId, Pair.of(null,
                    null)).getLeft());
        }
    }

}
