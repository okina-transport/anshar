/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.mapping;

import lombok.Getter;
import no.rutebanken.anshar.config.AnsharConfiguration;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;

@Component
@Configuration
public class StopPlaceUpdaterService {

    private static final Logger logger = LoggerFactory.getLogger(StopPlaceUpdaterService.class);

    private static final Object LOCK = new Object();

    private transient final ConcurrentMap<String, Pair<String, String>> stopPlaceMappings = new ConcurrentHashMap<>();

    @Getter
    private transient final ConcurrentMap<String, Set<String>> reverseStopPlaceMappings = new ConcurrentHashMap<>();

    private transient final Set<String> validNsrIds = new HashSet<>();

    private transient final Set<String> knownDatasetIds = ConcurrentHashMap.newKeySet();

    private transient final ConcurrentMap<String, List<String>> stopPlaceAndQuayAssociation = new ConcurrentHashMap<>();


    @Autowired
    private StopPlaceRegisterMappingFetcher stopPlaceRegisterMappingFetcher;

    @Value("${anshar.mapping.quays.gcs.path}")
    private String quayMappingPath;

    @Value("${anshar.mapping.stopplaces.gcs.path}")
    private String stopPlaceMappingPath;

    @Value("${anshar.stop.place.quay.association.file}")
    private String stopPlaceQuayAssociationFile;


    @Value("${anshar.mapping.stopplaces.update.frequency.min:60}")
    private int updateFrequency = 60;

    @Autowired
    AnsharConfiguration ansharConfiguration;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public String get(String id) {
        if (stopPlaceMappings.isEmpty()) {
            // Avoid multiple calls at the same time.
            // Could have used a timed lock here.
            synchronized (LOCK) {
                // Check again.
                if (stopPlaceMappings.isEmpty()) {
                    updateIdMapping();
                }
            }
        }
        return stopPlaceMappings.get(id) != null ? stopPlaceMappings.get(id).getLeft() : null;
    }

    public List<String> getReverse(String id, String datasetId) {
        if (reverseStopPlaceMappings.isEmpty()) {
            // Avoid multiple calls at the same time.
            // Could have used a timed lock here.
            synchronized (LOCK) {
                // Check again.
                if (reverseStopPlaceMappings.isEmpty()) {
                    updateIdMapping();
                }
            }
        }

        Set<String> stopPlaces = reverseStopPlaceMappings.get(id);
        if (id.contains("StopPlace")){
            logger.info("reversion process found {} results for id {}",stopPlaces.size(), id);
        }


        if (CollectionUtils.isEmpty(stopPlaces)) {
            return List.of();
        }

        Optional<String> reverseStopPlaceMapping = stopPlaces.stream()
                .filter(provId -> datasetId == null || provId.startsWith(datasetId))
                .findFirst();

        return reverseStopPlaceMapping.map(Arrays::asList).orElseGet(ArrayList::new);

    }

    public Collection<String> getReverseWithoutDatasetId(String id) {
        if (reverseStopPlaceMappings.isEmpty()) {
            // Avoid multiple calls at the same time.
            // Could have used a timed lock here.
            synchronized (LOCK) {
                // Check again.
                if (reverseStopPlaceMappings.isEmpty()) {
                    updateIdMapping();
                }
            }
        }

        if (reverseStopPlaceMappings.get(id) != null && !reverseStopPlaceMappings.get(id).isEmpty()) {
            return reverseStopPlaceMappings.get(id);
        }

        return new ArrayList<>();

    }

    /**
     * @param datasetId L'identifiant du dataset à vérifier.
     * @return true si le datasetId est présent dans les fichiers de mapping chargés.
     */
    public boolean isDatasetKnown(String datasetId) {
        if (datasetId == null) {
            return false;
        }
        return knownDatasetIds.contains(datasetId);
    }

    /**
     * @param id stop identifier
     * @return true if provided id is included in the latest dataset from NSR
     */
    public boolean isKnownId(String id) {
        return validNsrIds.contains(id);
    }

    /**
     * @param id        stop identifier
     * @param datasetId stop dataset id
     * @return true, if provided id can be reverted to producer id
     */
    public boolean canBeReverted(String id, String datasetId) {
        if (!reverseStopPlaceMappings.containsKey(id)) {
            return false;
        }

        Set<String> mappings = reverseStopPlaceMappings.get(id);

        if (datasetId == null || CollectionUtils.isEmpty(mappings)) {
            return true;
        }

        return mappings.stream()
                .filter(Objects::nonNull)
                .anyMatch(provId -> provId.startsWith(datasetId));
    }

    public boolean canBeRevertedWithoutDatasetId(String id) {
        if (!reverseStopPlaceMappings.containsKey(id)) {
            return false;
        }

        return reverseStopPlaceMappings.get(id) != null && !reverseStopPlaceMappings.get(id).isEmpty();
    }

