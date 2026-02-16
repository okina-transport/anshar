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
public class PoiIdsService {

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private static final Map<String, Map<String, String>> POI_BY_OPERATOR_AND_ORIGINAL_ID_TO_NETEX_ID =
            new ConcurrentHashMap<>();
    private static final Map<String, Pair<String, String>> POI_NETEX_ID_TO_OPERATOR_AND_ORIGINAL_ID =
            new ConcurrentHashMap<>();
    private final File poiIdMappingPath;
    private final int updateFrequency;

    public PoiIdsService(@Value("${anshar.mapping.pois.path:/tmp/exports/technique/poiIdMappings.csv}") File poiIdMappingPath,
                         @Value("${anshar.mapping.pois.update.frequency.min:5}") int updateFrequency) {
        this.poiIdMappingPath = poiIdMappingPath;
        this.updateFrequency = updateFrequency;
    }

    @PostConstruct
    private void initialize() {

        if (!poiIdMappingPath.exists()) {
            log.info("No poi file existing. Update poi file will not be scheduled");
            return;
        }


        EXECUTOR.scheduleAtFixedRate(this::updatePoiIds, 0, updateFrequency, TimeUnit.MINUTES);
        log.info("Initialized poi id updater mapping service with path: {}, updateFrequency: {} minutes", poiIdMappingPath, updateFrequency);
    }

    public void updatePoiIds() {
        synchronized (this) {
            log.info("Update poi id mappings map");

            POI_BY_OPERATOR_AND_ORIGINAL_ID_TO_NETEX_ID.clear();

            try (CSVParser csvParser = CSVParser.builder()
                    .setFile(poiIdMappingPath)
                    .setCharset(StandardCharsets.UTF_8)
                    .setFormat(CSVFormat.RFC4180
                            .builder()
                            .setHeader("operator", "originalId", "netexId")
                            .setSkipHeaderRecord(true)
                            .get())
                    .get()) {

                Iterable<CSVRecord> records = csvParser.getRecords();
                log.info("Read {} records from {}", CollectionUtils.size(records), poiIdMappingPath);
                for (CSVRecord _record : records) {
                    String operator = _record.get("operator");
                    String originalId = _record.get("originalId");
                    String netexId = _record.get("netexId");

                    Map<String, String> poiByOriginalIdToNetexId =
                            POI_BY_OPERATOR_AND_ORIGINAL_ID_TO_NETEX_ID.computeIfAbsent(operator,
                                    key -> new HashMap<>());

                    if (poiByOriginalIdToNetexId.containsKey(originalId)) {
                        log.info("Duplicate poi originalId in mapping file for operator {} / originalId {}",
                                operator, originalId);
                        log.info("Current netexId: {}", poiByOriginalIdToNetexId.get(originalId));
                        log.info("New netexId: {}", netexId);
                    }

                    poiByOriginalIdToNetexId.put(originalId, netexId);
                    POI_NETEX_ID_TO_OPERATOR_AND_ORIGINAL_ID.put(netexId, Pair.of(operator, originalId));
                    log.debug("operator: {}, originalId: {}, netexId: {}", operator, originalId, netexId);
                }
            } catch (Exception e) {
                log.error("Failed to read CSV records from {}", poiIdMappingPath, e);
            }
            log.info("Poi id mappings map has {} operator(s) / {} netexId(s)",
                    POI_BY_OPERATOR_AND_ORIGINAL_ID_TO_NETEX_ID.size(),
                    POI_NETEX_ID_TO_OPERATOR_AND_ORIGINAL_ID.size());
        }
    }

    public Optional<String> getNetexPoiIdByOperatorAndOriginalId(String operator, String originalId) {
        Objects.requireNonNull(operator);
        Objects.requireNonNull(originalId);
        synchronized (this) {
            return Optional.ofNullable(POI_BY_OPERATOR_AND_ORIGINAL_ID_TO_NETEX_ID.getOrDefault(operator, Map.of()).get(originalId));
        }
    }

    public Optional<String> getOriginalPoiIdByNetexId(String netexId) {
        Objects.requireNonNull(netexId);
        synchronized (this) {
            return Optional.ofNullable(POI_NETEX_ID_TO_OPERATOR_AND_ORIGINAL_ID.getOrDefault(netexId, Pair.of(null,
                    null)).getRight());
        }
    }

    public Optional<String> getOperatorByNetexId(String netexId) {
        Objects.requireNonNull(netexId);
        synchronized (this) {
            return Optional.ofNullable(POI_NETEX_ID_TO_OPERATOR_AND_ORIGINAL_ID.getOrDefault(netexId, Pair.of(null,
                    null)).getLeft());
        }
    }

}
