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

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.*;

@Component
@Configuration
public class StopPlaceUpdaterService {

    private static final Logger logger = LoggerFactory.getLogger(StopPlaceUpdaterService.class);

    private static final Object LOCK = new Object();

    private transient final ConcurrentMap<String, Pair<String, String>> stopPlaceMappings = new ConcurrentHashMap<>();

    private transient final ConcurrentMap<String, List<String>> reverseStopPlaceMappings = new ConcurrentHashMap<>();

    private transient final Set<String> validNsrIds = new HashSet<>();

    @Autowired
    private StopPlaceRegisterMappingFetcher stopPlaceRegisterMappingFetcher;

    @Value("${anshar.mapping.quays.gcs.path}")
    private String quayMappingPath;

    @Value("${anshar.mapping.stopplaces.gcs.path}")
    private String stopPlaceMappingPath;

    @Value("${anshar.mapping.stopquayjson.gcs.path}")
    private String stopPlaceQuayJsonPath;

    @Value("${anshar.mapping.stopplaces.update.frequency.min:60}")
    private int updateFrequency = 60;

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

        List<String> stopPlaces = reverseStopPlaceMappings.get(id);

        if (CollectionUtils.isEmpty(stopPlaces)) {
            return List.of();
        }

        Optional<String> reverseStopPlaceMapping = stopPlaces.stream()
                .filter(provId -> datasetId == null || provId.startsWith(datasetId))
                .findFirst();

        return reverseStopPlaceMapping.map(Arrays::asList).orElseGet(ArrayList::new);

    }

    public List<String> getReverseWithoutDatasetId(String id) {
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
     * Returns true if provided id is included in the latest dataset from NSR
     *
     * @param id
     * @return
     */
    public boolean isKnownId(String id) {
        return validNsrIds.contains(id);
    }

    /**
     * Returns true if provided id can be reverted to producer id
     *
     * @param id
     * @return
     */
    public boolean canBeReverted(String id, String datasetId) {
        if (!reverseStopPlaceMappings.containsKey(id)) {
            return false;
        }

        List<String> mappings = reverseStopPlaceMappings.get(id);

        if (datasetId == null && mappings != null && !mappings.isEmpty()) {
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

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(this::updateIdMapping, 0, updateFrequency, TimeUnit.MINUTES);

        logger.info("Initialized id_mapping-updater with urls:{}, updateFrequency:{} min", new String[]{quayMappingPath, stopPlaceMappingPath}, updateFrequency);
    }

    private void updateIdMapping() {
        // re-entrant
        synchronized (LOCK) {
            updateStopPlaceMapping(quayMappingPath);
            updateStopPlaceMapping(stopPlaceMappingPath);
            updateStopPlacesAndQuays(stopPlaceQuayJsonPath);
        }
    }

    private void updateStopPlaceMapping(String mappingUrl) {
        logger.info("Fetching mapping data - start. Fetching mapping-data from {}", mappingUrl);

        Map<String, Pair<String,String>> foundMappings = stopPlaceRegisterMappingFetcher.fetchStopPlaceMapping(mappingUrl);
        stopPlaceMappings.putAll(foundMappings);

        validNsrIds.addAll(stopPlaceMappings.keySet());

        for (Map.Entry<String, Pair<String,String>> mappingEntry : foundMappings.entrySet()) {

            List<String> providerIds;
            if (reverseStopPlaceMappings.containsKey(mappingEntry.getValue().getLeft())) {
                providerIds = reverseStopPlaceMappings.get(mappingEntry.getValue().getLeft());
            } else {
                providerIds = new ArrayList<>();
                reverseStopPlaceMappings.put(mappingEntry.getValue().getLeft(), providerIds);
            }

            providerIds.add(mappingEntry.getKey());
        }

        logger.info("Fetching mapping data - done.");
    }

    private void updateStopPlacesAndQuays(String url) {
        logger.info("Fetching stops and quay data - start. Fetching mapping-data from {}", url);
        final Map<String, Collection<String>> stopQuayMap = stopPlaceRegisterMappingFetcher.fetchStopPlaceQuayJson(url);
        if (!stopQuayMap.isEmpty()) {
            validNsrIds.clear();

            int stopsCounter = stopQuayMap.size();
            int quayCounter = 0;
            for (String s : stopQuayMap.keySet()) {
                // Add StopPlace-id
                validNsrIds.add(s);

                //Add quay-ids
                final Collection<String> quayIds = stopQuayMap.get(s);
                quayCounter += quayIds.size();
                validNsrIds.addAll(quayIds);
            }

            logger.info("Fetching stops and quay data - done. Found {} stops, {} quays", stopsCounter, quayCounter);
        } else {
            logger.info("Fetching stops and quay data - done. No stops found");
        }
    }


    //Called from tests
    public void addStopPlaceMappings(Map<String, Pair<String, String>> stopPlaceMap) {
        this.stopPlaceMappings.putAll(stopPlaceMap);
    }

    //Called from tests
    public void addStopPlaceReverseMappings(Map<String, List<String>> stopPlaceReverseMap) {
        this.reverseStopPlaceMappings.putAll(stopPlaceReverseMap);
    }

    //Called from tests
    public void addStopQuays(Collection<String> stopQuays) {
        this.validNsrIds.addAll(stopQuays);
    }

    public String getStopName(String stopId, String datasetId){
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
                stopPlaceMappings.get(datasetId + ":StopPlace:" + stopId)  != null ? stopPlaceMappings.get(datasetId + ":StopPlace:" + stopId).getRight() :
                null;
    }
}