    @PostConstruct
    private void initialize() {
        executor.scheduleAtFixedRate(this::updateIdMapping, 0, updateFrequency, TimeUnit.MINUTES);

        logger.info("Initialized id_mapping-updater with urls:{}, updateFrequency:{} min", new String[]{quayMappingPath, stopPlaceMappingPath}, updateFrequency);
    }

    @PreDestroy
    private void destroy() {
        logger.info("Destroy StopPlaceUpdaterService");
        executor.shutdown();
    }

    public void updateIdMapping() {
        // re-entrant
        synchronized (LOCK) {
            updateStopPlaceMapping(quayMappingPath);
            updateStopPlaceMapping(stopPlaceMappingPath);
            updateStopPlaceQuayAssociations(stopPlaceQuayAssociationFile);

            knownDatasetIds.clear();
            // Pour chaque ID producteur (ex: "DATASET1:StopPlace:123"),
            // on extrait la première partie ("DATASET1") et on l'ajoute à notre ensemble.
            for (String producerId : validNsrIds) {
                if (producerId != null && producerId.contains(":")) {
                    knownDatasetIds.add(producerId.split(":")[0]);
                }
            }
            logger.info("Updated known datasets. Found {} unique dataset IDs.", knownDatasetIds.size());
        }

        ansharConfiguration.setInitialized(true);

    }

    private void updateStopPlaceQuayAssociations(String stopPlaceQuayAssociationFile) {
        Map<String, List<String>> stopPlaceAndQuays = stopPlaceRegisterMappingFetcher.fecthStopPlaceAndQuayData(stopPlaceQuayAssociationFile);
        stopPlaceAndQuayAssociation.clear();
        stopPlaceAndQuayAssociation.putAll(stopPlaceAndQuays);
        logger.info("Fetching stopPlace and quay association data - done.");
    }

    public List<String> getStopPlaceChildren(String stopPlaceId) {
        if (stopPlaceId == null || !stopPlaceAndQuayAssociation.containsKey(stopPlaceId)) {
            return new ArrayList<>();
        }
        return stopPlaceAndQuayAssociation.get(stopPlaceId);
    }

    private void updateStopPlaceMapping(String mappingUrl) {
        logger.info("Fetching mapping data - start. Fetching mapping-data from {}", mappingUrl);

        Map<String, Pair<String, String>> foundMappings = stopPlaceRegisterMappingFetcher.fetchStopPlaceMapping(mappingUrl);
        stopPlaceMappings.putAll(foundMappings);

        validNsrIds.addAll(stopPlaceMappings.keySet());

        for (Map.Entry<String, Pair<String, String>> mappingEntry : foundMappings.entrySet()) {

            Set<String> providerIds;
            if (reverseStopPlaceMappings.containsKey(mappingEntry.getValue().getLeft())) {
                providerIds = reverseStopPlaceMappings.get(mappingEntry.getValue().getLeft());
            } else {
                providerIds = new HashSet<>();
                reverseStopPlaceMappings.put(mappingEntry.getValue().getLeft(), providerIds);
            }

            providerIds.add(mappingEntry.getKey());
        }

        logger.info("Fetching mapping data - done.");
    }


    //Called from tests
    public void addStopPlaceMappings(Map<String, Pair<String, String>> stopPlaceMap) {
        this.stopPlaceMappings.putAll(stopPlaceMap);
    }

    //Called from tests
    public void addStopPlaceReverseMappings(Map<String, Set<String>> stopPlaceReverseMap) {
        this.reverseStopPlaceMappings.putAll(stopPlaceReverseMap);
    }

    //Called from tests
    public void addStopPlaceQuayAssociations(Map<String, List<String>> stopPlaceQuayAssociations) {
        this.stopPlaceAndQuayAssociation.putAll(stopPlaceQuayAssociations);
    }

    //Called from tests
    public void addStopQuays(Collection<String> stopQuays) {
        this.validNsrIds.addAll(stopQuays);
    }

    public String getStopName(String stopId, String datasetId) {
        if (stopPlaceMappings.isEmpty()) {
            // Avoid multiple calls at the same time.
            // Could have used a timed lock here.
            synchronized (LOCK) {
                // Check again.
                if (stopPlaceMappings.isEmpty()) {
                    updateIdMapping();
                }
            }
        }
        return stopPlaceMappings.get(datasetId + ":Quay:" + stopId) != null ? stopPlaceMappings.get(datasetId + ":Quay:" + stopId).getRight() :
                stopPlaceMappings.get(datasetId + ":StopPlace:" + stopId) != null ? stopPlaceMappings.get(datasetId + ":StopPlace:" + stopId).getRight() :
                        null;
    }

    public boolean exists(String stopMobiitiId) {
        return reverseStopPlaceMappings.containsKey(stopMobiitiId);
    }

}
