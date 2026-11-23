package no.rutebanken.anshar.routes.mapping;

import no.rutebanken.anshar.routes.export.file.BlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cache ScheduledStopPoint -> Quay, alimenté par le fichier d'association exporté par Chouette
 * (format CSV : "scheduledStopPoint,quay" avec ligne d'en-tête).
 * <p>
 * Le bean n'est créé que si la propriété {@code ssp.mapping.csv} vaut {@code true}.
 * Le cache est chargé au démarrage puis rechargé toutes les {@code anshar.ssp.quay.update.frequency.minutes} minutes.
 * <p>
 * Si le fichier est absent, vide ou illisible, une erreur est logguée (sans exception) et le cache existant est conservé.
 */
@Component
@ConditionalOnProperty(name = "ssp.mapping.csv", havingValue = "true")
public class ScheduledStopPointUpdaterService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledStopPointUpdaterService.class);
    private static final String HEADER_FIRST_COLUMN = "scheduledStopPoint";

    private final BlobStoreService blobStoreService;
    private final String sspQuayAssociationPath;
    private final int updateFrequency;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private volatile Map<String, String> sspToQuayMap = Map.of();

    public ScheduledStopPointUpdaterService(BlobStoreService blobStoreService,
                                            @Value("${anshar.ssp.quay.association.file:/tmp/lmd/chouette/mobiiti_technique/scheduledStopPoints/sspQuayAssociation.csv}") String sspQuayAssociationPath,
                                            @Value("${anshar.ssp.quay.update.frequency.minutes:240}") int updateFrequency) {
        this.blobStoreService = blobStoreService;
        this.sspQuayAssociationPath = sspQuayAssociationPath;
        this.updateFrequency = updateFrequency;
    }

    @PostConstruct
    void initialize() {
        executor.scheduleAtFixedRate(this::updateCache, 0, updateFrequency, TimeUnit.MINUTES);
        logger.info("Initialized ScheduledStopPoint-Quay cache with file:{}, updateFrequency:{} minutes", sspQuayAssociationPath, updateFrequency);
    }

    @PreDestroy
    void destroy() {
        logger.info("Destroy ScheduledStopPointUpdaterService");
        executor.shutdown();
    }

    synchronized void updateCache() {
        logger.info("Fetching ScheduledStopPoint-Quay associations - start. Fetching from {}", sspQuayAssociationPath);
        long t1 = System.currentTimeMillis();

        try (InputStream blob = blobStoreService.getBlob(sspQuayAssociationPath)) {
            if (blob == null) {
                logger.error("ssp.mapping.csv is enabled but ScheduledStopPoint-Quay association file {} is missing. Keeping {} existing associations", sspQuayAssociationPath, sspToQuayMap.size());
                return;
            }

            Map<String, String> newMap = new HashMap<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(blob, StandardCharsets.UTF_8));
            reader.lines().forEach(line -> parseLine(line, newMap));

            if (newMap.isEmpty()) {
                logger.error("ssp.mapping.csv is enabled but ScheduledStopPoint-Quay association file {} is empty (no valid association). Keeping {} existing associations", sspQuayAssociationPath, sspToQuayMap.size());
                return;
            }

            // remplacement atomique : les lecteurs voient soit l'ancien cache complet, soit le nouveau
            sspToQuayMap = Map.copyOf(newMap);

            logger.info("Fetched ScheduledStopPoint-Quay associations - {} mappings. [fetched: {}ms]", sspToQuayMap.size(), System.currentTimeMillis() - t1);
        } catch (IOException | RuntimeException e) {
            // ne pas propager : une exception stopperait définitivement le scheduleAtFixedRate
            logger.error("Error while updating ScheduledStopPoint-Quay cache, keeping {} existing associations", sspToQuayMap.size(), e);
        }
    }

    private static void parseLine(String line, Map<String, String> map) {
        if (line.isBlank()) {
            return;
        }
        String[] fields = line.split(",", -1);
        if (fields.length < 2 || fields[0].isBlank() || fields[1].isBlank()) {
            logger.warn("Invalid ScheduledStopPoint-Quay line: {}", line);
            return;
        }
        String ssp = fields[0].trim();
        if (HEADER_FIRST_COLUMN.equals(ssp)) {
            return;
        }
        map.put(ssp, fields[1].trim());
    }

    public Optional<String> getQuay(String scheduledStopPointId) {
        if (scheduledStopPointId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sspToQuayMap.get(scheduledStopPointId));
    }

    public int size() {
        return sspToQuayMap.size();
    }
}
